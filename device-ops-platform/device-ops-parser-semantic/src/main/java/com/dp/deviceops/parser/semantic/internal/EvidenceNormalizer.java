package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.evidence.EvidenceDocument;
import com.dp.deviceops.parser.semantic.evidence.EvidenceUnit;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class EvidenceNormalizer {

    public List<NormalizedEvidenceUnit> normalize(EvidenceDocument document) {
        if (document == null) {
            throw invalid("evidence document must not be null");
        }
        List<EvidenceUnit> sorted;
        try {
            sorted = document.units().stream()
                    .map(unit -> Objects.requireNonNull(unit, "evidence unit"))
                    .sorted(Comparator.comparingInt(EvidenceUnit::unitIndex))
                    .toList();
        } catch (NullPointerException exception) {
            throw new SemanticParserError(SemanticParserError.INVALID_INPUT,
                    "evidence units must not contain null", exception);
        }
        Set<Integer> indexes = new HashSet<>();
        return sorted.stream().map(unit -> {
            if (!indexes.add(unit.unitIndex())) {
                throw invalid("unitIndex must be unique");
            }
            Map<String, String> attributes = new LinkedHashMap<>();
            unit.attributes().forEach((key, value) -> attributes.put(
                    Objects.requireNonNull(key, "attribute key"), normalizeLineEndings(value)));
            return new NormalizedEvidenceUnit(unit.unitIndex(), unit.unitType(), attributes,
                    normalizeLines(unit.contentLines()), normalizeLines(unit.errorLines()), unit.truncated());
        }).toList();
    }

    private static List<String> normalizeLines(List<String> lines) {
        return lines.stream()
                .map(line -> normalizeLineEndings(Objects.requireNonNull(line, "evidence line")))
                .flatMap(line -> List.of(line.split("\\n", -1)).stream())
                .toList();
    }

    private static String normalizeLineEndings(String text) {
        return Objects.requireNonNull(text, "text").replace("\r\n", "\n").replace('\r', '\n');
    }

    private static SemanticParserError invalid(String message) {
        return new SemanticParserError(SemanticParserError.INVALID_INPUT, message);
    }
}
