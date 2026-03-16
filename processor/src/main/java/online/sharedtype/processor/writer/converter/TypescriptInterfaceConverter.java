package online.sharedtype.processor.writer.converter;

import lombok.RequiredArgsConstructor;
import online.sharedtype.SharedType;
import online.sharedtype.processor.context.Config;
import online.sharedtype.processor.context.Context;
import online.sharedtype.processor.context.Props;
import online.sharedtype.processor.domain.component.ComponentInfo;
import online.sharedtype.processor.domain.def.ClassDef;
import online.sharedtype.processor.domain.component.FieldComponentInfo;
import online.sharedtype.processor.domain.def.TypeDef;
import online.sharedtype.processor.support.exception.SharedTypeInternalError;
import online.sharedtype.processor.support.utils.Tuple;
import online.sharedtype.processor.writer.converter.type.TypeExpressionConverter;
import online.sharedtype.processor.writer.render.Template;

import java.util.List;
import java.util.stream.Collectors;

import static online.sharedtype.processor.context.Props.Typescript.OptionalFieldFormat.NULL;
import static online.sharedtype.processor.context.Props.Typescript.OptionalFieldFormat.QUESTION_MARK;
import static online.sharedtype.processor.context.Props.Typescript.OptionalFieldFormat.UNDEFINED;

final class TypescriptInterfaceConverter extends AbstractStructConverter {
    private final Context ctx;
    private final TypeExpressionConverter typeExpressionConverter;
    private final char interfacePropertyDelimiter;

    TypescriptInterfaceConverter(Context ctx, TypeExpressionConverter typeExpressionConverter) {
        this.ctx = ctx;
        interfacePropertyDelimiter = ctx.getProps().getTypescript().getInterfacePropertyDelimiter();
        this.typeExpressionConverter = typeExpressionConverter;
    }

    @Override
    public Tuple<Template, AbstractTypeExpr> convert(TypeDef typeDef) {
        ClassDef classDef = (ClassDef) typeDef;
        Config config = ctx.getTypeStore().getConfig(typeDef);
        InterfaceExpr value = new InterfaceExpr(
            classDef.simpleName(),
            classDef.typeVariables().stream().map(typeVar -> {
                String name = typeVar.name();
                if (typeVar.bounds().isEmpty()) {
                    return name;
                }
                String bounds = typeVar.bounds().stream()
                    .map(b -> typeExpressionConverter.toTypeExpr(b, typeDef))
                    .collect(Collectors.joining(" & "));
                return name + " extends " + bounds;
            }).collect(Collectors.toList()),
            classDef.directSupertypes().stream().map(typeInfo1 -> typeExpressionConverter.toTypeExpr(typeInfo1, typeDef)).collect(Collectors.toList()),
            classDef.components().stream().map(field -> toPropertyExpr(field, typeDef, config)).collect(Collectors.toList())
        );

        return Tuple.of(Template.TEMPLATE_TYPESCRIPT_INTERFACE, value);
    }

    private PropertyExpr toPropertyExpr(FieldComponentInfo field, TypeDef contextTypeDef, Config config) {
        boolean isFieldOptional = ConversionUtils.isOptionalField(field);
        return new PropertyExpr(
            field,
            typeExpressionConverter.toTypeExpr(field.type(), contextTypeDef),
            interfacePropertyDelimiter,
            isFieldOptional && config.getTypescriptOptionalFieldFormats().contains(QUESTION_MARK),
            isFieldOptional && config.getTypescriptOptionalFieldFormats().contains(NULL),
            isFieldOptional && config.getTypescriptOptionalFieldFormats().contains(UNDEFINED),
            isFieldReadonly(field, config)
        );
    }

    private static boolean isFieldReadonly(FieldComponentInfo field, Config config) {
        if (config.getTypescriptFieldReadonly() == Props.Typescript.FieldReadonlyType.NONE) {
            return false;
        }
        if (config.getTypescriptFieldReadonly() == Props.Typescript.FieldReadonlyType.ALL) {
            return true;
        }
        if (config.getTypescriptFieldReadonly() == Props.Typescript.FieldReadonlyType.ACYCLIC) {
            return !ConversionUtils.isOfCyclicReferencedType(field);
        }
        throw new SharedTypeInternalError("Unknown typescriptFieldReadonlyType: " + config.getTypescriptFieldReadonly());
    }

    @RequiredArgsConstructor
    @SuppressWarnings("unused")
    static final class InterfaceExpr extends AbstractTypeExpr {
        final String name;
        final List<String> typeParameters;
        final List<String> supertypes;
        final List<PropertyExpr> properties;

        String typeParametersExpr() {
            if (typeParameters.isEmpty()) {
                return null;
            }
            return String.format("<%s>", String.join(", ", typeParameters));
        }

        String supertypesExpr() {
            if (supertypes.isEmpty()) {
                return null;
            }
            return String.format("extends %s ", String.join(", ", supertypes));
        }
    }

    @SuppressWarnings("unused")
    static final class PropertyExpr extends AbstractFieldExpr {
        final String type;
        final char propDelimiter;
        final boolean optional;
        final boolean unionNull;
        final boolean unionUndefined;
        final boolean readonly;
        PropertyExpr(ComponentInfo componentInfo, String type, char propDelimiter, boolean optional, boolean unionNull,
                     boolean unionUndefined, boolean readonly) {
            super(componentInfo, SharedType.TargetType.TYPESCRIPT);
            this.type = type;
            this.propDelimiter = propDelimiter;
            this.optional = optional;
            this.unionNull = unionNull;
            this.unionUndefined = unionUndefined;
            this.readonly = readonly;
        }
    }
}
