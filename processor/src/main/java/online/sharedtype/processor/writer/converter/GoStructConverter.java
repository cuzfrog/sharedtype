package online.sharedtype.processor.writer.converter;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import online.sharedtype.SharedType;
import online.sharedtype.processor.domain.component.ComponentInfo;
import online.sharedtype.processor.domain.component.FieldComponentInfo;
import online.sharedtype.processor.domain.def.ClassDef;
import online.sharedtype.processor.domain.def.TypeDef;
import online.sharedtype.processor.support.utils.Tuple;
import online.sharedtype.processor.writer.converter.type.TypeExpressionConverter;
import online.sharedtype.processor.writer.render.Template;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static online.sharedtype.processor.support.utils.Utils.isBlank;

@RequiredArgsConstructor
final class GoStructConverter extends AbstractStructConverter {
    private final TypeExpressionConverter typeExpressionConverter;

    @Override
    public Tuple<Template, AbstractTypeExpr> convert(TypeDef typeDef) {
        ClassDef classDef = (ClassDef) typeDef;
        StructExpr value = new StructExpr(
            classDef.simpleName(),
            classDef.typeVariables().stream().map(typeVar -> {
                String name = typeVar.name();
                if (typeVar.bounds().isEmpty()) {
                    return name + " any";
                }
                String bounds = typeVar.bounds().stream()
                    .map(b -> typeExpressionConverter.toTypeExpr(b, typeDef))
                    .collect(Collectors.joining("; "));

                if (typeVar.bounds().size() > 1) {
                    return name + " interface{ " + bounds + " }";
                }
                return name + " " + bounds;
            }).collect(Collectors.toList()),
            classDef.directSupertypes().stream().map(typeInfo1 -> typeExpressionConverter.toTypeExpr(typeInfo1, typeDef)).collect(Collectors.toList()),
            gatherProperties(classDef)
        );
        return Tuple.of(Template.TEMPLATE_GO_STRUCT, value);
    }

    private List<PropertyExpr> gatherProperties(ClassDef classDef) {
        List<PropertyExpr> properties = new ArrayList<>();
        for (FieldComponentInfo component : classDef.components()) {
            properties.add(toPropertyExpr(component, classDef));
        }
        return properties;
    }

    private PropertyExpr toPropertyExpr(FieldComponentInfo field, TypeDef contextTypeDef) {
        return new PropertyExpr(
            field,
            typeExpressionConverter.toTypeExpr(field.type(), contextTypeDef),
            ConversionUtils.isOptionalField(field)
        );
    }

    @SuppressWarnings("unused")
    @RequiredArgsConstructor
    static final class StructExpr extends AbstractTypeExpr {
        final String name;
        final List<String> typeParameters;
        final List<String> supertypes;
        final List<PropertyExpr> properties;

        String typeParametersExpr() {
            if (typeParameters.isEmpty()) {
                return null;
            }
            return String.format("[%s]", String.join(", ", typeParameters));
        }
    }

    @ToString
    @SuppressWarnings("unused")
    @EqualsAndHashCode(of = {}, callSuper = true)
    static final class PropertyExpr extends AbstractFieldExpr {
        final String type;
        final boolean optional;
        PropertyExpr(ComponentInfo componentInfo, String type, boolean optional) {
            super(componentInfo, SharedType.TargetType.GO);
            this.type = type;
            this.optional = optional;
        }

        String capitalizedName() {
            return ConversionUtils.capitalize(name);
        }

        String typeExpr() {
            if (optional) {
                return String.format("*%s", type);
            }
            return type;
        }

        String inlineTagsExpr() {
            if (!isBlank(tagLiteralsInlineAfter)) {
                return tagLiteralsInlineAfter;
            }

            Set<String> jsonTags = new HashSet<>(2);
            jsonTags.add(name);
            if (optional) {
                jsonTags.add("omitempty");
            }
            return String.format("`json:\"%s\"`", String.join(",", jsonTags));
        }
    }
}
