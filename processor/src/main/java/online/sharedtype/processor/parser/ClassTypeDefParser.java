package online.sharedtype.processor.parser;

import online.sharedtype.SharedType;
import online.sharedtype.processor.context.Config;
import online.sharedtype.processor.context.Context;
import online.sharedtype.processor.domain.def.ClassDef;
import online.sharedtype.processor.domain.type.ConcreteTypeInfo;
import online.sharedtype.processor.domain.component.FieldComponentInfo;
import online.sharedtype.processor.domain.def.TypeDef;
import online.sharedtype.processor.domain.type.TypeInfo;
import online.sharedtype.processor.domain.type.TypeVariableInfo;
import online.sharedtype.processor.parser.type.TypeInfoParser;
import online.sharedtype.processor.support.annotation.VisibleForTesting;
import online.sharedtype.processor.support.utils.Tuple;
import online.sharedtype.processor.support.utils.Utils;

import online.sharedtype.processor.support.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Cause Chung
 */
final class ClassTypeDefParser implements TypeDefParser {

    private static final Set<String> SUPPORTED_ELEMENT_KINDS = new HashSet<>(3);
    static {
        SUPPORTED_ELEMENT_KINDS.add(ElementKind.CLASS.name());
        SUPPORTED_ELEMENT_KINDS.add(ElementKind.INTERFACE.name());
        SUPPORTED_ELEMENT_KINDS.add("RECORD");
    }
    private final Context ctx;
    private final Types types;
    private final TypeInfoParser typeInfoParser;

    ClassTypeDefParser(Context ctx, TypeInfoParser typeInfoParser) {
        this.ctx = ctx;
        this.types = ctx.getProcessingEnv().getTypeUtils();
        this.typeInfoParser = typeInfoParser;
    }

    @Override
    public List<TypeDef> parse(TypeElement typeElement) {
        if (!SUPPORTED_ELEMENT_KINDS.contains(typeElement.getKind().name())) {
            return Collections.emptyList();
        }
        if (!isValidClassTypeElement(typeElement)) {
            return Collections.emptyList();
        }
        Config config = new Config(typeElement, ctx);
        ctx.getTypeStore().saveConfig(config);

        ClassDef classDef = ClassDef.builder().element(typeElement)
            .qualifiedName(config.getQualifiedName()).simpleName(config.getSimpleName()).annotated(config.isAnnotated())
            .build();

        classDef.typeVariables().addAll(parseTypeVariables(typeElement));
        classDef.components().addAll(parseComponents(typeElement, config, classDef));
        classDef.directSupertypes().addAll(parseSupertypes(typeElement));

        TypeInfo typeInfo = typeInfoParser.parse(typeElement.asType(), typeElement);
        ((ConcreteTypeInfo) typeInfo).markShallowResolved(classDef);
        return Collections.singletonList(classDef);
    }

    private boolean isValidClassTypeElement(TypeElement typeElement) {
        if (typeElement.getNestingKind() != NestingKind.TOP_LEVEL && !typeElement.getModifiers().contains(Modifier.STATIC)) {
            ctx.error(typeElement, "Class %s is not static, non-static inner class is not supported." +
                " Instance class may refer to its enclosing class's generic type without the type declaration on its own," +
                " which could break the generated code. Later version of SharedType may loosen this limitation.", typeElement);
            return false;
        }
        return true;
    }

    private List<TypeVariableInfo> parseTypeVariables(TypeElement typeElement) {
        List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
        return typeParameters.stream()
            .map(typeParameterElement ->
                TypeVariableInfo.builder()
                    .contextTypeQualifiedName(typeElement.getQualifiedName().toString())
                    .name(typeParameterElement.getSimpleName().toString())
                    .bounds(parseBounds(typeParameterElement, typeElement))
                    .build()
            )
            .collect(Collectors.toList());
    }

    private List<TypeInfo> parseBounds(TypeParameterElement typeParameterElement, TypeElement typeElement) {
        return typeParameterElement.getBounds().stream()
            .filter(b -> !"java.lang.Object".equals(b.toString()))
            .filter(b -> {
                Element e = ctx.getProcessingEnv().getTypeUtils().asElement(b);
                if (e == null || ctx.isIgnored(e)) {
                    return false;
                }
                if (e instanceof TypeElement) {
                    TypeElement te = (TypeElement) e;
                    return !ctx.isOptionalType(te.getQualifiedName().toString());
                }
                return true;
            })
            .map(b -> typeInfoParser.parse(b, typeElement))
            .collect(Collectors.toList());
    }

    private List<TypeInfo> parseSupertypes(TypeElement typeElement) {
        List<DeclaredType> supertypes = new ArrayList<>();
        TypeMirror superclass = typeElement.getSuperclass();
        if (superclass instanceof DeclaredType) { // superclass can be NoType.
            supertypes.add((DeclaredType) superclass);
        }

        List<? extends TypeMirror> interfaceTypes = typeElement.getInterfaces();
        for (TypeMirror interfaceType : interfaceTypes) {
            supertypes.add((DeclaredType) interfaceType);
        }

        List<TypeInfo> res = new ArrayList<>(supertypes.size());
        for (DeclaredType supertype : supertypes) {
            if (!ctx.isIgnored(supertype.asElement())) {
                res.add(typeInfoParser.parse(supertype, typeElement));
            }
        }
        return res;
    }

