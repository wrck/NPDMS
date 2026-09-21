package com.dp.deviceops.parser.semantic;

import java.util.List;
import java.util.Objects;

public record NestedBlockObservation(
        int parentCommandIndex,
        int sectionIndex,
        int nestingDepth,
        String commandText,
        String blockRole,
        ObservationStatus status,
        double confidence,
        int sourceLineStart,
        int sourceLineEnd,
        List<String> matchedRuleIds,
        List<String> warnings) {

    public NestedBlockObservation {
        commandText = Objects.requireNonNull(commandText, "commandText");
        blockRole = blockRole == null ? null : blockRole.strip();
        status = Objects.requireNonNull(status, "status");
        matchedRuleIds = List.copyOf(Objects.requireNonNull(matchedRuleIds, "matchedRuleIds"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }
}
