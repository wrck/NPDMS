package com.dp.deviceops.parser.semantic.evidence;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record EvidenceUnit(
        int unitIndex,
        String unitType,
        Map<String, String> attributes,
        List<String> contentLines,
        List<String> errorLines,
        boolean truncated) {

    public EvidenceUnit {
        if (unitIndex < 1) {
            throw new IllegalArgumentException("unitIndex must be positive");
        }
        unitType = Objects.requireNonNull(unitType, "unitType").strip();
        if (unitType.isEmpty()) {
            throw new IllegalArgumentException("unitType is required");
        }
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        contentLines = List.copyOf(Objects.requireNonNull(contentLines, "contentLines"));
        errorLines = List.copyOf(Objects.requireNonNull(errorLines, "errorLines"));
    }
}
