package org.sharedtype.processor.parser;

import org.sharedtype.annotation.SharedType;
import org.sharedtype.processor.context.Config;
import org.sharedtype.processor.context.Context;
import org.sharedtype.processor.domain.ClassDef;
import org.sharedtype.processor.domain.FieldComponentInfo;
import org.sharedtype.processor.domain.TypeDef;
import org.sharedtype.processor.domain.TypeVariableInfo;
import org.sharedtype.processor.parser.type.TypeInfoParser;
import org.sharedtype.processor.support.annotation.VisibleForTesting;
import org.sharedtype.processor.support.utils.Tuple;
import org.sharedtype.processor.support.utils.Utils;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Singleton
final class TypeDefParserImpl implements TypeDefParser {
    private final Context ctx;
    private final Types types;
    private final Elements elements;
    private final TypeInfoParser typeInfoParser;

    @Inject
    TypeDefParserImpl(Context ctx, TypeInfoParser typeInfoParser) {
        this.ctx = ctx;
        this.types = ctx.getProcessingEnv().getTypeUtils();
        this.elements = ctx.getProcessingEnv().getElementUtils();
        this.typeInfoParser = typeInfoParser;
    }

    @Override @Nullable
    public TypeDef parse(TypeElement typeElement) {
        if (ctx.isTypeIgnored(typeElement)) {
            return null;
        }
        String qualifiedName = typeElement.getQualifiedName().toString();
        var cachedDef = ctx.getTypeCache().getType(qualifiedName);
        if (cachedDef != null) {
            return cachedDef;
        }

        var config = new Config(typeElement); // TODO: validate typeElement's eligibility

        var builder = ClassDef.builder().qualifiedName(config.getQualifiedName()).name(config.getName());
        builder.typeVariables(parseTypeVariables(typeElement));
        builder.components(parseComponents(typeElement, config));
        builder.supertypes(parseSupertypes(typeElement));

        var classDef = builder.build();
        ctx.getTypeCache().saveType(config.getQualifiedName(), classDef);
        return classDef;
    }

    private List<TypeVariableInfo> parseTypeVariables(TypeElement typeElement) {
        var typeParameters = typeElement.getTypeParameters();
        return typeParameters.stream()
            .map(typeParameterElement -> TypeVariableInfo.builder().name(typeParameterElement.getSimpleName().toString()).build())
            .toList();
    }

    private List<TypeDef> parseSupertypes(TypeElement typeElement) {
        var supertypeElems = new ArrayList<TypeElement>();
        var superclass = typeElement.getSuperclass();
        if (superclass instanceof DeclaredType declaredType) {
            supertypeElems.add((TypeElement) declaredType.asElement());
        }

        var interfaceTypes = typeElement.getInterfaces();
        for (TypeMirror interfaceType : interfaceTypes) {
            var declaredType = (DeclaredType) interfaceType;
            supertypeElems.add((TypeElement) declaredType.asElement());
        }
        return supertypeElems.stream().map(this::parse).filter(Objects::nonNull).toList();
    }

    private List<FieldComponentInfo> parseComponents(TypeElement typeElement, Config config) {
        var componentElems = resolveComponents(typeElement, config);

        var fields = new ArrayList<FieldComponentInfo>(componentElems.size());
        for (var tuple : componentElems) {
            var element = tuple.a();
            var fieldInfo = FieldComponentInfo.builder()
                .name(tuple.b())
                .modifiers(element.getModifiers())
                .optional(element.getAnnotation(ctx.getProps().getOptionalAnno()) != null)
                .type(typeInfoParser.parse(element.asType()))
                .build();
            fields.add(fieldInfo);
        }
        return fields;
    }

    @VisibleForTesting
    List<Tuple<Element, String>> resolveComponents(TypeElement typeElement, Config config) {
        var isRecord = typeElement.getKind() == ElementKind.RECORD;
        var enclosedElements = typeElement.getEnclosedElements();
        var recordAccessors = typeElement.getRecordComponents().stream().map(RecordComponentElement::getAccessor).collect(Collectors.toUnmodifiableSet());

        var res = new ArrayList<Tuple<Element, String>>(enclosedElements.size());
        var namesOfTypes = new NamesOfTypes(enclosedElements.size());
        var includeAccessors = config.includes(SharedType.ComponentType.ACCESSORS);
        var includeFields = config.includes(SharedType.ComponentType.FIELDS) && !(isRecord && includeAccessors);

        for (Element enclosedElement : enclosedElements) {
            if (config.isComponentIgnored(enclosedElement)) {
                continue;
            }

            var type = enclosedElement.asType();
            var name = enclosedElement.getSimpleName().toString();

            if (includeFields && enclosedElement.getKind() == ElementKind.FIELD && enclosedElement instanceof VariableElement variableElem) {
                if (namesOfTypes.contains(name, type)) {
                    continue;
                }
                res.add(Tuple.of(variableElem, name));
                namesOfTypes.add(name, type);
            }

            if (includeAccessors && enclosedElement instanceof ExecutableElement methodElem && isZeroArgNonstaticMethod(methodElem)) {
                boolean explicitAccessor = methodElem.getAnnotation(SharedType.Accessor.class) != null;
                if (!explicitAccessor && isRecord && !recordAccessors.contains(methodElem)) {
                    continue;
                }
                var baseName = getAccessorBaseName(name, isRecord);
                if (baseName == null) {
                    continue;
                }
                var returnType = methodElem.getReturnType();
                if (namesOfTypes.contains(baseName, returnType)) {
                    continue;
                }
                res.add(Tuple.of(methodElem, baseName));
                namesOfTypes.add(baseName, returnType);
            }

            // TODO: CONSTANTS
        }

        return res;
    }

    private boolean isZeroArgNonstaticMethod(ExecutableElement componentElem) {
        if (componentElem.getKind() != ElementKind.METHOD || componentElem.getModifiers().contains(Modifier.STATIC)) {
            return false;
        }
        return componentElem.getParameters().isEmpty();
    }

    @Nullable
    private String getAccessorBaseName(String name, boolean isRecord) {
        for (String accessorGetterPrefix : ctx.getProps().getAccessorGetterPrefixes()) {
            if (name.startsWith(accessorGetterPrefix)) {
                return Utils.substringAndUncapitalize(name, accessorGetterPrefix.length());
            }
        }
        if (isRecord) {
            return name;
        }
        return null;
    }

    private final class NamesOfTypes {
        private final Map<String, TypeMirror> namesOfTypes;

        NamesOfTypes(int size) {
            this.namesOfTypes = new HashMap<>(size);
        }

        boolean contains(String name, TypeMirror componentType) {
            var type = namesOfTypes.get(name);
            if (type == null) {
                return false;
            }
            if (!types.isSameType(type, componentType)) {
                ctx.error("Components with same name '%s' have different types '%s' and '%s'", name, type, componentType);
            }
            return true;
        }

        void add(String name, TypeMirror componentType) {
            namesOfTypes.put(name, componentType);
        }
    }
}
