package com.dp.deviceops.parser.runtime.model;

import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.SemanticParseResult;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ParseResultEnvelope(
        String resultId,
        String taskId,
        String releaseId,
        ParserCoordinate coordinate,
        Map<String, Object> contextSnapshot,
        String sourceResultId,
        SemanticParseResult semanticResult,
        Instant createdAt) {

    public ParseResultEnvelope {
        resultId = ModelSupport.requireText(resultId, "resultId");
        taskId = ModelSupport.requireText(taskId, "taskId");
        releaseId = ModelSupport.requireText(releaseId, "releaseId");
        Objects.requireNonNull(coordinate, "coordinate");
        contextSnapshot = contextSnapshot == null ? Map.of() : Map.copyOf(contextSnapshot);
        sourceResultId = ModelSupport.optionalText(sourceResultId, "sourceResultId");
        Objects.requireNonNull(semanticResult, "semanticResult");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
