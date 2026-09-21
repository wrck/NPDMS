package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.BlockObservation;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledExtractor;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class NestedEvidenceExpander {

    private final SemanticLimits limits;
    private final DelimitedSectionParser sectionParser;

    public NestedEvidenceExpander(SemanticLimits limits) {
        this(limits, false);
    }

    public NestedEvidenceExpander(SemanticLimits limits, boolean enhanced) {
        this.limits = limits;
        this.sectionParser = new DelimitedSectionParser(enhanced);
    }

    public Expansion expand(
            List<NormalizedEvidenceUnit> topLevelUnits,
            List<BlockObservation> observations,
            List<CompiledRule> rules) {
        return expand(topLevelUnits, observations, rules, false);
    }

    public Expansion expand(
            List<NormalizedEvidenceUnit> topLevelUnits,
            List<BlockObservation> observations,
            List<CompiledRule> rules,
            boolean includeGenericSeeds) {
        Map<Integer, NormalizedEvidenceUnit> unitsByIndex = topLevelUnits.stream()
                .collect(Collectors.toMap(NormalizedEvidenceUnit::unitIndex, Function.identity()));
        Map<String, CompiledRule> rulesById = rules.stream().collect(Collectors.toMap(
                rule -> rule.source().ruleId(), Function.identity(), (first, ignored) -> first,
                LinkedHashMap::new));
        int nextUnitIndex = topLevelUnits.stream()
                .mapToInt(NormalizedEvidenceUnit::unitIndex).max().orElse(0) + 1;
        List<NormalizedEvidenceUnit> expanded = new ArrayList<>();
        Map<Integer, NestedIdentity> identities = new LinkedHashMap<>();
        List<SectionSeed> sectionSeeds = new ArrayList<>();
        for (BlockObservation observation : observations) {
            NormalizedEvidenceUnit parent = unitsByIndex.get(observation.commandIndex());
            if (parent == null || parent.provenance().nestingDepth() >= 1) {
                continue;
            }
            for (String ruleId : observation.matchedRuleIds()) {
                CompiledRule rule = rulesById.get(ruleId);
                if (rule == null) {
                    continue;
                }
                for (CompiledExtractor extractor : rule.extractors()) {
                    if (!extractor.source().emitNestedUnits()) {
                        continue;
                    }
                    for (DelimitedSectionParser.Section section : sectionParser.parse(parent, extractor)) {
                        if (includeGenericSeeds && isValidSection(section)) {
                            sectionSeeds.add(new SectionSeed(
                                    parent.provenance().commandIndex(), section.sectionIndex(),
                                    section.commandText(),
                                    parent.provenance().absoluteLine(section.lineStart()),
                                    parent.provenance().absoluteLine(section.lineEnd()),
                                    section.lines(), parent.truncated()));
                        }
                        boolean expandable = includeGenericSeeds
                                ? isValidSection(section) && hasBody(section)
                                : isLegacyExpandable(section);
                        if (!expandable) {
                            continue;
                        }
                        if (topLevelUnits.size() + expanded.size() + 1 > limits.maxCommandBlocks()) {
                            throw new SemanticParserError(SemanticParserError.RESOURCE_LIMIT,
                                    "command block count exceeds limit");
                        }
                        int syntheticIndex = nextUnitIndex++;
                        EvidenceProvenance provenance = new EvidenceProvenance(
                                parent.provenance().commandIndex(),
                                parent.provenance().commandText(),
                                1,
                                section.commandText(),
                                section.sectionIndex(),
                                parent.provenance().absoluteLine(section.lineStart()) - 1);
                        Map<String, String> attributes = Map.of(
                                "commandText", section.commandText(),
                                "status", parent.status().name());
                        expanded.add(new NormalizedEvidenceUnit(syntheticIndex, "COMMAND_OUTPUT",
                                attributes, section.lines(), List.of(), parent.truncated(), provenance));
                        identities.put(syntheticIndex, new NestedIdentity(syntheticIndex,
                                parent.provenance().commandIndex(), section.sectionIndex(),
                                provenance.absoluteLine(1), provenance.absoluteLine(section.lines().size())));
                    }
                }
            }
        }
        return new Expansion(List.copyOf(expanded),
                Collections.unmodifiableMap(new LinkedHashMap<>(identities)),
                List.copyOf(sectionSeeds));
    }

    private static boolean isValidSection(DelimitedSectionParser.Section section) {
        return section.sectionIndex() > 0
                && section.commandText().codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private static boolean hasBody(DelimitedSectionParser.Section section) {
        return section.lines().stream().skip(1).anyMatch(line -> !line.isBlank());
    }

    private static boolean isLegacyExpandable(DelimitedSectionParser.Section section) {
        return section.sectionIndex() > 0 && !section.commandText().isBlank() && hasBody(section);
    }

    public record Expansion(
            List<NormalizedEvidenceUnit> units,
            Map<Integer, NestedIdentity> identities,
            List<SectionSeed> sectionSeeds) {

        public Expansion(List<NormalizedEvidenceUnit> units, Map<Integer, NestedIdentity> identities) {
            this(units, identities, List.of());
        }
    }

    public record SectionSeed(
            int parentCommandIndex,
            int sectionIndex,
            String commandText,
            int lineStart,
            int lineEnd,
            List<String> lines,
            boolean truncated) {
    }

    public record NestedIdentity(
            int syntheticUnitIndex,
            int parentCommandIndex,
            int sectionIndex,
            int lineStart,
            int lineEnd) {
    }
}
