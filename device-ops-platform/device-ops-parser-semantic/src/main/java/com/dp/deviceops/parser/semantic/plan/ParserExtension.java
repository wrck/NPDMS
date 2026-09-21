package com.dp.deviceops.parser.semantic.plan;

import com.dp.deviceops.parser.semantic.ObservationStatus;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.evidence.EvidenceDocument;

import java.util.List;
import java.util.Objects;

public interface ParserExtension {
    String extensionId();

    String extensionVersion();

    ExtensionResult extract(EvidenceDocument document, ExtensionContext context);

    record ExtensionContext(ParserCoordinate coordinate) {
        public ExtensionContext {
            Objects.requireNonNull(coordinate, "coordinate");
        }
    }

    record ExtensionEvidence(int unitIndex, String commandText, int lineStart, int lineEnd) {
        public ExtensionEvidence {
            if (unitIndex < 1 || lineStart < 1 || lineEnd < lineStart) {
                throw new IllegalArgumentException("extension evidence range is invalid");
            }
            commandText = commandText == null ? "" : commandText;
        }
    }

    record ExtensionFact(
            String semanticKey,
            Object value,
            String dataType,
            String unit,
            ObservationStatus status,
            double confidence,
            String cardinality,
            String conflictPolicy,
            ExtensionEvidence source,
            String ruleId) {
        public ExtensionFact {
            Objects.requireNonNull(semanticKey, "semanticKey");
            Objects.requireNonNull(dataType, "dataType");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(cardinality, "cardinality");
            Objects.requireNonNull(conflictPolicy, "conflictPolicy");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(ruleId, "ruleId");
            if (confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException("confidence must be between 0 and 1");
            }
            if (!List.of("ONE", "MANY").contains(cardinality)) {
                throw new IllegalArgumentException("extension cardinality is invalid");
            }
            if (!List.of("HIGHEST_CONFIDENCE", "APPEND_DISTINCT").contains(conflictPolicy)) {
                throw new IllegalArgumentException("extension conflictPolicy is invalid");
            }
        }
    }

    record ExtensionWarning(String ruleId, String code, int lineNumber) {
        public ExtensionWarning {
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(code, "code");
            if (lineNumber < 1) {
                throw new IllegalArgumentException("lineNumber must be positive");
            }
        }
    }

    record ExtensionResult(List<ExtensionFact> facts, List<ExtensionWarning> warnings) {
        public ExtensionResult {
            facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        }

        public static ExtensionResult empty() {
            return new ExtensionResult(List.of(), List.of());
        }
    }
}
