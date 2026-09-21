package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent.TableColumn;
import com.dp.deviceops.parser.semantic.GenericContent.TableRow;
import com.dp.deviceops.parser.semantic.GenericContent.UnparsedLine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableStructureParserTest {

    private final TableStructureParser parser = new TableStructureParser();

    @Test
    void parsesHeaderSeparatorMissingCellsAndLastColumnSpaces() {
        var section = parser.parse(lines(
                "Name      State   Description",
                "--------  ------  -----------",
                "alpha     up      description with spaces",
                "beta              ready"), 0, 1, List.of()).orElseThrow().section();

        List<TableColumn> columns = columns(section);
        List<TableRow> rows = rows(section);
        assertEquals(List.of("column1", "column2", "column3"),
                columns.stream().map(TableColumn::id).toList());
        assertEquals("Name", columns.getFirst().label());
        assertEquals("description with spaces", rows.getFirst().values().get("column3"));
        assertNull(rows.get(1).values().get("column2"));
        assertEquals("ready", rows.get(1).values().get("column3"));
    }

    @Test
    void parsesStableHeaderStartsWithoutSeparator() {
        var match = parser.parse(lines(
                "Port  Status  Detail",
                "p1    up      primary uplink",
                "p2    down    backup link"), 0, 1, List.of()).orElseThrow();

        assertEquals(3, match.nextOffset());
        assertEquals("primary uplink", rows(match.section()).getFirst().values().get("column3"));
    }

    @Test
    void parsesBorderedPipeTable() {
        var section = parser.parse(lines(
                "| Name | State |",
                "| ---- | ----- |",
                "| edge | up    |"), 0, 1, List.of()).orElseThrow().section();

        assertEquals("Name", columns(section).getFirst().label());
        assertEquals("up", rows(section).getFirst().values().get("column2"));
    }

    @Test
    void parsesHeaderlessPipeRecordsWithNullLabels() {
        var section = parser.parse(lines("| a | b |", "| c | d |"),
                0, 1, List.of()).orElseThrow().section();

        assertNull(columns(section).getFirst().label());
        assertNull(columns(section).get(1).label());
        assertEquals(2, rows(section).size());
    }

    @Test
    void keepsDuplicateHeadersByPositionAndSupportsForcedHeaderlessColumns() {
        var duplicate = parser.parse(lines(
                "Name  Name  Description",
                "----  ----  -----------",
                "a     b     repeated labels"), 0, 1, List.of()).orElseThrow().section();
        assertEquals(List.of("Name", "Name", "Description"),
                columns(duplicate).stream().map(TableColumn::label).toList());
        assertEquals("a", rows(duplicate).getFirst().values().get("column1"));
        assertEquals("b", rows(duplicate).getFirst().values().get("column2"));

        var forced = parser.parse(lines("alpha  up  first", "beta   down second"),
                0, 2, List.of("Name", "State", "Detail")).orElseThrow().section();
        assertEquals(List.of("Name", "State", "Detail"),
                columns(forced).stream().map(TableColumn::label).toList());
        assertEquals(2, rows(forced).size());
    }

    @Test
    void retainsOutOfBoundsContinuationAsUnparsedLine() {
        var section = parser.parse(lines(
                "Name      State   Description",
                "--------  ------  -----------",
                "alpha     up      ready",
                "          continuation outside first column",
                "beta      down    retained"), 0, 1, List.of()).orElseThrow().section();

        List<UnparsedLine> unparsed = unparsed(section);
        assertEquals(1, unparsed.size());
        assertEquals(4, unparsed.getFirst().lineNumber());
        assertEquals(2, rows(section).size());
        assertTrue(rows(section).stream().anyMatch(row -> "beta".equals(row.values().get("column1"))));
    }

    private static List<StructureLine> lines(String... values) {
        List<StructureLine> result = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            result.add(new StructureLine(index + 1, values[index]));
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static List<TableColumn> columns(com.dp.deviceops.parser.semantic.GenericContent.Section section) {
        return (List<TableColumn>) section.fields().get("columns");
    }

    @SuppressWarnings("unchecked")
    private static List<TableRow> rows(com.dp.deviceops.parser.semantic.GenericContent.Section section) {
        return (List<TableRow>) section.fields().get("rows");
    }

    @SuppressWarnings("unchecked")
    private static List<UnparsedLine> unparsed(com.dp.deviceops.parser.semantic.GenericContent.Section section) {
        return (List<UnparsedLine>) section.fields().get("unparsedLines");
    }
}
