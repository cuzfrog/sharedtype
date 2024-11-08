package online.sharedtype.processor.domain;

import lombok.Builder;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents an enum.
 *
 * @author Cause Chung
 */
@EqualsAndHashCode(of = "qualifiedName")
@Builder
public final class EnumDef implements TypeDef {
    private static final long serialVersionUID = 9158463705652816935L;
    private final String qualifiedName;
    private final String simpleName;
    @Builder.Default
    private final List<EnumValueInfo> enumValueInfos = Collections.emptyList();

    @Override
    public String qualifiedName() {
        return qualifiedName;
    }

    @Override
    public String simpleName() {
        return simpleName;
    }

    @Override
    public List<EnumValueInfo> components() {
        return enumValueInfos;
    }

    @Override
    public boolean resolved() {
        return enumValueInfos.stream().allMatch(EnumValueInfo::resolved);
    }

    @Override
    public String toString() {
        return String.format("%s[%s]",
            qualifiedName,
            enumValueInfos.isEmpty() ? "" : String.join(",", enumValueInfos.stream().map(EnumValueInfo::toString).collect(Collectors.toList()))
        );
    }
}
