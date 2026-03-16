package online.sharedtype.processor.writer.converter;

import online.sharedtype.SharedType;
import online.sharedtype.processor.context.ContextMocks;
import online.sharedtype.processor.domain.Constants;
import online.sharedtype.processor.domain.component.FieldComponentInfo;
import online.sharedtype.processor.domain.component.TagLiteralContainer;
import online.sharedtype.processor.domain.def.ClassDef;
import online.sharedtype.processor.domain.type.ConcreteTypeInfo;
import online.sharedtype.processor.domain.type.MapTypeInfo;
import online.sharedtype.processor.domain.type.TypeVariableInfo;
import online.sharedtype.processor.writer.converter.type.TypeExpressionConverter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class GoStructConverterTest {
    private final ContextMocks ctxMocks = new ContextMocks();
    private final TypeExpressionConverter typeExpressionConverter = TypeExpressionConverter.go(ctxMocks.getContext());
    private final GoStructConverter converter = new GoStructConverter(typeExpressionConverter);

    @Test
    void convert() {
        ConcreteTypeInfo recursiveTypeInfo = ConcreteTypeInfo.builder()
            .qualifiedName("com.github.cuzfrog.RecursiveClass")
            .simpleName("RecursiveClass")
            .build();
        ClassDef recursiveTypeDef = ClassDef.builder()
            .simpleName("RecursiveClass")
            .qualifiedName("com.github.cuzfrog.RecursiveClass")
            .components(List.of(
                FieldComponentInfo.builder()
                    .name("recursiveRef")
                    .type(recursiveTypeInfo)
                    .build()
            ))
            .cyclicReferenced(true)
            .build();
        recursiveTypeInfo.markShallowResolved(recursiveTypeDef);

        ClassDef classDef = ClassDef.builder()
            .simpleName("ClassA")
            .qualifiedName("com.github.cuzfrog.ClassA")
            .typeVariables(List.of(
                TypeVariableInfo.builder().name("T").contextTypeQualifiedName("com.github.cuzfrog.ClassA").build()
            ))
            .components(List.of(
                FieldComponentInfo.builder()
                    .name("field1")
                    .type(Constants.INT_TYPE_INFO)
                    .build(),
                FieldComponentInfo.builder()
                    .name("field2")
                    .type(TypeVariableInfo.builder().name("T").contextTypeQualifiedName("com.github.cuzfrog.ClassA").build())
                    .build(),
                FieldComponentInfo.builder()
                    .name("field3")
                    .type(recursiveTypeInfo)
                    .build(),
                FieldComponentInfo.builder()
                    .name("mapField")
                    .type(MapTypeInfo.builder()
                        .qualifiedName("java.util.Map")
                        .keyType(Constants.STRING_TYPE_INFO)
                        .valueType(Constants.INT_TYPE_INFO)
                        .build()
                    )
                    .build()
            ))
            .supertypes(List.of(
                ConcreteTypeInfo.builder()
                    .qualifiedName("com.github.cuzfrog.SuperClassA")
                    .simpleName("SuperClassA")
                    .typeArgs(List.of(
                        Constants.STRING_TYPE_INFO
                    ))
                    .build()
            ))
            .build();

        var data = converter.convert(classDef);
        assertThat(data).isNotNull();
        var model = (GoStructConverter.StructExpr) data.b();
        assertThat(model.name).isEqualTo("ClassA");
        assertThat(model.typeParameters).containsExactly("T any");
        assertThat(model.typeParametersExpr()).isEqualTo("[T any]");
        assertThat(model.supertypes).containsExactly("SuperClassA[string]");

        assertThat(model.properties).hasSize(4);
        GoStructConverter.PropertyExpr prop1 = model.properties.get(0);
        assertThat(prop1.name).isEqualTo("field1");
        assertThat(prop1.capitalizedName()).isEqualTo("Field1");
        assertThat(prop1.type).isEqualTo("int32");
        assertThat(prop1.typeExpr()).isEqualTo("int32");
        assertThat(prop1.optional).isFalse();
        assertThat(prop1.inlineTagsExpr()).isEqualTo("`json:\"field1\"`");

        GoStructConverter.PropertyExpr prop2 = model.properties.get(1);
        assertThat(prop2.name).isEqualTo("field2");
        assertThat(prop2.type).isEqualTo("T");

        GoStructConverter.PropertyExpr prop3 = model.properties.get(2);
        assertThat(prop3.name).isEqualTo("field3");
        assertThat(prop3.type).isEqualTo("RecursiveClass");
        assertThat(prop3.typeExpr()).isEqualTo("*RecursiveClass");
        assertThat(prop3.optional).isTrue();
        assertThat(prop3.inlineTagsExpr()).isEqualTo("`json:\"field3,omitempty\"`");

        GoStructConverter.PropertyExpr prop5 = model.properties.get(3);
        assertThat(prop5.name).isEqualTo("mapField");
        assertThat(prop5.capitalizedName()).isEqualTo("MapField");
        assertThat(prop5.type).isEqualTo("map[string]int32");
    }

    @Test
    void convertTypeWithBounds() {
        ClassDef classDef = ClassDef.builder()
            .simpleName("ClassA")
            .qualifiedName("com.github.cuzfrog.ClassA")
            .typeVariables(List.of(
                TypeVariableInfo.builder()
                    .name("T")
                    .bounds(List.of(
                        ConcreteTypeInfo.builder()
                            .qualifiedName("com.github.cuzfrog.Shape")
                            .simpleName("Shape")
                            .build()
                    ))
                    .build()
            ))
            .components(List.of())
            .build();

        var data = converter.convert(classDef);
        var model = (GoStructConverter.StructExpr) data.b();

        assertThat(model.name).isEqualTo("ClassA");
        assertThat(model.typeParameters).containsExactly("T Shape");
        assertThat(model.typeParametersExpr()).isEqualTo("[T Shape]");
    }

    @Test
    void inlineTagsOverrideDefaultTags() {
        var propertyExpr = new GoStructConverter.PropertyExpr(
            FieldComponentInfo.builder()
                .name("field1")
                .type(Constants.INT_TYPE_INFO)
                .tagLiterals(Map.of(SharedType.TargetType.GO, List.of(
                    new TagLiteralContainer(List.of("`json:\"-\"` //override"), SharedType.TagPosition.INLINE_AFTER)
                )))
                .build(),
            "int32",
            false
        );

        assertThat(propertyExpr.inlineTagsExpr()).isEqualTo("`json:\"-\"` //override");
    }
}
