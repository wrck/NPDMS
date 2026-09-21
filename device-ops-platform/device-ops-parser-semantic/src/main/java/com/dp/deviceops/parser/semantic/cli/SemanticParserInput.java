package com.dp.deviceops.parser.semantic.cli;

import java.util.List;
import java.util.Map;

public record SemanticParserInput(
        String schemaVersion,
        String collectionId,
        Map<String, Object> contextSnapshot,
        List<CommandOutputBlockInput> commandBlocks) {

    public SemanticParserInput {
        contextSnapshot = contextSnapshot == null ? Map.of() : Map.copyOf(contextSnapshot);
    }

    public SemanticParserInput(
            String schemaVersion,
            String collectionId,
            List<CommandOutputBlockInput> commandBlocks) {
        this(schemaVersion, collectionId, Map.of(), commandBlocks);
    }
}
