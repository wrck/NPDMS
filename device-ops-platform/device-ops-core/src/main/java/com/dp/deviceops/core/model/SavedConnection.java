package com.dp.deviceops.core.model;

import com.dp.deviceops.core.port.CommandExecutionPort.ConnectionSpec;

import java.time.Instant;
import java.util.Objects;

/** Metadata for a persisted connection whose credential remains opaque. */
public record SavedConnection(
        String id,
        String namespace,
        String displayName,
        String description,
        ConnectionSpec connection,
        boolean credentialSaved,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public SavedConnection {
        id = requireText(id, "id");
        namespace = requireText(namespace, "namespace");
        displayName = requireText(displayName, "displayName");
        description = normalize(description);
        connection = Objects.requireNonNull(connection, "connection");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip();
    }
}
