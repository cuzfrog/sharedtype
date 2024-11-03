package org.sharedtype.it;

import org.junit.jupiter.api.Test;
import org.sharedtype.domain.ConcreteTypeInfo;
import org.sharedtype.domain.EnumDef;
import org.sharedtype.domain.EnumValueInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sharedtype.it.TypeDefDeserializer.deserializeTypeDef;

final class EnumValueIntegrationTest {
    private final EnumDef enumSize = (EnumDef) deserializeTypeDef("org.sharedtype.it.java8.EnumSize.ser");

    @Test
    void enumSize() {
        assertThat(enumSize.simpleName()).isEqualTo("EnumSize");
        assertThat(enumSize.qualifiedName()).isEqualTo("org.sharedtype.it.java8.EnumSize");
        assertThat(enumSize.components()).hasSize(3).allMatch(constant -> {
            ConcreteTypeInfo typeInfo = (ConcreteTypeInfo)constant.type();
            return typeInfo.qualifiedName().equals("int");
        });

        EnumValueInfo constant1 = enumSize.components().get(0);
        assertThat(constant1.value()).isEqualTo(1);

        EnumValueInfo constant2 = enumSize.components().get(1);
        assertThat(constant2.value()).isEqualTo(2);

        EnumValueInfo constant3 = enumSize.components().get(2);
        assertThat(constant3.value()).isEqualTo(3);
    }
}
