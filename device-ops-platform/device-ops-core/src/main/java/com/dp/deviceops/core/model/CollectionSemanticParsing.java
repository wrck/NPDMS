package com.dp.deviceops.core.model;

import java.net.URI;

/** Immutable parser selection frozen together with a collection submission. */
public record CollectionSemanticParsing(
        String logType,
        String releaseId,
        String inputFormat,
        String resultConsumerId,
        URI resultDestination) {

    public CollectionSemanticParsing {
        logType = requireText(logType, "logType");
        releaseId = requireText(releaseId, "releaseId");
        inputFormat = requireText(inputFormat, "inputFormat");
        resultConsumerId = optionalText(resultConsumerId);
        if ((resultConsumerId == null) != (resultDestination == null)) {
            throw new IllegalArgumentException("result consumer and destination must be provided together");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
