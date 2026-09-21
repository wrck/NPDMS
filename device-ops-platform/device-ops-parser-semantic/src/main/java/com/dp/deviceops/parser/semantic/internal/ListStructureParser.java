package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent.ListItem;
import com.dp.deviceops.parser.semantic.GenericContent.Section;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ListStructureParser {

    private static final Pattern BULLET = Pattern.compile("^\\s*([-*+])\\s+(.+)$");
    private static final Pattern NUMBERED = Pattern.compile("^\\s*\\d+([.)])\\s+(.+)$");
    private final boolean enhanced;

    public ListStructureParser() {
        this(false);
    }

    public ListStructureParser(boolean enhanced) {
        this.enhanced = enhanced;
    }

    public Optional<StructureParseMatch> parse(
            List<StructureLine> lines,
            int offset,
            int sectionIndex) {
        if (offset < 0 || offset >= lines.size()) {
            return Optional.empty();
        }
        int cursor = offset;
        while (cursor < lines.size() && !lines.get(cursor).text().isBlank()) {
            cursor++;
        }
        List<StructureLine> consumed = lines.subList(offset, cursor);
        if (consumed.size() < 2) {
            return Optional.empty();
        }
        List<String> values = markedValues(consumed, BULLET);
        if (values.isEmpty()) {
            values = markedValues(consumed, NUMBERED);
        }
        if (values.isEmpty() && !enhanced) {
            values = plainValues(consumed);
        }
        if (values.isEmpty() || (enhanced && values.stream().anyMatch(String::isBlank))) {
            return Optional.empty();
        }
        List<ListItem> items = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            StructureLine source = consumed.get(index);
            items.add(new ListItem(index + 1, values.get(index),
                    source.lineNumber(), source.lineNumber()));
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("items", items);
        Section section = new Section(sectionIndex, "list",
                consumed.getFirst().lineNumber(), consumed.getLast().lineNumber(),
                consumed.stream().map(StructureLine::text).toList(), fields, List.of());
        return Optional.of(new StructureParseMatch(section, cursor));
    }

    private static List<String> markedValues(List<StructureLine> lines, Pattern pattern) {
        List<String> values = new ArrayList<>();
        String style = null;
        for (StructureLine line : lines) {
            Matcher matcher = pattern.matcher(line.text());
            if (!matcher.matches()) {
                return List.of();
            }
            if (style == null) {
                style = matcher.group(1);
            } else if (!style.equals(matcher.group(1))) {
                return List.of();
            }
            values.add(matcher.group(2).strip());
        }
        return List.copyOf(values);
    }

    private static List<String> plainValues(List<StructureLine> lines) {
        int words = -1;
        List<String> values = new ArrayList<>();
        for (StructureLine line : lines) {
            String value = line.text().strip();
            if (value.contains(":") || value.contains("=") || value.contains("|")) {
                return List.of();
            }
            int currentWords = value.split("\\s+").length;
            if (words < 0) {
                words = currentWords;
            } else if (words != currentWords) {
                return List.of();
            }
            values.add(value);
        }
        return List.copyOf(values);
    }
}
