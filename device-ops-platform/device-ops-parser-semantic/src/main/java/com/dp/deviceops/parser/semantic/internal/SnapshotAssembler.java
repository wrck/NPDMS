package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.BlockObservation;
import com.dp.deviceops.parser.semantic.ObservationStatus;
import com.dp.deviceops.parser.semantic.SemanticParserError;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SnapshotAssembler {

    private static final Set<String> FORBIDDEN_SEGMENTS = Set.of("__proto__", "prototype", "constructor");
    private static final Comparator<SemanticFact> FACT_ORDER = Comparator
            .comparingInt((SemanticFact fact) -> fact.source().nestingDepthOrZero())
            .thenComparing(Comparator.comparingDouble(SemanticFact::confidence).reversed())
            .thenComparingInt(fact -> fact.source().commandIndex())
            .thenComparingInt(fact -> fact.source().lineStart())
            .thenComparing(SemanticFact::ruleId);
    private static final Comparator<SemanticFact> MANY_FACT_ORDER = Comparator
            .comparingInt((SemanticFact fact) -> fact.source().nestingDepthOrZero())
            .thenComparingInt(fact -> fact.source().commandIndex())
            .thenComparingInt(fact -> fact.source().sectionIndex() == null
                    ? 0 : fact.source().sectionIndex())
            .thenComparingInt(fact -> fact.source().lineStart())
            .thenComparing(SemanticFact::ruleId);

    public Map<String, Object> assemble(
            List<BlockObservation> observations,
            FactExtractor.ExtractionResult extraction) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", "1.0.0");
        Map<String, Object> entities = initialEntities();
        snapshot.put("entities", entities);

        List<Map<String, Object>> conflicts = new ArrayList<>();
        Map<String, List<SemanticFact>> grouped = extraction.facts().stream()
                .collect(Collectors.groupingBy(SemanticFact::semanticKey,
                        LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<SemanticFact>> entry : grouped.entrySet()) {
            List<SemanticFact> ordered = entry.getValue().stream().sorted(FACT_ORDER).toList();
            if ("MANY".equals(ordered.getFirst().cardinality())) {
                List<Map<String, Object>> distinct = distinctFacts(ordered);
                assign(entities, entry.getKey(), distinct);
            } else {
                SemanticFact winner = ordered.getFirst();
                assign(entities, entry.getKey(), factDocument(winner));
                for (SemanticFact discarded : ordered.subList(1, ordered.size())) {
                    conflicts.add(Map.of(
                            "semanticKey", entry.getKey(),
                            "policy", winner.conflictPolicy(),
                            "winnerRuleId", winner.ruleId(),
                            "discardedRuleId", discarded.ruleId(),
                            "winnerSource", winner.source(),
                            "discardedSource", discarded.source()));
                }
            }
        }
        List<Map<String, Object>> unmapped = observations.stream()
                .filter(observation -> observation.status() == ObservationStatus.UNPARSED)
                .map(observation -> Map.<String, Object>of(
                        "commandIndex", observation.commandIndex(),
                        "sourceLineStart", observation.sourceLineStart() == null ? 0 : observation.sourceLineStart(),
                        "sourceLineEnd", observation.sourceLineEnd() == null ? 0 : observation.sourceLineEnd()))
                .toList();
        snapshot.put("unmapped", unmapped);
        snapshot.put("quality", Map.of(
                "factCount", extraction.facts().size(),
                "mappedFactCount", extraction.facts().size(),
                "conflicts", conflicts,
                "warnings", extraction.warnings()));
        return snapshot;
    }

    private static Map<String, Object> initialEntities() {
        Map<String, Object> device = new LinkedHashMap<>();
        for (String section : List.of("identity", "software", "hardware", "environment", "performance",
                "management", "highAvailability")) {
            device.put(section, new LinkedHashMap<>());
        }
        Map<String, Object> entities = new LinkedHashMap<>();
        entities.put("device", device);
        entities.put("interfaces", new ArrayList<>());
        entities.put("addresses", new LinkedHashMap<>());
        entities.put("routing", new LinkedHashMap<>());
        entities.put("sessions", new LinkedHashMap<>());
        entities.put("security", new LinkedHashMap<>());
        entities.put("logs", new LinkedHashMap<>());
        entities.put("configuration", new LinkedHashMap<>());
        return entities;
    }

    private List<Map<String, Object>> distinctFacts(List<SemanticFact> facts) {
        Map<String, Map<String, Object>> distinct = new LinkedHashMap<>();
        CanonicalJson json = new CanonicalJson();
        for (SemanticFact fact : facts.stream().sorted(MANY_FACT_ORDER).toList()) {
            distinct.putIfAbsent(json.sha256(fact.value()), factDocument(fact));
        }
        return List.copyOf(distinct.values());
    }

    private static Map<String, Object> factDocument(SemanticFact fact) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("semanticKey", fact.semanticKey());
        document.put("value", fact.value());
        document.put("dataType", fact.dataType());
        document.put("unit", fact.unit());
        document.put("status", fact.status().name());
        document.put("confidence", fact.confidence());
        document.put("source", fact.source());
        document.put("ruleId", fact.ruleId());
        return document;
    }

    @SuppressWarnings("unchecked")
    private static void assign(Map<String, Object> entities, String semanticKey, Object value) {
        String[] segments = semanticKey.split("\\.", -1);
        if (segments.length == 0 || "collection".equals(segments[0])) {
            throw new SemanticParserError(SemanticParserError.INVALID_RULES,
                    "collection semantic paths are not supported by the standalone parser");
        }
        Map<String, Object> cursor = entities;
        for (int index = 0; index < segments.length - 1; index++) {
            String segment = safeSegment(segments[index]);
            Object existing = cursor.get(segment);
            if (existing == null) {
                Map<String, Object> nested = new LinkedHashMap<>();
                cursor.put(segment, nested);
                cursor = nested;
            } else if (existing instanceof Map<?, ?> nested) {
                cursor = (Map<String, Object>) nested;
            } else {
                throw new SemanticParserError(SemanticParserError.INVALID_RULES,
                        "semantic path collides with a scalar");
            }
        }
        String leaf = safeSegment(segments[segments.length - 1]);
        if (cursor.containsKey(leaf)) {
            Object existing = cursor.get(leaf);
            if (existing instanceof List<?> list && list.isEmpty() && value instanceof List<?>) {
                cursor.put(leaf, value);
                return;
            }
            throw new SemanticParserError(SemanticParserError.INVALID_RULES,
                    "semantic path cardinality collides");
        }
        cursor.put(leaf, value);
    }

    private static String safeSegment(String segment) {
        if (segment.isBlank() || FORBIDDEN_SEGMENTS.contains(segment)) {
            throw new SemanticParserError(SemanticParserError.INVALID_RULES, "semantic path is unsafe");
        }
        return segment;
    }
}
