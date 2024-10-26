package org.sharedtype.domain;

import lombok.Builder;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.List;

@EqualsAndHashCode(of = "qualifiedName")
@Builder
public final class ConcreteTypeInfo implements TypeInfo {
    private final String qualifiedName;
    @Builder.Default
    private final List<? extends TypeInfo> typeArgs = Collections.emptyList();
    @Builder.Default
    private boolean resolved = true;

    static ConcreteTypeInfo ofPredefined(String qualifiedName) {
        return ConcreteTypeInfo.builder().qualifiedName(qualifiedName).build();
    }

    @Override
    public boolean resolved() {
        return resolved && typeArgs.stream().allMatch(TypeInfo::resolved);
    }

    public boolean shallowResolved() {
        return resolved;
    }

    public void markShallowResolved() {
        this.resolved = true;
    }

    public String qualifiedName() {
        return qualifiedName;
    }

    public List<? extends TypeInfo> typeArgs() {
        return typeArgs;
    }

    @Override
    public String toString() {
        return String.format("%s%s%s",
                qualifiedName,
                typeArgs.isEmpty() ? "" : "<" + String.join(",", typeArgs.stream().map(TypeInfo::toString).toList()) + ">",
                resolved ? "" : "?"
        );
    }
}