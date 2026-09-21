package com.dp.deviceops.parser.semantic;

import java.util.List;
import java.util.Objects;

/** Business interpretation and traceable evidence for one command block. */
public record BlockObservation(
        int commandIndex,
        String blockRole,
        ObservationStatus status,
        double confidence,
        Integer sourceLineStart,
        Integer sourceLineEnd,
        List<String> matchedRuleIds,
        List<String> warnings) {

    public BlockObservation {
        if (commandIndex < 1) {
            throw new IllegalArgumentException("commandIndex must be positive");
        }
        blockRole = blockRole == null ? null : blockRole.strip();
        status = Objects.requireNonNull(status, "status");
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        matchedRuleIds = List.copyOf(Objects.requireNonNull(matchedRuleIds, "matchedRuleIds"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }
}
