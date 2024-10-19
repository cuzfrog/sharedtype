package org.sharedtype.processor.parser;

import org.junit.jupiter.api.Test;
import org.sharedtype.processor.context.ContextMocks;
import org.sharedtype.processor.domain.ClassDef;
import org.sharedtype.processor.domain.ConcreteTypeInfo;
import org.sharedtype.processor.parser.type.TypeInfoParser;

import javax.lang.model.element.ElementKind;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TypeDefParserTest {
    private final ContextMocks ctxMocks = new ContextMocks();
    private final TypeInfoParser typeInfoParser = mock(TypeInfoParser.class);
    private final TypeDefParserImpl parser = new TypeDefParserImpl(ctxMocks.getContext(), typeInfoParser);

    @Test
    void parseClass() {
        var field1 = ctxMocks.variableElement("field1", PrimitiveType.class).withElementKind(ElementKind.FIELD);
        var field2 = ctxMocks.variableElement("field2", DeclaredType.class).withElementKind(ElementKind.FIELD);
        var element = ctxMocks.typeElement("com.github.cuzfrog.Abc")
          .withEnclosedElements(
            field1.element(),
            field2.element()
          )
          .withTypeParameters(
            ctxMocks.typeParameterElement("T").element(),
            ctxMocks.typeParameterElement("U").element()
          )
          .withSuperClass(
            ctxMocks.typeElement("com.github.cuzfrog.SuperClassA")
              .withEnclosedElements(
                ctxMocks.variableElement("a", PrimitiveType.class).withElementKind(ElementKind.FIELD).element()
              )
              .type()
          )
          .withInterfaces(
            ctxMocks.typeElement("com.github.cuzfrog.InterfaceA").type(),
            ctxMocks.typeElement("com.github.cuzfrog.InterfaceB").type()
          )
          .element();

        var parsedField1Type = ConcreteTypeInfo.builder().qualifiedName("int").build();
        var parsedField2Type = ConcreteTypeInfo.builder().qualifiedName("java.lang.String").build();
        when(typeInfoParser.parse(field1.type())).thenReturn(parsedField1Type);
        when(typeInfoParser.parse(field2.type())).thenReturn(parsedField2Type);

        var defs = parser.parse(element);
        assertThat(defs).hasSize(1);
        var classDef = (ClassDef) defs.get(0);
        assertThat(classDef.name()).isEqualTo("Abc");

        // components
        assertThat(classDef.components()).hasSize(2);
        var field1Def = classDef.components().get(0);
        assertThat(field1Def.name()).isEqualTo("field1");
        assertThat(field1Def.type()).isEqualTo(parsedField1Type);
        var field2Def = classDef.components().get(1);
        assertThat(field2Def.name()).isEqualTo("field2");
        assertThat(field2Def.type()).isEqualTo(parsedField2Type);

        // type variables
        assertThat(classDef.typeVariables()).hasSize(2);
        var typeVar1 = classDef.typeVariables().get(0);
        assertThat(typeVar1.getName()).isEqualTo("T");
        var typeVar2 = classDef.typeVariables().get(1);
        assertThat(typeVar2.getName()).isEqualTo("U");

        // supertypes
        assertThat(classDef.supertypes()).hasSize(3);
        var supertype1 = (ClassDef)classDef.supertypes().get(0);
        assertThat(supertype1.qualifiedName()).isEqualTo("com.github.cuzfrog.SuperClassA");
        assertThat(supertype1.components()).hasSize(1);
        var supertype1Field = supertype1.components().get(0);
        assertThat(supertype1Field.name()).isEqualTo("a");
        var supertype2 = classDef.supertypes().get(1);
        assertThat(supertype2.qualifiedName()).isEqualTo("com.github.cuzfrog.InterfaceA");
        var supertype3 = classDef.supertypes().get(2);
        assertThat(supertype3.qualifiedName()).isEqualTo("com.github.cuzfrog.InterfaceB");
    }
}