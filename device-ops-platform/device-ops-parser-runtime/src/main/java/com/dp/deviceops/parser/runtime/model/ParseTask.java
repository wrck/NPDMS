package com.dp.deviceops.parser.runtime.model;

import com.dp.deviceops.parser.semantic.ParserCoordinate;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ParseTask(
        String taskId,
        String requestId,
        String callerNamespace,
        String logType,
        String releaseId,
        ParserCoordinate coordinate,
        String inputFormat,
        String inputRef,
        Map<String, Object> contextSnapshot,
        String sourceResultId,
        String resultConsumerId,
        URI resultDestination,
        ParseTaskState state,
        ParseWaitReason waitReason,
        int attemptCount,
        Instant nextAttemptAt,
        String leaseOwner,
        long leaseGeneration,
        Instant leaseExpiresAt,
        String resultId,
        Instant createdAt,
        Instant updatedAt) {

    public ParseTask {
        taskId = ModelSupport.requireText(taskId, "taskId");
        requestId = ModelSupport.requireText(requestId, "requestId");
        callerNamespace = ModelSupport.requireText(callerNamespace, "callerNamespace");
        logType = ModelSupport.requireText(logType, "logType");
        releaseId = ModelSupport.requireText(releaseId, "releaseId");
        Objects.requireNonNull(coordinate, "coordinate");
        inputFormat = ModelSupport.requireText(inputFormat, "inputFormat");
        inputRef = ModelSupport.requireText(inputRef, "inputRef");
        contextSnapshot = contextSnapshot == null ? Map.of() : Map.copyOf(contextSnapshot);
        sourceResultId = ModelSupport.optionalText(sourceResultId, "sourceResultId");
        resultConsumerId = ModelSupport.optionalText(resultConsumerId, "resultConsumerId");
        if ((resultConsumerId == null) != (resultDestination == null)) {
            throw new IllegalArgumentException("result consumer and destination must be provided together");
        }
        Objects.requireNonNull(state, "state");
        if (!logType.equals(coordinate.logType())) {
            throw new IllegalArgumentException("task logType must match its parser coordinate");
        }
        if ((state == ParseTaskState.WAITING) != (waitReason != null)) {
            throw new IllegalArgumentException("waitReason is only valid for WAITING tasks");
        }
        if (attemptCount < 0 || leaseGeneration < 0) {
            throw new IllegalArgumentException("attemptCount and leaseGeneration must not be negative");
        }
        leaseOwner = ModelSupport.optionalText(leaseOwner, "leaseOwner");
        if (state == ParseTaskState.RUNNING
                && (leaseOwner == null || leaseGeneration < 1 || leaseExpiresAt == null)) {
            throw new IllegalArgumentException("running task requires an active lease");
        }
        resultId = ModelSupport.optionalText(resultId, "resultId");
        if ((state == ParseTaskState.SUCCEEDED) != (resultId != null)) {
            throw new IllegalArgumentException("resultId is only required for SUCCEEDED tasks");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }
}
