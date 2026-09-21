package com.dp.deviceops.parser.semantic.plan;

import com.dp.deviceops.parser.semantic.SemanticParserError;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

public final class ParserExtensionRegistry {

    private final Map<ExtensionCoordinate, ParserExtension> extensions;

    public ParserExtensionRegistry() {
        this(ServiceLoader.load(ParserExtension.class).stream().map(ServiceLoader.Provider::get).toList());
    }

    public ParserExtensionRegistry(Collection<ParserExtension> extensions) {
        Map<ExtensionCoordinate, ParserExtension> indexed = new LinkedHashMap<>();
        for (ParserExtension extension : extensions) {
            ExtensionCoordinate coordinate = new ExtensionCoordinate(
                    extension.extensionId(), extension.extensionVersion());
            if (indexed.putIfAbsent(coordinate, extension) != null) {
                throw new SemanticParserError(SemanticParserError.INVALID_RULES,
                        "parser extension coordinate must be unique");
            }
        }
        this.extensions = Map.copyOf(indexed);
    }

    public Optional<ParserExtension> find(ExtensionCoordinate coordinate) {
        return Optional.ofNullable(extensions.get(coordinate));
    }
}
