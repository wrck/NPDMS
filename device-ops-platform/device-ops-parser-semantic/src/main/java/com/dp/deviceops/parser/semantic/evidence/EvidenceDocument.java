package com.dp.deviceops.parser.semantic.evidence;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record EvidenceDocument(List<EvidenceUnit> units, Map<String, Object> contextSnapshot) {

    public EvidenceDocument {
        units = List.copyOf(Objects.requireNonNull(units, "units"));
        contextSnapshot = contextSnapshot == null ? Map.of() : Map.copyOf(contextSnapshot);
    }

    public EvidenceDocument(List<EvidenceUnit> units) {
        this(units, Map.of());
    }
}
