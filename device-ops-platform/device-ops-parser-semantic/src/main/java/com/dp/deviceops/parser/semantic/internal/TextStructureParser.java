package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent.Section;
import com.dp.deviceops.parser.semantic.GenericContent.TextLine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TextStructureParser {

    public Optional<StructureParseMatch> parse(
            List<StructureLine> lines,
            int offset,
            int sectionIndex) {
        if (offset < 0 || offset >= lines.size() || lines.get(offset).text().isBlank()) {
            return Optional.empty();
        }
        int cursor = offset;
        while (cursor < lines.size() && !lines.get(cursor).text().isBlank()) {
            cursor++;
        }
        List<StructureLine> consumed = lines.subList(offset, cursor);
        List<TextLine> textLines = consumed.stream()
                .map(line -> new TextLine(line.lineNumber(), line.text())).toList();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("lines", textLines);
        Section section = new Section(sectionIndex, "text",
                consumed.getFirst().lineNumber(), consumed.getLast().lineNumber(),
                consumed.stream().map(StructureLine::text).toList(), fields, List.of());
        return Optional.of(new StructureParseMatch(section, cursor));
    }
}
