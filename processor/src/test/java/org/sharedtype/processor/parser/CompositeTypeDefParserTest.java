package org.sharedtype.processor.parser;

import java.util.List;
import java.util.Map;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

import org.sharedtype.processor.context.Context;
import org.sharedtype.processor.domain.ClassDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
final class CompositeTypeDefParserTest {
    private @Mock TypeDefParser delegate1;
    private @Mock TypeDefParser delegate2;
    private CompositeTypeElementDefParser parser;

    private @Mock TypeElement typeElement;
    private final Context ctx = new Context(null, null);
    private final ClassDef typeInfo = ClassDef.builder().build();

    @BeforeEach
    void setUp() {
        parser = new CompositeTypeElementDefParser(
            ctx, 
            Map.of(
                ElementKind.RECORD, delegate1,
                ElementKind.ENUM, delegate2
        ));
        when(delegate1.parse(typeElement)).thenReturn(List.of(typeInfo));
        when(delegate2.parse(typeElement)).thenReturn(List.of(typeInfo));
    }

    @Test
    void parse() {
        when(typeElement.getKind()).thenReturn(ElementKind.RECORD);
        var infoList = parser.parse(typeElement);
        verify(delegate1).parse(typeElement);
        assertThat(infoList).containsExactly(typeInfo);

        when(typeElement.getKind()).thenReturn(ElementKind.ENUM);
        infoList = parser.parse(typeElement);
        verify(delegate2).parse(typeElement);
        assertThat(infoList).containsExactly(typeInfo);

        when(typeElement.getKind()).thenReturn(ElementKind.CONSTRUCTOR);
        infoList = parser.parse(typeElement);
        assertThat(infoList).isNull();
    }
}