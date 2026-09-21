package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent.ConfigStanza;
import com.dp.deviceops.parser.semantic.GenericContent.Section;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ConfigStanzaStructureParser {

    private final boolean enhanced;

    public ConfigStanzaStructureParser() {
        this(false);
    }

    public ConfigStanzaStructureParser(boolean enhanced) {
        this.enhanced = enhanced;
    }

    public Optional<StructureParseMatch> parse(
            List<StructureLine> lines,
            int offset,
            int sectionIndex,
            boolean enabled,
            List<String> configSeparators) {
        if (!enabled || offset < 0 || offset >= lines.size()) {
            return Optional.empty();
        }
        Set<String> separators = Set.copyOf(configSeparators == null ? List.of("!") : configSeparators);
        int start = offset;
        int firstSeparator = offset;
        while (firstSeparator < lines.size() && !isSeparator(lines.get(firstSeparator), separators)) {
            firstSeparator++;
        }
        if (!enhanced && firstSeparator < lines.size()
                && lines.subList(offset, firstSeparator).stream().anyMatch(line -> line.text().isBlank())) {
            start = firstSeparator + 1;
        }
        if (enhanced && firstSeparator < lines.size()
                && lines.subList(offset, firstSeparator).stream()
                .anyMatch(line -> line.text().strip().matches("(?i)(?:Building configuration\\.\\.\\.|Current configuration.*)"))) {
            return Optional.empty();
        }
        int cursor = lines.size();
        List<StructureLine> consumed = lines.subList(start, cursor);
        List<ConfigStanza> stanzas = new ArrayList<>();
        StanzaBuilder current = null;
        for (StructureLine line : consumed) {
            String content = line.text().strip();
            if (isSeparator(line, separators) || "end".equalsIgnoreCase(content)) {
                if (current != null) {
                    stanzas.add(current.build());
                    current = null;
                }
            } else if (line.text().isBlank()) {
                if (current != null) {
                    current.add(line);
                }
            } else if (line.indentationColumns() == 0) {
                if (current != null) {
                    stanzas.add(current.build());
                }
                current = new StanzaBuilder(line);
            } else if (current != null) {
                current.add(line);
            }
        }
        if (current != null) {
            stanzas.add(current.build());
        }
        if (stanzas.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("stanzas", stanzas);
        Section section = new Section(sectionIndex, "configStanza",
                consumed.getFirst().lineNumber(), consumed.getLast().lineNumber(),
                consumed.stream().map(StructureLine::text).toList(), fields, List.of());
        return Optional.of(new StructureParseMatch(section, cursor));
    }

    private static boolean isSeparator(StructureLine line, Set<String> separators) {
        String content = line.text().strip();
        return separators.stream().anyMatch(separator -> content.equals(separator)
                || (!separator.isEmpty() && content.startsWith(separator)));
    }

    private static final class StanzaBuilder {
        private final StructureLine header;
        private final List<String> lines = new ArrayList<>();
        private int endLine;

        private StanzaBuilder(StructureLine header) {
            this.header = header;
            this.endLine = header.lineNumber();
        }

        private void add(StructureLine line) {
            lines.add(line.text());
            endLine = line.lineNumber();
        }

        private ConfigStanza build() {
            return new ConfigStanza(header.text().strip(), header.lineNumber(), endLine, lines);
        }
    }
}
