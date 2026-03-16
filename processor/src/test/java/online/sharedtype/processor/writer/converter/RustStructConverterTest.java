package online.sharedtype.processor.writer.converter;

import online.sharedtype.processor.context.Config;
import online.sharedtype.processor.context.ContextMocks;
import online.sharedtype.processor.context.TestUtils;
import online.sharedtype.processor.domain.component.EnumValueInfo;
import online.sharedtype.processor.domain.def.ClassDef;
import online.sharedtype.processor.domain.type.ConcreteTypeInfo;
import online.sharedtype.processor.domain.Constants;
import online.sharedtype.processor.domain.def.EnumDef;
import online.sharedtype.processor.domain.component.FieldComponentInfo;
import online.sharedtype.processor.domain.type.MapTypeInfo;
import online.sharedtype.processor.domain.type.TypeVariableInfo;
import online.sharedtype.processor.domain.value.ValueHolder;
import online.sharedtype.processor.writer.converter.type.TypeExpressionConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Set;

import static online.sharedtype.processor.domain.type.ConcreteTypeInfo.Kind.ENUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class RustStructConverterTest {
    private final ContextMocks ctxMocks = new ContextMocks();
    private final TypeExpressionConverter rustTypeExpressionConverter = TypeExpressionConverter.rust(ctxMocks.getContext());
    private final RustMacroTraitsGenerator rustMacroTraitsGenerator = mock(RustMacroTraitsGenerator.class);

    private final RustStructConverter converter = new RustStructConverter(
        ctxMocks.getContext(), rustTypeExpressionConverter, rustMacroTraitsGenerator);

    private final Config config = mock(Config.class);

    @Test
    void skipNonClassDef() {
        assertThat(converter.shouldAccept(EnumDef.builder().build())).isFalse();
    }

    @Test
    void skipAnnotatedEmptyClassDef() {
        assertThat(converter.shouldAccept(ClassDef.builder().annotated(true).build())).isFalse();
    }

    @Test
    void shouldAcceptClassDefAnnotated() {
        List<FieldComponentInfo> components = List.of(FieldComponentInfo.builder().build());
        assertThat(converter.shouldAccept(ClassDef.builder().build())).isFalse();
        assertThat(converter.shouldAccept(ClassDef.builder().annotated(true).components(components).build())).isTrue();
        assertThat(converter.shouldAccept(ClassDef.builder().referencedByAnnotated(true).depended(true).build())).isTrue();
    }

    @Test
    void convertTypeWithEnumField() {
        var enumATypeInfo = ConcreteTypeInfo.builder().qualifiedName("com.github.cuzfrog.EnumA").simpleName("EnumA").kind(ENUM).build();
        EnumDef enumADef = EnumDef.builder()
            .simpleName("EnumA")
            .qualifiedName("com.github.cuzfrog.EnumA")
            .enumValueInfos(List.of(
                EnumValueInfo.builder().name("Value1").value(ValueHolder.ofEnum(enumATypeInfo, "Value1", Constants.BOOLEAN_TYPE_INFO, true)).build(),
                EnumValueInfo.builder().name("Value2").value(ValueHolder.ofEnum(enumATypeInfo, "Value2", Constants.BOOLEAN_TYPE_INFO, false)).build()
            ))
            .build();
        enumATypeInfo.markShallowResolved(enumADef);

        var enumBTypeInfo = ConcreteTypeInfo.builder().qualifiedName("com.github.cuzfrog.EnumB").simpleName("EnumB").kind(ENUM).build();
        EnumDef enumBDef = EnumDef.builder()
            .simpleName("EnumB")
            .qualifiedName("com.github.cuzfrog.EnumB")
            .enumValueInfos(List.of(
                EnumValueInfo.builder().name("ValueB1").value(ValueHolder.ofEnum(enumATypeInfo, "ValueB1", enumBTypeInfo, "ValueB1")).build()
            ))
            .build();
        enumBTypeInfo.markShallowResolved(enumBDef);

        ClassDef classDef = ClassDef.builder()
            .simpleName("ClassA")
            .qualifiedName("com.github.cuzfrog.ClassA")
            .components(List.of(
                FieldComponentInfo.builder()
                    .name("field1")
                    .type(enumATypeInfo)
                    .build(),
                FieldComponentInfo.builder()
                    .name("field2")
                    .type(enumBTypeInfo)
                    .build()
            ))
            .build();

        var data = converter.convert(classDef);
        var model = (RustStructConverter.StructExpr)data.b();

        assertThat(model.name).isEqualTo("ClassA");
        assertThat(model.properties).satisfiesExactly(
            v1 -> {
                assertThat(v1.name).isEqualTo("field1");
                assertThat(v1.type).isEqualTo("bool");
            },
            v2 -> {
                assertThat(v2.name).isEqualTo("field2");
                assertThat(v2.type).isEqualTo("EnumB");
            }
        );
    }

    @Test
    void convertComplexType() {
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
                    .typeDef(
                        ClassDef.builder()
                            .simpleName("SuperClassA")
                            .qualifiedName("com.github.cuzfrog.SuperClassA")
                            .typeVariables(List.of(
                                TypeVariableInfo.builder().name("T").contextTypeQualifiedName("com.github.cuzfrog.SuperClassA").build()
                            ))
                            .components(List.of(
                                FieldComponentInfo.builder()
                                    .name("superField1")
                                    .type(TypeVariableInfo.builder().name("T").contextTypeQualifiedName("com.github.cuzfrog.SuperClassA").build())
                                    .build()
                            ))
                            .build()
                    )
                    .build()
            ))
            .build();
        when(ctxMocks.getTypeStore().getConfig(classDef)).thenReturn(config);
        when(config.getAnno()).thenReturn(TestUtils.spiedDefaultSharedTypeAnnotation());
        when(rustMacroTraitsGenerator.generate(classDef)).thenReturn(Set.of("TestMacro"));

        var data = converter.convert(classDef);
        assertThat(data).isNotNull();
        var model = (RustStructConverter.StructExpr) data.b();
        assertThat(model.name).isEqualTo("ClassA");
        assertThat(model.typeParameters).containsExactly("T");
        assertThat(model.macroTraits).containsExactly("TestMacro");

        assertThat(model.properties).hasSize(5);
        RustStructConverter.PropertyExpr prop1 = model.properties.get(0);
        assertThat(prop1.name).isEqualTo("field1");
        assertThat(prop1.type).isEqualTo("i32");
        assertThat(prop1.optional).isFalse();
        assertThat(prop1.typeExpr()).isEqualTo("i32");

        RustStructConverter.PropertyExpr prop2 = model.properties.get(1);
        assertThat(prop2.name).isEqualTo("field2");
        assertThat(prop2.type).isEqualTo("T");
        assertThat(prop2.optional).isFalse();

        RustStructConverter.PropertyExpr prop3 = model.properties.get(2);
        assertThat(prop3.name).isEqualTo("field3");
        assertThat(prop3.type).isEqualTo("Box<RecursiveClass>");
        assertThat(prop3.optional).isTrue();
        assertThat(prop3.typeExpr()).isEqualTo("Option<Box<RecursiveClass>>");

        RustStructConverter.PropertyExpr prop5 = model.properties.get(3);
        assertThat(prop5.name).isEqualTo("mapField");
        assertThat(prop5.type).isEqualTo("HashMap<String, i32>");

        RustStructConverter.PropertyExpr prop4 = model.properties.get(4);
        assertThat(prop4.name).isEqualTo("superField1");
        assertThat(prop4.type).isEqualTo("String");
        assertThat(prop4.optional).isFalse();
        assertThat(prop4.typeExpr()).isEqualTo("String");
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
        var model = (RustStructConverter.StructExpr) data.b();

        assertThat(model.name).isEqualTo("ClassA");
        assertThat(model.typeParameters).containsExactly("T: Shape");
    }
}
