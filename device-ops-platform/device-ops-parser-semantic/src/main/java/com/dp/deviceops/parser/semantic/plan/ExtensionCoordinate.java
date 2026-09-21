package com.dp.deviceops.parser.semantic.plan;

import java.util.Objects;

public record ExtensionCoordinate(String extensionId, String extensionVersion) {
    public ExtensionCoordinate {
        extensionId = requireText(extensionId, "extensionId");
        extensionVersion = requireText(extensionVersion, "extensionVersion");
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return result;
    }
}
