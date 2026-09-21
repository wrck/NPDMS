package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent.ConfigStanza;
import com.dp.deviceops.parser.semantic.GenericContent.ListItem;
import com.dp.deviceops.parser.semantic.GenericContent.RecordValue;
import com.dp.deviceops.parser.semantic.GenericContent.TextLine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordAndFallbackStructureParserTest {

    private final RecordStructureParser records = new RecordStructureParser(new KeyValueStructureParser());

    @Test
    void parsesHorizontalAndRepeatedFirstKeyRecordBoundaries() {
        var horizontal = records.parse(lines(
                "Name: first", "State: up", "-----", "Name: second", "State: down"),
                0, 1, null).orElseThrow().section();
        assertEquals(2, records(horizontal).size());
        assertEquals("first", records(horizontal).getFirst().entries().getFirst().value());
        assertEquals("second", records(horizontal).get(1).entries().getFirst().value());

        var repeated = records.parse(lines(
                "Name: first", "State: up", "Name: second", "State: down"),
                0, 1, null).orElseThrow().section();
        assertEquals(2, records(repeated).size());
    }

    @Test
    void usesOnlyForcedNamedGroupForRecordIdentity() {
        Pattern start = Pattern.compile("^Interface\\s+(?<id>\\S+)$");
        var section = records.parse(lines(
                "Interface eth0", "    State: up",
                "Interface eth1", "    State: down"), 0, 1, start).orElseThrow().section();

        assertEquals(List.of("eth0", "eth1"),
                records(section).stream().map(RecordValue::identity).toList());
    }

    @Test
    void parsesEnabledConfigStanzasAndPreservesDuplicateHeaders() {
        ConfigStanzaStructureParser parser = new ConfigStanzaStructureParser();
        assertTrue(parser.parse(lines("interface edge", " description one"),
                0, 1, false, List.of("!")).isEmpty());

        var section = parser.parse(lines(
                "interface edge", " description one", "!",
                "interface edge", " description two", "!"),
                0, 1, true, List.of("!")).orElseThrow().section();
        List<ConfigStanza> stanzas = stanzas(section);
        assertEquals(List.of("interface edge", "interface edge"),
                stanzas.stream().map(ConfigStanza::header).toList());
        assertEquals(List.of(" description one"), stanzas.getFirst().lines());
        assertEquals(List.of(" description two"), stanzas.get(1).lines());
    }

    @Test
    void parsesHomogeneousListsAndFallsBackToText() {
        var list = new ListStructureParser().parse(lines("- first item", "- second item"),
                0, 1).orElseThrow().section();
        assertEquals(List.of("first item", "second item"),
                items(list).stream().map(ListItem::value).toList());

        TextStructureParser textParser = new TextStructureParser();
        var text = textParser.parse(lines("Ordinary explanatory sentence."), 0, 2)
                .orElseThrow().section();
        assertEquals("text", text.type());
        assertEquals("Ordinary explanatory sentence.", textLines(text).getFirst().value());
        assertTrue(textParser.parse(List.of(), 0, 1).isEmpty());
        assertTrue(textParser.parse(lines("   "), 0, 1).isEmpty());
    }

    private static List<StructureLine> lines(String... values) {
        List<StructureLine> result = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            result.add(new StructureLine(index + 1, values[index]));
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static List<RecordValue> records(com.dp.deviceops.parser.semantic.GenericContent.Section section) {
        return (List<RecordValue>) section.fields().get("records");
    }

    @SuppressWarnings("unchecked")
    private static List<ConfigStanza> stanzas(com.dp.deviceops.parser.semantic.GenericContent.Section section) {
        return (List<ConfigStanza>) section.fields().get("stanzas");
    }

    @SuppressWarnings("unchecked")
    private static List<ListItem> items(com.dp.deviceops.parser.semantic.GenericContent.Section section) {
        return (List<ListItem>) section.fields().get("items");
    }

    @SuppressWarnings("unchecked")
    private static List<TextLine> textLines(com.dp.deviceops.parser.semantic.GenericContent.Section section) {
        return (List<TextLine>) section.fields().get("lines");
    }
}
