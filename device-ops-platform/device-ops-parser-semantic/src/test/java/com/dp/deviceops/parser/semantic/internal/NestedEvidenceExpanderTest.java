package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.BlockObservation;
import com.dp.deviceops.parser.semantic.ObservationStatus;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalog.Extractor;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalog.Rule;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalog.Selector;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalog.Target;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledExtractor;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledRule;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledSelector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NestedEvidenceExpanderTest {

    @Test
    void preservesValidEmptySectionSeedsWithoutCreatingObservationUnits() {
        NormalizedEvidenceUnit aggregate = new NormalizedEvidenceUnit(1, "COMMAND_OUTPUT",
                Map.of("commandText", "show bundle"), List.of(
                        "--- populated ---", "value",
                        "--- empty command ---",
                        "--- *** ---"), List.of(), false);
        CompiledRule rule = rule();
        BlockObservation observation = new BlockObservation(1, "AGGREGATE",
                ObservationStatus.OBSERVED, 0.9, 1, 4, List.of("sections"), List.of());

        NestedEvidenceExpander.Expansion expansion = new NestedEvidenceExpander(SemanticLimits.defaults())
                .expand(List.of(aggregate), List.of(observation), List.of(rule), true);

        assertEquals(List.of("populated"), expansion.units().stream()
                .map(NormalizedEvidenceUnit::commandText).toList());
        assertEquals(List.of("populated", "empty command"), expansion.sectionSeeds().stream()
                .map(NestedEvidenceExpander.SectionSeed::commandText).toList());
        assertEquals(List.of(1, 2), expansion.sectionSeeds().stream()
                .map(NestedEvidenceExpander.SectionSeed::sectionIndex).toList());
    }

    @Test
    void oldConstructorsAndExplicitFalseRetainLegacyDecorationBoundaries() {
        NormalizedEvidenceUnit aggregate = new NormalizedEvidenceUnit(1, "COMMAND_OUTPUT",
                Map.of("commandText", "show bundle"), List.of(
                        "--- show session statistic ---", "", "--- *** ---", "Total: 42",
                        "--- show empty ---"), List.of(), false);
        BlockObservation observation = new BlockObservation(1, "AGGREGATE",
                ObservationStatus.OBSERVED, 0.9, 1, 5, List.of("sections"), List.of());
        var extractor = rule().extractors().getFirst();

        var sections = new DelimitedSectionParser().parse(aggregate, extractor);
        assertEquals(sections, new DelimitedSectionParser(false).parse(aggregate, extractor));
        assertEquals(List.of("show session statistic", "***", "show empty"), sections.stream()
                .map(DelimitedSectionParser.Section::commandText).toList());
        assertEquals(2, sections.getFirst().lineEnd());

        var legacy = new NestedEvidenceExpander(SemanticLimits.defaults())
                .expand(List.of(aggregate), List.of(observation), List.of(rule()), true);
        var explicitFalse = new NestedEvidenceExpander(SemanticLimits.defaults(), false)
                .expand(List.of(aggregate), List.of(observation), List.of(rule()), true);
        assertEquals(legacy, explicitFalse);
        assertEquals(List.of(), legacy.units());
        assertEquals(List.of(1, 3), legacy.sectionSeeds().stream()
                .map(NestedEvidenceExpander.SectionSeed::sectionIndex).toList());
    }

    @Test
    void enhancedExpansionKeepsStatisticsAfterDecorationInTheirOriginalSection() {
        NormalizedEvidenceUnit aggregate = new NormalizedEvidenceUnit(1, "COMMAND_OUTPUT",
                Map.of("commandText", "show bundle"), List.of(
                        "--- show session statistic ---", "", "--- *** ---",
                        "Total: 42", "  IPv4: 40", "  IPv6: 2", "--- show empty ---"), List.of(), false);
        BlockObservation observation = new BlockObservation(1, "AGGREGATE",
                ObservationStatus.OBSERVED, 0.9, 1, 7, List.of("sections"), List.of());

        var expansion = new NestedEvidenceExpander(SemanticLimits.defaults(), true)
                .expand(List.of(aggregate), List.of(observation), List.of(rule()), true);

        assertEquals(List.of("show session statistic"), expansion.units().stream()
                .map(NormalizedEvidenceUnit::commandText).toList());
        assertEquals(aggregate.contentLines().subList(0, 6), expansion.units().getFirst().contentLines());
        assertEquals(List.of("show session statistic", "show empty"), expansion.sectionSeeds().stream()
                .map(NestedEvidenceExpander.SectionSeed::commandText).toList());
        assertEquals(List.of(1, 2), expansion.sectionSeeds().stream()
                .map(NestedEvidenceExpander.SectionSeed::sectionIndex).toList());
        assertEquals(1, expansion.identities().get(2).lineStart());
        assertEquals(6, expansion.identities().get(2).lineEnd());
        assertEquals(7, expansion.sectionSeeds().get(1).lineStart());
    }

    @Test
    void enhancedDelimitedParserFiltersNonAlphanumericTitlesBeforeCreatingBoundaries() {
        NormalizedEvidenceUnit aggregate = new NormalizedEvidenceUnit(1, "COMMAND_OUTPUT",
                Map.of("commandText", "show bundle"), List.of(
                        "--- show session statistic ---", "", "--- *** ---", "Total: 42",
                        "--- !!! ---", "IPv4: 40", "--- 状态2 ---", "healthy"), List.of(), false);

        var sections = new DelimitedSectionParser(true).parse(aggregate, rule().extractors().getFirst());

        assertEquals(List.of("show session statistic", "状态2"), sections.stream()
                .map(DelimitedSectionParser.Section::commandText).toList());
        assertEquals(List.of(1, 2), sections.stream().map(DelimitedSectionParser.Section::sectionIndex).toList());
        assertEquals(6, sections.getFirst().lineEnd());
        assertEquals(aggregate.contentLines().subList(0, 6), sections.getFirst().value().get("lines"));
        assertEquals(7, sections.get(1).lineStart());
    }

    private static CompiledRule rule() {
        Selector selector = new Selector("CONTENT_REGEX", "---", null);
        Extractor extractor = new Extractor("DELIMITED_SECTION", null, null, null, null,
                List.of(), null, "^---\\s*(?<command>.*?)\\s*---$", "command", true);
        Rule rule = new Rule("sections", "AGGREGATE", List.of(selector), List.of(extractor),
                new Target("diagnostics.sections", "MANY", "object", "APPEND_DISTINCT", null),
                List.of(), 0.9);
        return new CompiledRule(rule,
                List.of(new CompiledSelector(selector,
                        Pattern.compile("---", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE))),
                List.of(new CompiledExtractor(extractor, null,
                        Pattern.compile(extractor.startPattern(), Pattern.CASE_INSENSITIVE))));
    }
}
