package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent;
import com.dp.deviceops.parser.semantic.GenericContent.KeyValueEntry;
import com.dp.deviceops.parser.semantic.GenericContent.RecordValue;
import com.dp.deviceops.parser.semantic.GenericContent.Section;
import com.dp.deviceops.parser.semantic.GenericContent.StructureStatus;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalog.Selector;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalog.StructureRule;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledSelector;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledStructureRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EnhancedGenericStructureParserTest {

    @Test
    void textModeRetainsAllParagraphs() {
        var result = parse(List.of("first paragraph", "", "second paragraph"), "TEXT", null);
        assertEquals(List.of("first paragraph", "second paragraph"), raw(result));
    }

    @Test
    void forcedKeyValuesPreserveResidualTextAndLaterFields() {
        var result = parse(List.of("Name: alpha", "", "State: up", "tail note"), "FORCE", "keyValue");
        assertEquals(List.of("Name: alpha", "State: up", "tail note"), raw(result));
        assertEquals(StructureStatus.PARTIAL, result.structureStatus());
    }

    @Test
    void recordsParseMixedBodiesAfterNonKeyValueOpeningLines() {
        var result = parse(List.of("Interface portA", "    physical state is UP",
                "    MTU: 1500", "    description text", "    State: up",
                "Interface portB", "    physical state is DOWN", "    MTU: 1400"), "AUTO", null);
        var records = records(result);
        assertEquals(2, records.size());
        assertTrue(records.stream().allMatch(record -> !record.sections().isEmpty()));
        assertEquals(List.of("MTU", "State"), records.getFirst().sections().stream()
                .filter(section -> section.type().startsWith("keyValue"))
                .flatMap(section -> entries(section).stream()).map(KeyValueEntry::key).toList());
        assertEquals(StructureStatus.PARTIAL, result.structureStatus());
    }

    @Test
    void recordsCanSpanBlankLinesWithoutLosingTheirBody() {
        var result = parse(List.of("Interface portA", "    State: up", "", "    MTU: 1500", "",
                "Interface portB", "    State: down", "", "    MTU: 1400"), "AUTO", null);
        assertEquals(2, records(result).size());
        assertEquals(6, raw(result).size());
    }

    @Test
    void configurationBlankLineDoesNotEraseLegalPrefix() {
        var result = parse(List.of("hostname edge", "", "interface portA", " description example",
                "!", "interface portB", " shutdown"), "FORCE", "configStanza");
        assertEquals(List.of("hostname edge", "interface portA", " description example", "!",
                "interface portB", " shutdown"), raw(result));
        var stanzas = (List<?>) result.sections().getFirst().fields().get("stanzas");
        assertEquals(3, stanzas.size());
    }

    @Test
    void blankForcedListItemFallsBackWithoutFailingOrDroppingLines() {
        var result = parse(List.of("- first", "-   "), "FORCE", "list");
        assertEquals(List.of("- first", "-   "), raw(result));
        assertEquals(StructureStatus.PARTIAL, result.structureStatus());
    }

    @Test
    void tableLimitDoesNotConsumeIndependentKeyValueNodeBudget() {
        var result = parse(List.of("Name  State", "----  -----", "one   up", "two   down", "",
                "A: one", "B: two", "C: three"), "AUTO", null, limits(1, 100));
        assertEquals(StructureStatus.LIMITED, result.structureStatus());
        assertEquals(List.of("A", "B", "C"), result.sections().stream()
                .filter(section -> section.type().startsWith("keyValue"))
                .flatMap(section -> entries(section).stream()).map(KeyValueEntry::key).toList());
    }

    @Test
    void recordChildrenParticipateInNodeBudget() {
        var result = parse(List.of("Name: first", "State: up", "Name: second", "State: down"),
                "AUTO", null, limits(100, 1));
        assertEquals(StructureStatus.LIMITED, result.structureStatus());
        assertNotNull(result.omittedLineCount());
        assertEquals(1, records(result).stream().mapToLong(record -> record.entries().size()
                + record.sections().stream().filter(section -> section.type().startsWith("keyValue"))
                .mapToLong(section -> entries(section).size()).sum()).sum());
    }

    @Test
    void tableLimitDoesNotCountClosingBorderAsAnOmittedDataLine() {
        var result = parse(List.of("| Name | State |", "| ---- | ----- |", "| one | up |",
                "| two | down |", "+------+-------+"), "AUTO", null, limits(1, 100));
        assertEquals(StructureStatus.LIMITED, result.structureStatus());
        assertEquals(1, result.omittedLineCount());
    }

    @Test
    void depthLimitPreservesRemainingEvidenceWithoutRecursiveFailure() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            lines.add(" ".repeat(i * 2) + "level: " + i);
        }
        var result = parse(lines, "AUTO", null);
        assertEquals(100, raw(result).size());
        assertEquals(StructureStatus.LIMITED, result.structureStatus());
    }

    @Test
    void textAndUnparsedChildrenMakeARecordPartial() {
        var result = parse(List.of("Name: first", "State: up", "unrecognized note",
                "Name: second", "State: down", "another note"), "AUTO", null);
        assertEquals(StructureStatus.PARTIAL, result.structureStatus());
        assertEquals(6, raw(result).size());
    }

    @Test
    void sectionLimitCountsTheEntireRemainingSourceExactlyOnce() {
        SemanticLimits limits = new SemanticLimits(2_000, 500, 2_048, 10_000,
                1L << 20, 2L << 20, 100_000, 1L << 20, 1, 100, 100, 100);
        var result = parse(List.of("Name: first", "", "State: up", "", "Tail: retained"),
                "AUTO", null, limits);
        assertEquals(StructureStatus.LIMITED, result.structureStatus());
        assertEquals(2, result.omittedLineCount());
    }

    private static GenericContent.Unit parse(List<String> lines, String mode, String type) {
        return parse(lines, mode, type, SemanticLimits.defaults());
    }

    private static GenericContent.Unit parse(List<String> lines, String mode, String type, SemanticLimits limits) {
        var unit = new NormalizedEvidenceUnit(1, "COMMAND_OUTPUT",
                Map.of("commandText", "show audit", "status", "SUCCEEDED"), lines, List.of(), false);
        Selector selector = new Selector("COMMAND_ALIAS", "show audit", null);
        StructureRule source = new StructureRule("audit", List.of(selector), mode, type, null);
        var rule = new CompiledStructureRule(source, List.of(new CompiledSelector(selector, null)),
                null, List.of(), List.of("!"));
        return new GenericStructureParser(limits, true).parse(List.of(unit),
                new NestedEvidenceExpander.Expansion(List.of(), Map.of(), List.of()),
                List.of(rule), List.of()).content().units().getFirst();
    }

    private static SemanticLimits limits(int rows, int nodes) {
        return new SemanticLimits(2_000, 500, 2_048, 10_000, 1L << 20, 2L << 20,
                100_000, 1L << 20, 100, rows, 100, nodes);
    }

    private static List<String> raw(GenericContent.Unit result) {
        return result.sections().stream().flatMap(section -> section.rawLines().stream())
                .filter(line -> !line.isBlank()).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<RecordValue> records(GenericContent.Unit result) {
        return (List<RecordValue>) result.sections().stream().filter(section -> section.type().equals("recordList"))
                .findFirst().orElseThrow().fields().get("records");
    }

    @SuppressWarnings("unchecked")
    private static List<KeyValueEntry> entries(Section section) {
        return (List<KeyValueEntry>) section.fields().get("entries");
    }
}
