package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent.Section;
import com.dp.deviceops.parser.semantic.GenericContent.TableColumn;
import com.dp.deviceops.parser.semantic.GenericContent.TableRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnhancedTableStructureParserTest {

    private final TableStructureParser parser = new TableStructureParser(true);

    @Test
    void parsesHeaderAndSingleAlignedRow() {
        Section section = parse("Port  State  Detail", "p1    up     primary uplink").section();
        assertEquals(List.of("Port", "State", "Detail"), labels(section));
        assertEquals("primary uplink", rows(section).getFirst().values().get("column3"));
    }

    @Test
    void doesNotCreateColumnForWholeTableIndentation() {
        Section section = parse("    Name      State   Description", "    --------  ------  -----------",
                "    alpha     up      ready", "    beta              backup link").section();
        assertEquals(List.of("Name", "State", "Description"), labels(section));
        assertNull(rows(section).get(1).values().get("column2"));
        assertEquals("backup link", rows(section).get(1).values().get("column3"));
    }

    @Test
    void expandsTabsAtFourColumnStopsWithoutChangingRawLines() {
        List<StructureLine> input = lines("\tName\tState\tDetail", "\tunit\tup\t\tready");
        Section section = parser.parse(input, 0, 1, List.of()).orElseThrow().section();
        assertEquals(List.of("Name", "State", "Detail"), labels(section));
        assertEquals("up", rows(section).getFirst().values().get("column2"));
        assertEquals("ready", rows(section).getFirst().values().get("column3"));
        assertEquals(input.stream().map(StructureLine::text).toList(), section.rawLines());
    }

    @Test
    void usesHeaderAndDataInsteadOfMixedDashDecorationAsColumns() {
        Section section = parse("Slot ID   Board Name   Board Temperature   CPU Temperature   Switch-chip Temperature",
                "- - - - - - - - - - -- - - - - - - - - - - - - - - - - - - - - - - - - - - - - -",
                "0         board        31                  32                31").section();
        assertEquals(List.of("Slot ID", "Board Name", "Board Temperature", "CPU Temperature", "Switch-chip Temperature"),
                labels(section));
        assertEquals("32", rows(section).getFirst().values().get("column4"));
    }

    @Test
    void readsSanitizedEnvironmentAsFiveIntactColumns() throws Exception {
        List<StructureLine> input = sanitizedTech(153, 155);
        Section section = parser.parse(input, 0, 1, List.of()).orElseThrow().section();
        assertEquals(List.of("Slot ID", "Board Name", "Board Temperature", "CPU Temperature", "Switch-chip Temperature"),
                labels(section));
        assertEquals("31", rows(section).getFirst().values().get("column3"));
        assertEquals("32", rows(section).getFirst().values().get("column4"));
        assertEquals("31", rows(section).getFirst().values().get("column5"));
        assertEquals(153, section.startLine());
        assertEquals(155, section.endLine());
    }

    @Test
    void readsSanitizedCpuMatrixWithRightAlignedValues() throws Exception {
        Section section = parser.parse(sanitizedTech(199, 200), 0, 1, List.of()).orElseThrow().section();
        assertEquals(List.of("CpuId", "0", "1", "2", "3"), labels(section));
        assertEquals(List.of("Usage", "7%", "1%", "1%", "2%"),
                new ArrayList<>(rows(section).getFirst().values().values()));
    }

    @Test
    void compactRouteStatisticsAreNotAHeaderAndFollowingTableHasThreeColumns() throws Exception {
        List<StructureLine> input = sanitizedTech(1589, 1592);
        assertTrue(parser.parse(input, 0, 1, List.of()).isEmpty());
        StructureParseMatch match = parser.parse(input, 1, 2, List.of()).orElseThrow();
        assertEquals(List.of("Route Source", "Active", "Inactive"), labels(match.section()));
        assertEquals(2, rows(match.section()).size());
        assertEquals("2", rows(match.section()).getFirst().values().get("column2"));
        assertEquals(4, match.nextOffset());
    }

    @Test
    void stopsBeforeCompactOrSpacedKeyValuesAndUnalignedProse() {
        for (String tail : List.of("Total: 1", "Status:healthy", "tail note with no table alignment",
                "Other  Table", "2026-09-08 10:20:30 INFO source: event")) {
            StructureParseMatch match = parse("Name      State   Detail", "--------  ------  ------",
                    "alpha     up      ready", tail, "later text");
            assertEquals(3, match.nextOffset(), tail);
            assertEquals(1, rows(match.section()).size(), tail);
            assertEquals(3, match.section().rawLines().size(), tail);
        }
    }

    @Test
    void memoryTitleFollowedByKeyValuesIsNotATable() {
        assertTrue(parser.parse(lines("Memory details", "Total: 100", "Used: 25"), 0, 1, List.of()).isEmpty());
        assertTrue(parser.parse(lines("Memory  details", "Total:100", "Used:25"), 0, 1, List.of()).isEmpty());
    }

    @Test
    void handlesPlusBordersAndStopsAtIncompatiblePipeRow() {
        StructureParseMatch match = parse("+------+-------+", "| Name | State |", "+------+-------+",
                "| edge | up    |", "+------+-------+", "| unrelated | row | extra |", "Total:1");
        assertEquals(List.of("Name", "State"), labels(match.section()));
        assertEquals("edge", rows(match.section()).getFirst().values().get("column1"));
        assertEquals(5, match.nextOffset());
    }

    @Test
    void preservesMarkdownEmptyCellsAndStableDuplicateLabels() {
        Section section = parse("| Name | Name | column1 |", "| :--- | ---: | :---: |", "| a | | c |").section();
        assertEquals(List.of("Name", "Name", "column1"), labels(section));
        assertEquals(List.of("column1", "column2", "column3"),
                columns(section).stream().map(TableColumn::id).toList());
        assertEquals("a", rows(section).getFirst().values().get("column1"));
        assertNull(rows(section).getFirst().values().get("column2"));
        assertEquals("c", rows(section).getFirst().values().get("column3"));
    }

    @Test
    void retainsHeaderlessPipeRowsAndExplicitAlignedColumnHints() {
        Section pipe = parse("| a | b |", "| c | d |").section();
        assertNull(columns(pipe).getFirst().label());
        assertEquals(2, rows(pipe).size());
        Section aligned = parser.parse(lines("alpha  up  first", "beta   down second"), 0, 1,
                List.of("Name", "State", "Detail")).orElseThrow().section();
        assertEquals(List.of("Name", "State", "Detail"), labels(aligned));
        assertEquals(2, rows(aligned).size());
    }

    @Test
    void stopsBeforeASecondHeaderAndRuledTableWithoutBlankLine() {
        List<StructureLine> input = lines("Name  State", "----  -----", "a     up",
                "Port  State", "----  -----", "p1    down");
        StructureParseMatch first = parser.parse(input, 0, 1, List.of()).orElseThrow();
        assertEquals(3, first.nextOffset());
        StructureParseMatch second = parser.parse(input, first.nextOffset(), 2, List.of()).orElseThrow();
        assertEquals(List.of("Port", "State"), labels(second.section()));
        assertEquals("p1", rows(second.section()).getFirst().values().get("column1"));
    }

    @Test
    void usesSingleTabAsDelimiterEvenWhenItExpandsToOneSpace() {
        Section section = parse("Key\tAge\tSize", "abc\t123\t4567").section();
        assertEquals(List.of("Key", "Age", "Size"), labels(section));
        assertEquals(List.of("abc", "123", "4567"), new ArrayList<>(rows(section).getFirst().values().values()));
    }

    @Test
    void usesGroupedRuleWhenHeaderHasOnlySingleSpaces() {
        Section section = parse("Name Age Count", "---- --- -----", "beta  42  1000").section();
        assertEquals(List.of("Name", "Age", "Count"), labels(section));
        assertEquals("1000", rows(section).getFirst().values().get("column3"));
    }

    @Test
    void includesLeadingDecorativeBorderWithoutTreatingItAsAColumnDefinition() {
        Section section = parse("- - - -- - - - - - - - - -", "Name  State", "----  -----", "edge  up").section();
        assertEquals(List.of("Name", "State"), labels(section));
        assertEquals(4, section.rawLines().size());
    }

    private StructureParseMatch parse(String... text) {
        return parser.parse(lines(text), 0, 1, List.of()).orElseThrow();
    }

    private static List<StructureLine> sanitizedTech(int first, int last) throws Exception {
        Path fixture = Path.of("..", "parser-releases", "device-command-output-1.3.0", "input-real-sanitized.json");
        var blocks = new ObjectMapper().readTree(fixture.toFile()).get("commandBlocks");
        String[] text = blocks.get(2).get("stdout").asText().split("\\R", -1);
        List<StructureLine> result = new ArrayList<>();
        for (int number = first; number <= last; number++) {
            result.add(new StructureLine(number, text[number - 1]));
        }
        return List.copyOf(result);
    }

    private static List<StructureLine> lines(String... text) {
        List<StructureLine> result = new ArrayList<>();
        for (int index = 0; index < text.length; index++) {
            result.add(new StructureLine(index + 1, text[index]));
        }
        return List.copyOf(result);
    }

    private static List<String> labels(Section section) {
        return columns(section).stream().map(TableColumn::label).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<TableColumn> columns(Section section) {
        return (List<TableColumn>) section.fields().get("columns");
    }

    @SuppressWarnings("unchecked")
    private static List<TableRow> rows(Section section) {
        return (List<TableRow>) section.fields().get("rows");
    }
}
