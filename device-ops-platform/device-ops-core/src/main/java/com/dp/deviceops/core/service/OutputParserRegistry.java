package com.dp.deviceops.core.service;

import com.dp.deviceops.core.port.OutputParser;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves parser implementations by a case-insensitive, uniquely registered type.
 */
public final class OutputParserRegistry {

    private final Map<String, OutputParser> parsersByType;

    public OutputParserRegistry(Collection<? extends OutputParser> parsers) {
        Objects.requireNonNull(parsers, "parsers must not be null");
        Map<String, OutputParser> registered = new LinkedHashMap<>();
        for (OutputParser parser : parsers) {
            OutputParser requiredParser = Objects.requireNonNull(parser, "parser must not be null");
            String type = normalizeType(requiredParser.type());
            if (registered.putIfAbsent(type, requiredParser) != null) {
                throw new IllegalArgumentException("parser type is already registered: " + requiredParser.type());
            }
        }
        parsersByType = Map.copyOf(registered);
    }

    public Optional<OutputParser> find(String type) {
        return Optional.ofNullable(parsersByType.get(normalizeType(type)));
    }

    private static String normalizeType(String type) {
        Objects.requireNonNull(type, "parser type must not be null");
        if (type.isBlank()) {
            throw new IllegalArgumentException("parser type must not be blank");
        }
        return type.toLowerCase(Locale.ROOT);
    }
}
