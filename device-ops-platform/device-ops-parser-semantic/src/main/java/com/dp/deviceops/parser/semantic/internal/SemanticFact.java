package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.ObservationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

public record SemanticFact(
        String semanticKey,
        Object value,
        String dataType,
        String unit,
        ObservationStatus status,
        double confidence,
        String cardinality,
        String conflictPolicy,
        SourceEvidence source,
        String ruleId) {

    public record SourceEvidence(
            int commandIndex,
            String commandText,
            int lineStart,
            int lineEnd,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer nestingDepth,
            @JsonInclude(JsonInclude.Include.NON_NULL) String nestedCommandText,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer sectionIndex) {

        public SourceEvidence(int commandIndex, String commandText, int lineStart, int lineEnd) {
            this(commandIndex, commandText, lineStart, lineEnd, null, null, null);
        }

        public int nestingDepthOrZero() {
            return nestingDepth == null ? 0 : nestingDepth;
        }
    }
}
