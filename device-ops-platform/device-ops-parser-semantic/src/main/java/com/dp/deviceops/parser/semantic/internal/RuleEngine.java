package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.parser.semantic.BlockObservation;
import com.dp.deviceops.parser.semantic.ObservationStatus;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalog.Selector;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledRule;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledSelector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class RuleEngine {

    private static final double MINIMUM_CONFIDENCE = 0.6;
    private static final Map<String, Double> SELECTOR_SCORES = Map.of(
            "COMMAND_ALIAS", 0.95,
            "COMMAND_REGEX", 0.85,
            "CONTENT_REGEX", 0.80,
            "TABLE_HEADERS", 0.90);
    private static final Pattern DISABLED_TEXT = Pattern.compile(
            "(?i)\\b(?:not enabled|disabled|not configured|not used|process not enabled)\\b");
    private final SelectorMatcher selectorMatcher = new SelectorMatcher();

    public List<BlockObservation> observe(
            List<NormalizedEvidenceUnit> units,
            List<CompiledRule> rules,
            Set<Integer> sanitizedUnits) {
        return units.stream().map(unit -> observe(unit, rules, sanitizedUnits.contains(unit.unitIndex())))
                .toList();
    }

    private BlockObservation observe(
            NormalizedEvidenceUnit unit,
            List<CompiledRule> rules,
            boolean controlsRemoved) {
        Map<String, RoleMatch> roleMatches = new LinkedHashMap<>();
        for (CompiledRule rule : rules) {
            for (CompiledSelector selector : rule.selectors()) {
                if (selectorMatcher.matches(selector, unit)) {
                    roleMatches.computeIfAbsent(rule.source().blockRole(), ignored -> new RoleMatch())
                            .add(selector, rule);
                }
            }
        }
        List<RankedRole> ranked = roleMatches.entrySet().stream()
                .map(entry -> entry.getValue().rank(entry.getKey()))
                .sorted(Comparator.comparingDouble(RankedRole::confidence).reversed()
                        .thenComparing(RankedRole::role))
                .toList();
        RankedRole winner = ranked.isEmpty() ? null : ranked.getFirst();
        boolean ambiguous = ranked.size() > 1
                && Double.compare(ranked.get(0).confidence(), ranked.get(1).confidence()) == 0;
        boolean accepted = winner != null && winner.confidence() >= MINIMUM_CONFIDENCE && !ambiguous;
        String content = String.join("\n", unit.contentLines());
        boolean hasReplacementCharacter = content.indexOf('\uFFFD') >= 0;
        boolean hasReadableContent = content.codePoints()
                .anyMatch(codePoint -> codePoint != '\uFFFD' && !Character.isWhitespace(codePoint));
        ObservationStatus status;
        if (unit.status() != CommandBlockStatus.SUCCEEDED) {
            status = ObservationStatus.EXECUTION_FAILED;
        } else if (unit.contentLines().stream().noneMatch(line -> !line.isBlank())) {
            status = ObservationStatus.NO_DATA;
        } else if (hasReplacementCharacter && !hasReadableContent) {
            status = ObservationStatus.SOURCE_CORRUPTED;
        } else if (!accepted) {
            status = ObservationStatus.UNPARSED;
        } else if (isDisabledResponse(unit.contentLines())) {
            status = ObservationStatus.NOT_ENABLED;
        } else if (unit.truncated() || hasReplacementCharacter) {
            status = ObservationStatus.PARTIAL;
        } else {
            status = ObservationStatus.OBSERVED;
        }
        List<String> warnings = new ArrayList<>();
        if (ambiguous) {
            warnings.add("AMBIGUOUS_BLOCK_ROLE");
        }
        if (controlsRemoved) {
            warnings.add("TERMINAL_CONTROL_REMOVED");
        }
        if (hasReplacementCharacter) {
            warnings.add("SOURCE_REPLACEMENT_CHARACTER");
        }
        return new BlockObservation(
                unit.unitIndex(),
                accepted ? winner.role() : null,
                status,
                winner == null ? 0 : winner.confidence(),
                content.isBlank() ? null : 1,
                content.isBlank() ? null : unit.contentLines().size(),
                accepted ? winner.ruleIds() : List.of(),
                warnings);
    }

    private static boolean isDisabledResponse(List<String> lines) {
        List<String> meaningful = lines.stream().filter(line -> !line.isBlank()).toList();
        return meaningful.size() <= 3
                && meaningful.stream().anyMatch(line -> DISABLED_TEXT.matcher(line).find());
    }

    private static double combinedConfidence(Iterable<Double> scores) {
        double product = 1;
        for (double score : scores) {
            product *= 1 - score;
        }
        return Math.min(1, 1 - product);
    }

    private static final class RoleMatch {
        private final Map<String, Double> selectorScores = new LinkedHashMap<>();
        private final Map<String, CompiledRule> rules = new LinkedHashMap<>();

        void add(CompiledSelector compiled, CompiledRule rule) {
            Selector selector = compiled.source();
            String key = selector.type() + ':' + selector.value() + ':' + selector.values();
            selectorScores.putIfAbsent(key, SELECTOR_SCORES.get(selector.type()));
            rules.putIfAbsent(rule.source().ruleId(), rule);
        }

        RankedRole rank(String role) {
            return new RankedRole(role, combinedConfidence(selectorScores.values()),
                    List.copyOf(rules.keySet()));
        }
    }

    private record RankedRole(String role, double confidence, List<String> ruleIds) {
    }
}
