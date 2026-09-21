package com.dp.deviceops.core.model;

import com.dp.deviceops.core.port.CommandExecutionPort.ConnectionSpec;

import java.util.Objects;

/** Validated mutable fields for creating or replacing a saved connection. */
public record SavedConnectionDraft(
        String displayName,
        String description,
        ConnectionSpec connection) {

    public SavedConnectionDraft {
        displayName = requireText(displayName, "displayName");
        description = description == null ? null : description.strip();
        connection = Objects.requireNonNull(connection, "connection");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }
}
