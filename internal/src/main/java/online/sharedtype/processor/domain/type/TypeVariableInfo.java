package online.sharedtype.processor.domain.type;

import lombok.Builder;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * Represents a generic type variable.
 * <br>
 * A type variable refers to a generic type parameter, it has a notation like "T" or bound information like "T extends Number".
 * A type argument is the actual type of the type variable. E.g. {@code "Integer" in "List<Integer>"}.
 *
 * @see ConcreteTypeInfo#typeArgs()
 * @author Cause Chung
 */
@EqualsAndHashCode(of = {"contextTypeQualifiedName", "name"}, callSuper = false)
@Builder
public final class TypeVariableInfo extends ReferableTypeInfo {
    private static final long serialVersionUID = 7632941203572660271L;
    private final String contextTypeQualifiedName; // TODO: reference to TypeDef to avoid string
    private final String name;
    private String qualifiedName;
    @Builder.Default
    private final List<TypeInfo> bounds = Collections.emptyList();

    public static String concatQualifiedName(String contextTypeQualifiedName, String name) {
        return contextTypeQualifiedName + "@" + name;
    }

    public String contextTypeQualifiedName() {
        return contextTypeQualifiedName;
    }

    public String name() {
        return name;
    }

    public List<TypeInfo> bounds() {
        return bounds;
    }

    public String qualifiedName() {
        if (qualifiedName == null) {
            qualifiedName = concatQualifiedName(contextTypeQualifiedName, name);
        }
        return qualifiedName;
    }

    @Override
    public TypeInfo reify(Map<TypeVariableInfo, TypeInfo> mappings) {
        TypeInfo reifiedType = mappings.get(this);
        return reifiedType == null ? this : reifiedType;
    }

    @Override
    public String toString() {
        return name;
    }
}
