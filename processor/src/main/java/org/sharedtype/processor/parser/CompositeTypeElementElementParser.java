package org.sharedtype.processor.parser;

import lombok.RequiredArgsConstructor;
import org.sharedtype.processor.context.Context;
import org.sharedtype.processor.domain.TypeDef;
import org.sharedtype.processor.support.exception.SharedTypeInternalError;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;

@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
final class CompositeTypeElementElementParser implements TypeElementParser {
    private final Context ctx;
    private final Map<ElementKind, TypeElementParser> parsers;

    @Override
    public List<TypeDef> parse(TypeElement typeElement) {
        ctx.info("Processing: " + typeElement.getQualifiedName());
        var parser = parsers.get(typeElement.getKind());
        if (parser == null) {
            throw new SharedTypeInternalError(String.format("Unsupported element: %s, kind=%s", typeElement, typeElement.getKind()));
        }
        return parser.parse(typeElement);
    }
}