package online.sharedtype.processor.parser;

import online.sharedtype.SharedType;
import online.sharedtype.processor.context.Config;
import online.sharedtype.processor.context.ContextMocks;
import online.sharedtype.processor.context.TestUtils;
import online.sharedtype.processor.context.TypeElementMock;
import online.sharedtype.processor.domain.Constants;
import online.sharedtype.processor.domain.def.ClassDef;
import online.sharedtype.processor.domain.type.ConcreteTypeInfo;
import online.sharedtype.processor.parser.type.TypeInfoParser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import online.sharedtype.processor.support.annotation.Nullable;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class ClassTypeDefParserTest {
    private final ContextMocks ctxMocks = new ContextMocks();
    private final TypeInfoParser typeInfoParser = mock(TypeInfoParser.class);
    private final ClassTypeDefParser parser = new ClassTypeDefParser(ctxMocks.getContext(), typeInfoParser);

    private final ArgumentCaptor<Config> configCaptor = ArgumentCaptor.forClass(Config.class);

    private final TypeElementMock string = ctxMocks.typeElement("java.lang.String");

    @Test
    void parseComplexClass() {
        var staticField1 = ctxMocks.primitiveVariable("CONST_VALUE", TypeKind.INT)
            .withModifiers(Modifier.STATIC); // will be ignored
        var field1 = ctxMocks.primitiveVariable("field1", TypeKind.BOOLEAN);
        var field2 = ctxMocks.declaredTypeVariable("field2", string.type()).withElementKind(ElementKind.FIELD).withAnnotation(Nullable.class);
        when(ctxMocks.getContext().isOptionalAnnotated(field2.element())).thenReturn(true);
        var method1 = ctxMocks.executable("method1").withElementKind(ElementKind.METHOD);
        var method2 = ctxMocks.executable("getValue").withElementKind(ElementKind.METHOD);
        var supertype1 = ctxMocks.typeElement("com.github.cuzfrog.SuperClassA");
        var supertype2 = ctxMocks.typeElement("com.github.cuzfrog.InterfaceA");
        var supertype3 = ctxMocks.typeElement("com.github.cuzfrog.InterfaceB");
        var anno = TestUtils.spiedDefaultSharedTypeAnnotation();
        var clazz = ctxMocks.typeElement("com.github.cuzfrog.Abc")
            .withAnnotation(SharedType.class, () -> anno)
            .withEnclosedElements(
                staticField1.element(),
                field1.element(),
                field2.element(),
                method1.element(),
                method2.element()
            )
            .withTypeParameters(
                ctxMocks.typeParameter("T").element(),
                ctxMocks.typeParameter("U").element()
            )
            .withSuperClass(
                supertype1.type()
            )
            .withInterfaces(
                supertype2.type(),
                supertype3.type()
            )
            .element();

        var parsedField1Type = Constants.BOOLEAN_TYPE_INFO;
        var parsedField2Type = Constants.STRING_TYPE_INFO;
        var parsedMethod2Type = Constants.INT_TYPE_INFO;
        var parsedSupertype1 = ConcreteTypeInfo.builder().qualifiedName("com.github.cuzfrog.SuperClassA").build();
        var parsedSupertype2 = ConcreteTypeInfo.builder().qualifiedName("com.github.cuzfrog.InterfaceA").build();
        var parsedSupertype3 = ConcreteTypeInfo.builder().qualifiedName("com.github.cuzfrog.InterfaceB").build();
        when(typeInfoParser.parse(field1.type(), clazz)).thenReturn(parsedField1Type);
        when(typeInfoParser.parse(field2.type(), clazz)).thenReturn(parsedField2Type);
        when(typeInfoParser.parse(method2.type(), clazz)).thenReturn(parsedMethod2Type);
        when(typeInfoParser.parse(supertype1.type(), clazz)).thenReturn(parsedSupertype1);
        when(typeInfoParser.parse(supertype2.type(), clazz)).thenReturn(parsedSupertype2);
        when(typeInfoParser.parse(supertype3.type(), clazz)).thenReturn(parsedSupertype3);

        var parsedSelfTypeInfo = ConcreteTypeInfo.builder().qualifiedName("com.github.cuzfrog.Abc").build();
        when(typeInfoParser.parse(clazz.asType(), clazz)).thenReturn(parsedSelfTypeInfo);
        InOrder inOrder = inOrder(typeInfoParser);

        var classDefs = parser.parse(clazz);
        var classDef = (ClassDef)classDefs.get(0);
        assertThat(parsedField1Type.referencingTypes()).containsExactly(classDef);
        assertThat(classDef.simpleName()).isEqualTo("Abc");

        // components
        assertThat(classDef.components()).hasSize(3).satisfiesExactly(
            component -> {
                assertThat(component.name()).isEqualTo("field1");
                assertThat(component.type()).isEqualTo(parsedField1Type);
                assertThat(component.optional()).isFalse();
                assertThat(component.getElement()).isEqualTo(field1.element());
            },
            component -> {
                assertThat(component.name()).isEqualTo("field2");
                assertThat(component.type()).isEqualTo(parsedField2Type);
                assertThat(component.optional()).isTrue();
                assertThat(component.getElement()).isEqualTo(field2.element());
            },
            component -> {
                assertThat(component.name()).isEqualTo("value");
                assertThat(component.type()).isEqualTo(parsedMethod2Type);
                assertThat(component.optional()).isFalse();
            }
        );

        // type variables
        assertThat(classDef.typeVariables()).hasSize(2);
        var typeVar1 = classDef.typeVariables().get(0);
        assertThat(typeVar1.name()).isEqualTo("T");
        assertThat(typeVar1.qualifiedName()).isEqualTo("com.github.cuzfrog.Abc@T");
        var typeVar2 = classDef.typeVariables().get(1);
        assertThat(typeVar2.name()).isEqualTo("U");
        assertThat(typeVar2.contextTypeQualifiedName()).isEqualTo("com.github.cuzfrog.Abc");

        // supertypes
        assertThat(classDef.directSupertypes()).containsExactly(parsedSupertype1, parsedSupertype2, parsedSupertype3);

        inOrder.verify(typeInfoParser).parse(field1.type(), clazz);
        inOrder.verify(typeInfoParser).parse(field2.type(), clazz);
        inOrder.verify(typeInfoParser).parse(method2.type(), clazz);
        inOrder.verify(typeInfoParser).parse(supertype1.type(), clazz);
        inOrder.verify(typeInfoParser).parse(supertype2.type(), clazz);
        inOrder.verify(typeInfoParser).parse(supertype3.type(), clazz);

        // self typeInfo
        assertThat(classDef.typeInfoSet()).satisfiesExactly(typeInfo -> assertThat(typeInfo).isSameAs(parsedSelfTypeInfo));

        // config
        verify(ctxMocks.getTypeStore()).saveConfig(configCaptor.capture());
        var config = configCaptor.getValue();
        assertThat(config.getQualifiedName()).isEqualTo("com.github.cuzfrog.Abc");
        assertThat(config.getAnno()).isSameAs(anno);
        assertThat(classDef.isAnnotated()).isTrue();
    }

    @Test
    void ignoreGlobalConfiguredField() {
        var fieldElement = ctxMocks.declaredTypeVariable("field333", string.type()).withElementKind(ElementKind.FIELD).element();
        var typeElement = ctxMocks.typeElement("com.github.cuzfrog.Abc")
            .withEnclosedElements(
                fieldElement
            )
            .element();
        when(ctxMocks.getContext().isIgnored(fieldElement)).thenReturn(true);
        var typeInfo = ConcreteTypeInfo.builder().qualifiedName("com.github.cuzfrog.Abc").build();
        when(typeInfoParser.parse(typeElement.asType(), typeElement)).thenReturn(typeInfo);

        var classDef = parser.parse(typeElement).get(0);
        assertThat(classDef.components()).isEmpty();
    }

    @Test
    void parseTypeBounds() {
        var boundTypeMock = ctxMocks.typeElement("com.example.Bound");
        var boundType = boundTypeMock.type();
        var typeParam = ctxMocks.typeParameter("T");
        
        java.util.List<javax.lang.model.type.TypeMirror> bounds = new java.util.ArrayList<>();
        bounds.add(boundType);
        org.mockito.Mockito.doReturn(bounds).when(typeParam.element()).getBounds();

        var clazz = ctxMocks.typeElement("com.example.MyClass")
             .withTypeParameters(typeParam.element())
             .element();

        var parsedBoundType = ConcreteTypeInfo.builder().qualifiedName("com.example.Bound").build();
        when(typeInfoParser.parse(boundType, clazz)).thenReturn(parsedBoundType);
        
        var parsedSelfTypeInfo = ConcreteTypeInfo.builder().qualifiedName("com.example.MyClass").build();
        when(typeInfoParser.parse(clazz.asType(), clazz)).thenReturn(parsedSelfTypeInfo);

        var classDefs = parser.parse(clazz);
        var classDef = (ClassDef)classDefs.get(0);
        
        assertThat(classDef.typeVariables()).hasSize(1);
        var typeVar = classDef.typeVariables().get(0);
        assertThat(typeVar.name()).isEqualTo("T");
        assertThat(typeVar.bounds()).containsExactly(parsedBoundType);
    }

    @Test
    void parseTypeBoundsWithObject() {
        var objectTypeMock = ctxMocks.typeElement("java.lang.Object");
        var objectType = objectTypeMock.type();
        var typeParam = ctxMocks.typeParameter("T");
        
        java.util.List<javax.lang.model.type.TypeMirror> bounds = new java.util.ArrayList<>();
        bounds.add(objectType);
        org.mockito.Mockito.doReturn(bounds).when(typeParam.element()).getBounds();
        when(objectType.toString()).thenReturn("java.lang.Object");

        var clazz = ctxMocks.typeElement("com.example.MyClass")
             .withTypeParameters(typeParam.element())
             .element();
        
        var parsedSelfTypeInfo = ConcreteTypeInfo.builder().qualifiedName("com.example.MyClass").build();
        when(typeInfoParser.parse(clazz.asType(), clazz)).thenReturn(parsedSelfTypeInfo);

        var classDefs = parser.parse(clazz);
        var classDef = (ClassDef)classDefs.get(0);
        
        assertThat(classDef.typeVariables()).hasSize(1);
        var typeVar = classDef.typeVariables().get(0);
        assertThat(typeVar.name()).isEqualTo("T");
        assertThat(typeVar.bounds()).isEmpty();
    }
}