    private List<FieldComponentInfo> parseComponents(TypeElement typeElement, Config config, ClassDef classDef) {
        List<Tuple<Element, String>> componentElems = resolveComponents(typeElement, config);

        List<FieldComponentInfo> fields = new ArrayList<>(componentElems.size());
        for (Tuple<Element, String> tuple : componentElems) {
            Element element = tuple.a();
            TypeInfo fieldTypeInfo = typeInfoParser.parse(element.asType(), typeElement);
            fieldTypeInfo.addReferencingType(classDef);
            FieldComponentInfo fieldInfo = FieldComponentInfo.builder()
                .name(tuple.b())
                .optional(ctx.isOptionalAnnotated(element))
                .element(element)
                .type(fieldTypeInfo)
                .build();
            fields.add(fieldInfo);
        }
        return fields;
    }

    @VisibleForTesting
    List<Tuple<Element, String>> resolveComponents(TypeElement typeElement, Config config) {
        List<? extends Element> enclosedElements = typeElement.getEnclosedElements();
        List<Tuple<Element, String>> res = new ArrayList<>(enclosedElements.size());
        NamesOfTypes uniqueNamesOfTypes = new NamesOfTypes(enclosedElements.size(), typeElement);
        boolean includeAccessors = config.includes(SharedType.ComponentType.ACCESSORS);
        boolean includeFields = config.includes(SharedType.ComponentType.FIELDS);

        Set<String> instanceFieldNames = enclosedElements.stream()
            .filter(e -> e.getKind() == ElementKind.FIELD && !e.getModifiers().contains(Modifier.STATIC))
            .map(e -> e.getSimpleName().toString())
            .collect(Collectors.toSet());

        for (Element enclosedElement : enclosedElements) {
            if (ctx.isIgnored(enclosedElement)) {
                continue;
            }

            TypeMirror type = enclosedElement.asType();
            String name = enclosedElement.getSimpleName().toString();

            if (enclosedElement.getKind() == ElementKind.FIELD && enclosedElement instanceof VariableElement) {
                VariableElement variableElem = (VariableElement) enclosedElement;
                if (uniqueNamesOfTypes.contains(name, type)) {
                    continue;
                }
                if ((includeFields && instanceFieldNames.contains(name))) {
                    res.add(Tuple.of(variableElem, name));
                    uniqueNamesOfTypes.add(name, type);
                }
            } else if (includeAccessors && enclosedElement instanceof ExecutableElement) {
                ExecutableElement methodElem = (ExecutableElement) enclosedElement;
                boolean explicitAccessor = ctx.isExplicitAccessor(methodElem);
                if (!isZeroArgNonstaticMethod(methodElem)) {
                    if (explicitAccessor) {
                        ctx.warn(methodElem, "%s.%s annotated as an accessor is not a zero-arg nonstatic method.", typeElement, methodElem);
                    }
                    continue;
                }
                String baseName = getAccessorBaseName(name, instanceFieldNames.contains(name), explicitAccessor);
                if (baseName == null) {
                    continue;
                }
                TypeMirror returnType = methodElem.getReturnType();
                if (uniqueNamesOfTypes.contains(baseName, returnType)) {
                    continue;
                }
                res.add(Tuple.of(methodElem, baseName));
                uniqueNamesOfTypes.add(baseName, returnType);
            }
        }

        return res;
    }

    private static boolean isZeroArgNonstaticMethod(ExecutableElement componentElem) {
        if (componentElem.getKind() != ElementKind.METHOD || componentElem.getModifiers().contains(Modifier.STATIC)) {
            return false;
        }
        return componentElem.getParameters().isEmpty();
    }

    @Nullable
    private String getAccessorBaseName(String name, boolean isFluentGetter, boolean isExplicitAccessor) {
        if (isFluentGetter) {
            return name;
        }
        for (String accessorGetterPrefix : ctx.getProps().getAccessorGetterPrefixes()) {
            if (name.startsWith(accessorGetterPrefix)) {
                if (name.length() == accessorGetterPrefix.length()) {
                    return null;
                }
                return Utils.substringAndUncapitalize(name, accessorGetterPrefix.length());
            }
        }
        if (isExplicitAccessor) {
            return name;
        }
        return null;
    }

    private final class NamesOfTypes {
        private final TypeElement contextType;
        private final Map<String, TypeMirror> namesOfTypes;

        NamesOfTypes(int size, TypeElement contextType) {
            this.contextType = contextType;
            this.namesOfTypes = new HashMap<>(size);
        }

        boolean contains(String name, TypeMirror componentType) {
            TypeMirror type = namesOfTypes.get(name);
            if (type == null) {
                return false;
            }
            if (!types.isSameType(type, componentType)) { // TODO exception and log at higher level with element context
                ctx.error(contextType, "Type %s has conflicting components with same name '%s', because they have different types '%s' and '%s', they cannot be merged.",
                    contextType, name, type, componentType);
            }
            return true;
        }

        void add(String name, TypeMirror componentType) {
            namesOfTypes.put(name, componentType);
        }
    }
}
