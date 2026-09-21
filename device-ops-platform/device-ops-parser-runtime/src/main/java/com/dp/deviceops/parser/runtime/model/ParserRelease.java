package com.dp.deviceops.parser.runtime.model;

import com.dp.deviceops.parser.semantic.ParserCoordinate;

import java.time.Instant;
import java.util.Objects;

public record ParserRelease(
        String releaseId,
        String logType,
        String releaseVersion,
        ReleaseState state,
        ParserCoordinate coordinate,
        long draftRevision,
        ParserReleaseValidation validation,
        Instant createdAt,
        Instant publishedAt) {

    public ParserRelease {
        releaseId = ModelSupport.requireText(releaseId, "releaseId");
        logType = ModelSupport.requireText(logType, "logType");
        releaseVersion = ModelSupport.requireText(releaseVersion, "releaseVersion");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(coordinate, "coordinate");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!logType.equals(coordinate.logType()) || !releaseVersion.equals(coordinate.releaseVersion())) {
            throw new IllegalArgumentException("release coordinate must match log type and release version");
        }
        if (draftRevision < 1) {
            throw new IllegalArgumentException("draftRevision must be positive");
        }
        if (validation != null && (!releaseId.equals(validation.releaseId())
                || draftRevision != validation.draftRevision())) {
            throw new IllegalArgumentException("validation must match the release revision");
        }
        if (state == ReleaseState.DRAFT && publishedAt != null) {
            throw new IllegalArgumentException("draft release must not have publishedAt");
        }
        if (state != ReleaseState.DRAFT && (publishedAt == null || validation == null || !validation.passed())) {
            throw new IllegalArgumentException("published or disabled release requires passed validation");
        }
    }
}
