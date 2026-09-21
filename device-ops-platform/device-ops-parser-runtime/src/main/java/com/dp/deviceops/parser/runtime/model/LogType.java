package com.dp.deviceops.parser.runtime.model;

import java.time.Instant;
import java.util.Objects;

public record LogType(
        String logType,
        String displayName,
        String description,
        Instant createdAt,
        Instant updatedAt) {

    public LogType {
        logType = ModelSupport.requireText(logType, "logType");
        displayName = ModelSupport.requireText(displayName, "displayName");
        description = description == null ? "" : description.strip();
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }
}
