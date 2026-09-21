package com.dp.deviceops.parser.semantic;

import java.util.Objects;

/** Frozen rule and projection catalogs used by one deterministic parse. */
public record SemanticParserSpecification(
        String ruleCatalogJson,
        String projectionCatalogJson) {

    public SemanticParserSpecification {
        ruleCatalogJson = requireText(ruleCatalogJson, "ruleCatalogJson");
        projectionCatalogJson = requireText(projectionCatalogJson, "projectionCatalogJson");
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name + " must not be null").strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }
}
