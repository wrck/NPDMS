package com.dp.deviceops.parser.semantic;

import java.util.Objects;

public record ParserCoordinate(
        String logType,
        String releaseVersion,
        String engineVersion,
        String ruleVersion,
        String projectionVersion,
        String extensionId,
        String extensionVersion) {

    public ParserCoordinate {
        logType = requireText(logType, "logType");
        releaseVersion = requireText(releaseVersion, "releaseVersion");
        engineVersion = requireText(engineVersion, "engineVersion");
        ruleVersion = requireText(ruleVersion, "ruleVersion");
        projectionVersion = requireText(projectionVersion, "projectionVersion");
        if ((extensionId == null) != (extensionVersion == null)) {
            throw new IllegalArgumentException("extensionId and extensionVersion must be provided together");
        }
        extensionId = optionalText(extensionId, "extensionId");
        extensionVersion = optionalText(extensionVersion, "extensionVersion");
    }

    public boolean requiresExtension() {
        return extensionId != null;
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return result;
    }

    private static String optionalText(String value, String name) {
        if (value == null) {
            return null;
        }
        return requireText(value, name);
    }
}
