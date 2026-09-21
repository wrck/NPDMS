package com.dp.deviceops.parser.semantic.release;

import com.dp.deviceops.parser.semantic.plan.ExtensionCoordinate;

import java.util.Objects;

public record ParserReleaseManifest(
        String manifestVersion,
        String logType,
        String releaseVersion,
        String inputAdapter,
        String inputSchemaVersion,
        String outputSchemaVersion,
        String engineVersion,
        String ruleVersion,
        String projectionVersion,
        ExtensionCoordinate extension) {

    public ParserReleaseManifest {
        manifestVersion = requireText(manifestVersion, "manifestVersion");
        logType = requireText(logType, "logType");
        releaseVersion = requireText(releaseVersion, "releaseVersion");
        inputAdapter = requireText(inputAdapter, "inputAdapter");
        inputSchemaVersion = requireText(inputSchemaVersion, "inputSchemaVersion");
        outputSchemaVersion = requireText(outputSchemaVersion, "outputSchemaVersion");
        engineVersion = requireText(engineVersion, "engineVersion");
        ruleVersion = requireText(ruleVersion, "ruleVersion");
        projectionVersion = requireText(projectionVersion, "projectionVersion");
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return text;
    }
}
