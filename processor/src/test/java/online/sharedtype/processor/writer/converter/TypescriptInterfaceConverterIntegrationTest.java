package online.sharedtype.processor.writer.converter;

import online.sharedtype.processor.context.Config;
import online.sharedtype.processor.context.ContextMocks;
import online.sharedtype.processor.context.Props;
import online.sharedtype.processor.domain.def.EnumDef;
import online.sharedtype.processor.domain.type.ArrayTypeInfo;
import online.sharedtype.processor.domain.def.ClassDef;
import online.sharedtype.processor.domain.type.ConcreteTypeInfo;
import online.sharedtype.processor.domain.component.FieldComponentInfo;
import online.sharedtype.processor.domain.type.MapTypeInfo;
import online.sharedtype.processor.domain.type.TypeVariableInfo;
import online.sharedtype.processor.writer.converter.type.TypeExpressionConverter;
import online.sharedtype.processor.writer.render.Template;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static online.sharedtype.processor.context.Props.Typescript.OptionalFieldFormat.NULL;
import static online.sharedtype.processor.context.Props.Typescript.OptionalFieldFormat.QUESTION_MARK;
import static online.sharedtype.processor.context.Props.Typescript.OptionalFieldFormat.UNDEFINED;
import static online.sharedtype.processor.domain.Constants.INT_TYPE_INFO;
import static online.sharedtype.processor.domain.Constants.STRING_TYPE_INFO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class TypescriptInterfaceConverterIntegrationTest {
    private final ContextMocks ctxMocks = new ContextMocks();
    private final TypescriptInterfaceConverter converter = new TypescriptInterfaceConverter(
        ctxMocks.getContext(), TypeExpressionConverter.typescript(ctxMocks.getContext())
    );
    private final Config config = mock(Config.class);

    @Test
    void skipEmptyClassDef() {
        ClassDef classDef = ClassDef.builder().simpleName("Abc").build();
        assertThat(converter.shouldAccept(classDef)).isFalse();

        classDef.setDepended(true);
        assertThat(converter.shouldAccept(classDef)).isTrue();
    }

    @Test
    void writeInterface() {
        ClassDef classDef = ClassDef.builder()
            .qualifiedName("com.github.cuzfrog.ClassA")
            .simpleName("ClassA")
            .typeVariables(Arrays.asList(
                TypeVariableInfo.builder().name("T").build(),
                TypeVariableInfo.builder().name("U").build()
            ))
            .supertypes(Collections.singletonList(
                ConcreteTypeInfo.builder()
                    .qualifiedName("com.github.cuzfrog.SuperClassA")
                    .simpleName("SuperClassA")
                    .typeArgs(Collections.singletonList(TypeVariableInfo.builder().name("U").build()))
                    .build()
            ))
            .components(Arrays.asList(
                FieldComponentInfo.builder().name("field1").type(INT_TYPE_INFO).optional(true).build(),
                FieldComponentInfo.builder().name("field2").type(STRING_TYPE_INFO).optional(false).build(),
                FieldComponentInfo.builder().name("field3")
                    .type(
                        new ArrayTypeInfo(
                            new ArrayTypeInfo(
                                ConcreteTypeInfo.builder()
                                    .qualifiedName("com.github.cuzfrog.Container")
                                    .simpleName("Container")
                                    .typeArgs(Collections.singletonList(TypeVariableInfo.builder().name("T").build()))
                                    .build()
                            )
                        )
                    )
                    .build(),
                FieldComponentInfo.builder().name("field4")
                    .type(new ArrayTypeInfo(TypeVariableInfo.builder().name("T").build()))
                    .build(),
                FieldComponentInfo.builder().name("mapField")
                    .type(
                        MapTypeInfo.builder()
                            .qualifiedName("java.util.Map")
                            .keyType(STRING_TYPE_INFO).valueType(INT_TYPE_INFO)
                            .build()
                    )
                    .build(),
                FieldComponentInfo.builder().name("mapFieldEnumKey")
                    .type(
                        MapTypeInfo.builder()
                            .qualifiedName("java.util.Map")
                            .keyType(
                                ConcreteTypeInfo.builder().simpleName("MyEnum").kind(ConcreteTypeInfo.Kind.ENUM)
                                    .typeDef(EnumDef.builder().simpleName("MyEnum").qualifiedName("com.github.cuzfrog.MyEnum").build())
                                    .build())
                            .valueType(INT_TYPE_INFO)
                            .build()
                    )
                    .build(),
                FieldComponentInfo.builder().name("cyclicReferencedField")
                    .type(
                        ConcreteTypeInfo.builder()
                            .qualifiedName("com.github.cuzfrog.CyclicA").simpleName("CyclicA")
                            .typeDef(ClassDef.builder().qualifiedName("com.github.cuzfrog.CyclicA").simpleName("CyclicA").cyclicReferenced(true).build())
                            .build()
                    )
                    .build()
            ))
            .build();

        when(config.getTypescriptOptionalFieldFormats()).thenReturn(Set.of(QUESTION_MARK));
        when(config.getTypescriptFieldReadonly()).thenReturn(Props.Typescript.FieldReadonlyType.ACYCLIC);
        when(ctxMocks.getContext().getTypeStore().getConfig(classDef)).thenReturn(config);

        var tuple = converter.convert(classDef);
        assertThat(tuple).isNotNull();
        assertThat(tuple.a()).isEqualTo(Template.TEMPLATE_TYPESCRIPT_INTERFACE);
        TypescriptInterfaceConverter.InterfaceExpr model = (TypescriptInterfaceConverter.InterfaceExpr) tuple.b();
        assertThat(model.name).isEqualTo("ClassA");
        assertThat(model.typeParameters).containsExactly("T", "U");
        assertThat(model.supertypes).containsExactly("SuperClassA<U>");
        assertThat(model.properties).hasSize(7);
        TypescriptInterfaceConverter.PropertyExpr prop1 = model.properties.get(0);
        assertThat(prop1.name).isEqualTo("field1");
        assertThat(prop1.type).isEqualTo("number");
        assertThat(prop1.optional).isTrue();
        assertThat(prop1.unionNull).isFalse();
        assertThat(prop1.unionUndefined).isFalse();
        assertThat(prop1.readonly).isTrue();

        TypescriptInterfaceConverter.PropertyExpr prop2 = model.properties.get(1);
        assertThat(prop2.name).isEqualTo("field2");
        assertThat(prop2.type).isEqualTo("string");
        assertThat(prop2.optional).isFalse();

        TypescriptInterfaceConverter.PropertyExpr prop3 = model.properties.get(2);
        assertThat(prop3.name).isEqualTo("field3");
        assertThat(prop3.type).isEqualTo("Container<T>[][]");
        assertThat(prop3.optional).isFalse();

        TypescriptInterfaceConverter.PropertyExpr prop4 = model.properties.get(3);
        assertThat(prop4.name).isEqualTo("field4");
        assertThat(prop4.type).isEqualTo("T[]");
        assertThat(prop4.optional).isFalse();

        TypescriptInterfaceConverter.PropertyExpr prop5 = model.properties.get(4);
        assertThat(prop5.name).isEqualTo("mapField");
        assertThat(prop5.type).isEqualTo("Record<string, number>");

        TypescriptInterfaceConverter.PropertyExpr prop6 = model.properties.get(5);
        assertThat(prop6.name).isEqualTo("mapFieldEnumKey");
        assertThat(prop6.type).isEqualTo("Partial<Record<MyEnum, number>>");

        TypescriptInterfaceConverter.PropertyExpr prop7 = model.properties.get(6);
        assertThat(prop7.name).isEqualTo("cyclicReferencedField");
        assertThat(prop7.type).isEqualTo("CyclicA");
        assertThat(prop7.readonly).isFalse();
    }

    @Test
    void optionalFieldUnionNullAndUndefined() {
        ClassDef classDef = ClassDef.builder()
            .qualifiedName("com.github.cuzfrog.ClassA")
            .simpleName("ClassA")
            .components(Collections.singletonList(
                FieldComponentInfo.builder().name("field1").type(INT_TYPE_INFO).optional(true).build()
            ))
            .build();

        when(config.getTypescriptOptionalFieldFormats()).thenReturn(Set.of(NULL, UNDEFINED));
        when(config.getTypescriptFieldReadonly()).thenReturn(Props.Typescript.FieldReadonlyType.NONE);
        when(ctxMocks.getContext().getTypeStore().getConfig(classDef)).thenReturn(config);

        var tuple = converter.convert(classDef);
        assertThat(tuple).isNotNull();
        TypescriptInterfaceConverter.InterfaceExpr model = (TypescriptInterfaceConverter.InterfaceExpr) tuple.b();
        TypescriptInterfaceConverter.PropertyExpr prop1 = model.properties.get(0);
        assertThat(prop1.optional).isFalse();
        assertThat(prop1.unionNull).isTrue();
        assertThat(prop1.unionUndefined).isTrue();
        assertThat(prop1.readonly).isFalse();
    }

    @Test
    void writeInterfaceWithBounds() {
        ClassDef classDef = ClassDef.builder()
            .qualifiedName("com.github.cuzfrog.ClassA")
            .simpleName("ClassA")
            .typeVariables(Collections.singletonList(
                TypeVariableInfo.builder()
                    .name("T")
                    .bounds(Collections.singletonList(
                        ConcreteTypeInfo.builder()
                            .qualifiedName("com.github.cuzfrog.Shape")
                            .simpleName("Shape")
                            .build()
                    ))
                    .build()
            ))
            .components(Collections.emptyList())
            .build();

        when(ctxMocks.getContext().getTypeStore().getConfig(classDef)).thenReturn(config);
        when(config.getTypescriptFieldReadonly()).thenReturn(Props.Typescript.FieldReadonlyType.NONE);

        var tuple = converter.convert(classDef);
        assertThat(tuple).isNotNull();
        TypescriptInterfaceConverter.InterfaceExpr model = (TypescriptInterfaceConverter.InterfaceExpr) tuple.b();
        assertThat(model.name).isEqualTo("ClassA");
        assertThat(model.typeParameters).containsExactly("T extends Shape");
    }
}
