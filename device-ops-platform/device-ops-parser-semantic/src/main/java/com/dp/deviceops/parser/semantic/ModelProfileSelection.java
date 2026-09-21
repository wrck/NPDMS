package com.dp.deviceops.parser.semantic;

import java.util.List;
import java.util.Objects;

/** Explains the deterministic model profile selected for one parse result. */
public record ModelProfileSelection(
        String profileId,
        String normalizedModel,
        Source source,
        Integer commandIndex,
        Integer lineNumber,
        List<String> warnings) {

    public ModelProfileSelection {
        profileId = requireText(profileId, "profileId");
        normalizedModel = normalizeOptional(normalizedModel);
        Objects.requireNonNull(source, "source");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        if (source == Source.GENERIC && normalizedModel != null) {
            throw new IllegalArgumentException("generic source must not carry a model");
        }
        if (source == Source.LOG_OUTPUT && (commandIndex == null || lineNumber == null)) {
            throw new IllegalArgumentException("log output source requires evidence location");
        }
    }

    public enum Source {
        LOG_OUTPUT,
        CONTEXT_SNAPSHOT,
        GENERIC
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
