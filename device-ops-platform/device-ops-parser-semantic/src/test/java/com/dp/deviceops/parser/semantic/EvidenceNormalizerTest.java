package com.dp.deviceops.parser.semantic;

import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.parser.semantic.evidence.EvidenceDocument;
import com.dp.deviceops.parser.semantic.evidence.EvidenceUnit;
import com.dp.deviceops.parser.semantic.input.CommandOutputBlockEvidenceAdapter;
import com.dp.deviceops.parser.semantic.internal.EvidenceNormalizer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidenceNormalizerTest {

    private final EvidenceNormalizer normalizer = new EvidenceNormalizer();

    @Test
    void normalizesLineEndingsAndOrdering() {
        EvidenceDocument document = new EvidenceDocument(List.of(
                unit(2, "second"), unit(1, "line1\r\nline2")));

        var normalized = normalizer.normalize(document);

        assertEquals(List.of(1, 2), normalized.stream().map(item -> item.unitIndex()).toList());
        assertEquals(List.of("line1", "line2"), normalized.getFirst().contentLines());
    }

    @Test
    void rejectsDuplicateUnitIndexes() {
        SemanticParserError error = assertThrows(SemanticParserError.class,
                () -> normalizer.normalize(new EvidenceDocument(List.of(unit(1, "one"), unit(1, "two")))));

        assertEquals(SemanticParserError.INVALID_INPUT, error.code());
    }

    @Test
    void commandAdapterExcludesRuntimeOnlyFields() {
        CommandOutputBlock first = block(Map.of("old", "value"), Instant.EPOCH);
        CommandOutputBlock sameEvidence = block(Map.of("other", "fact"), Instant.now());
        CommandOutputBlockEvidenceAdapter adapter = new CommandOutputBlockEvidenceAdapter();

        assertEquals(normalizer.normalize(adapter.fromBlocks(List.of(first))),
                normalizer.normalize(adapter.fromBlocks(List.of(sameEvidence))));
    }

    private static EvidenceUnit unit(int index, String content) {
        return new EvidenceUnit(index, "SECTION", Map.of(), List.of(content), List.of(), false);
    }

    private static CommandOutputBlock block(Map<String, String> parsedFacts, Instant completedAt) {
        return new CommandOutputBlock(1, "show synthetic", CommandBlockStatus.SUCCEEDED,
                "line1\r\nline2", "", 12, 1, false, 0, "complete", parsedFacts,
                List.of("runtime warning"), Instant.EPOCH, completedAt, false);
    }
}
