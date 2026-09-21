package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent;
import com.dp.deviceops.parser.semantic.GenericContent.StructureStatus;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalog.Selector;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalog.StructureRule;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledSelector;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledStructureRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericStructureParserTest {

    @Test
    void preservesMixedSectionOrderAndRanges() {
        NormalizedEvidenceUnit unit = unit(List.of(
                "show mixed",
                "intro text", "",
                "Name: alpha", "State: up", "",
                "Col  Value", "---  -----", "one  first", "",
                "parent: root", "    child: value", "",
                "tail note"));

        GenericContent.Unit parsed = parser(SemanticLimits.defaults())
                .parse(List.of(unit), emptyExpansion(), List.of(), List.of())
                .content().units().getFirst();

        assertEquals(List.of("text", "keyValue", "table", "keyValueTree", "text"),
                parsed.sections().stream().map(GenericContent.Section::type).toList());
        assertEquals(List.of(2, 4, 7, 11, 14),
                parsed.sections().stream().map(GenericContent.Section::startLine).toList());
        assertEquals(StructureStatus.PARTIAL, parsed.structureStatus());
    }

    @Test
    void usesFirstMatchingRuleAndFallsBackOnHintMismatch() {
        NormalizedEvidenceUnit unit = unit(List.of("ordinary sentence"));
        CompiledStructureRule text = rule("first", "TEXT", null);
        CompiledStructureRule table = rule("second", "FORCE", "table");
        GenericContent.Unit first = parser(SemanticLimits.defaults())
                .parse(List.of(unit), emptyExpansion(), List.of(text, table), List.of())
                .content().units().getFirst();
        assertEquals(List.of("text"), first.sections().stream().map(GenericContent.Section::type).toList());

        GenericContent.Unit mismatch = parser(SemanticLimits.defaults())
                .parse(List.of(unit), emptyExpansion(), List.of(table), List.of())
                .content().units().getFirst();
        assertEquals("text", mismatch.sections().getFirst().type());
        assertTrue(mismatch.warnings().stream()
                .anyMatch(warning -> "STRUCTURE_HINT_MISMATCH".equals(warning.code())));
    }

    @Test
    void limitsOnlyTheCurrentUnitAndPreservesTheNextUnit() {
        SemanticLimits limits = new SemanticLimits(2_000, 500, 2_048, 10_000,
                1L << 20, 2L << 20, 100_000, 1L << 20,
                1, 1, 1, 1);
        NormalizedEvidenceUnit first = unit(1, "show first", List.of(
                "Name: one", "State: up", "", "Tail: two"));
        NormalizedEvidenceUnit second = unit(2, "show second", List.of("Name: retained"));

        List<GenericContent.Unit> units = parser(limits)
                .parse(List.of(first, second), emptyExpansion(), List.of(), List.of())
                .content().units();

        assertEquals(StructureStatus.LIMITED, units.getFirst().structureStatus());
        assertNotNull(units.getFirst().omittedLineCount());
        assertEquals(1, units.getFirst().sections().size());
        assertEquals(StructureStatus.STRUCTURED, units.get(1).structureStatus());
    }

    @Test
    void enforcesEachStructureBudgetIndependently() {
        assertLimited(new SemanticLimits(2_000, 500, 2_048, 10_000,
                        1L << 20, 2L << 20, 100_000, 1L << 20, 1, 100, 100, 100),
                List.of("Name: one", "", "State: two"), "keyValue", 1);
        assertLimited(new SemanticLimits(2_000, 500, 2_048, 10_000,
                        1L << 20, 2L << 20, 100_000, 1L << 20, 100, 1, 100, 100),
                List.of("Name  State", "----  -----", "one   up", "two   down"), "table", 1);
        assertLimited(new SemanticLimits(2_000, 500, 2_048, 10_000,
                        1L << 20, 2L << 20, 100_000, 1L << 20, 100, 100, 1, 100),
                List.of("Name: one", "State: up", "Name: two", "State: down"), "recordList", 1);
        assertLimited(new SemanticLimits(2_000, 500, 2_048, 10_000,
                        1L << 20, 2L << 20, 100_000, 1L << 20, 100, 100, 100, 1),
                List.of("parent: root", "    child: value"), "keyValueTree", 1);
    }

    @Test
    void emitsEmptyNestedSeedImmediatelyAfterItsParent() {
        NestedEvidenceExpander.SectionSeed empty = new NestedEvidenceExpander.SectionSeed(
                1, 2, "show empty", 20, 20, List.of("show empty"), false);

        List<GenericContent.Unit> units = parser(SemanticLimits.defaults())
                .parse(List.of(unit(List.of("Name: parent"))),
                        new NestedEvidenceExpander.Expansion(List.of(), Map.of(), List.of(empty)),
                        List.of(), List.of())
                .content().units();

        assertEquals(2, units.size());
        assertEquals(StructureStatus.EMPTY, units.get(1).structureStatus());
        assertEquals(1, units.get(1).parentCommandIndex());
        assertEquals(2, units.get(1).sectionIndex());
        assertTrue(units.get(1).sections().isEmpty());
    }

    private static void assertLimited(
            SemanticLimits limits,
            List<String> lines,
            String type,
            int retainedCount) {
        GenericContent.Unit parsed = parser(limits)
                .parse(List.of(unit(lines)), emptyExpansion(), List.of(), List.of())
                .content().units().getFirst();
        assertEquals(StructureStatus.LIMITED, parsed.structureStatus());
        assertEquals(type, parsed.sections().getFirst().type());
        assertTrue(parsed.omittedLineCount() > 0);
        String field = switch (type) {
            case "table" -> "rows";
            case "recordList" -> "records";
            default -> "entries";
        };
        assertEquals(retainedCount, ((List<?>) parsed.sections().getFirst().fields().get(field)).size());
    }

    private static GenericStructureParser parser(SemanticLimits limits) {
        return new GenericStructureParser(limits);
    }

    private static NormalizedEvidenceUnit unit(List<String> lines) {
        return unit(1, "show mixed", lines);
    }

    private static NormalizedEvidenceUnit unit(int index, String command, List<String> lines) {
        return new NormalizedEvidenceUnit(index, "COMMAND_OUTPUT",
                Map.of("commandText", command, "status", "SUCCEEDED"), lines, List.of(), false);
    }

    private static NestedEvidenceExpander.Expansion emptyExpansion() {
        return new NestedEvidenceExpander.Expansion(List.of(), Map.of(), List.of());
    }

    private static CompiledStructureRule rule(String id, String mode, String type) {
        Selector selector = new Selector("COMMAND_ALIAS", "show mixed", null);
        StructureRule rule = new StructureRule(id, List.of(selector), mode, type, null);
        return new CompiledStructureRule(rule, List.of(new CompiledSelector(selector, null)),
                null, List.of(), List.of());
    }
}
