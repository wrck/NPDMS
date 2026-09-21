package com.dp.deviceops.core.model;

import com.dp.deviceops.core.port.CommandExecutionPort;

import java.time.Instant;
import java.util.Objects;

public record SavedCredential(
        String id,
        String namespace,
        String name,
        CommandExecutionPort.AuthenticationType authenticationType,
        String keyVersion,
        Instant createdAt,
        Instant updatedAt) {

    public SavedCredential {
        id = requireText(id, "id");
        namespace = requireText(namespace, "namespace");
        name = requireText(name, "name");
        authenticationType = Objects.requireNonNull(authenticationType, "authenticationType");
        keyVersion = requireText(keyVersion, "keyVersion");
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
}
