package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent.KeyValueEntry;
import com.dp.deviceops.parser.semantic.GenericContent.Section;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class StructureBudget {

    private final SemanticLimits limits;
    private int sections;
    private int tableRows;
    private int records;
    private int nodes;
    private int omittedLineCount;
    private boolean limited;
    private final boolean enhanced;
    private final java.util.Set<Integer> omittedLines = new java.util.HashSet<>();

    StructureBudget(SemanticLimits limits) {
        this(limits, false);
    }

    StructureBudget(SemanticLimits limits, boolean enhanced) {
        this.limits = limits;
        this.enhanced = enhanced;
    }

    boolean canAcceptSection() {
        return sections < limits.maxGenericSections();
    }

    void omitLines(List<StructureLine> lines) {
        lines.stream().filter(line -> !line.text().isBlank()).forEach(line -> omittedLines.add(line.lineNumber()));
        limited |= !omittedLines.isEmpty();
    }

    Optional<Section> accept(Section section) {
        if (enhanced) {
            return acceptEnhanced(section);
        }
        if (sections >= limits.maxGenericSections()) {
            omit(section.rawLines().size());
            return Optional.empty();
        }
        sections++;
        return Optional.of(switch (section.type()) {
            case "table" -> limitList(section, "rows", limits.maxTableRows() - tableRows, true);
            case "recordList" -> limitList(section, "records", limits.maxRecords() - records, false);
            case "keyValue", "keyValueTree" -> limitEntries(section);
            default -> section;
        });
    }

    private Optional<Section> acceptEnhanced(Section section) {
        if (!canAcceptSection()) {
            omitSection(section);
            return Optional.empty();
        }
        sections++;
        Map<String, Object> fields = new LinkedHashMap<>(section.fields());
        if ("keyValue".equals(section.type()) || "keyValueTree".equals(section.type())) {
            @SuppressWarnings("unchecked")
            List<KeyValueEntry> entries = (List<KeyValueEntry>) fields.get("entries");
            List<KeyValueEntry> kept = new ArrayList<>();
            for (KeyValueEntry entry : entries) {
                KeyValueEntry accepted = acceptEnhancedEntry(entry, 0);
                if (accepted != null) {
                    kept.add(accepted);
                }
            }
            fields.put("entries", kept);
            fields.put("data", materialize(kept));
        } else if ("recordList".equals(section.type())) {
            List<com.dp.deviceops.parser.semantic.GenericContent.RecordValue> kept = new ArrayList<>();
            for (Object value : (List<?>) fields.get("records")) {
                var record = (com.dp.deviceops.parser.semantic.GenericContent.RecordValue) value;
                if (records >= limits.maxRecords()) {
                    omitRange(record.startLine(), record.endLine());
                    continue;
                }
                records++;
                List<KeyValueEntry> entries = new ArrayList<>();
                for (KeyValueEntry entry : record.entries()) {
                    KeyValueEntry accepted = acceptEnhancedEntry(entry, 0);
                    if (accepted != null) {
                        entries.add(accepted);
                    }
                }
                List<Section> children = new ArrayList<>();
                for (Section child : record.sections()) {
                    acceptEnhanced(child).ifPresent(children::add);
                }
                kept.add(new com.dp.deviceops.parser.semantic.GenericContent.RecordValue(record.recordIndex(),
                        record.identity(), record.startLine(), record.endLine(), entries, children));
            }
            fields.put("records", kept);
        } else if ("table".equals(section.type())) {
            List<?> rows = (List<?>) fields.get("rows");
            int allowed = Math.max(0, Math.min(limits.maxTableRows() - tableRows, rows.size()));
            tableRows += allowed;
            if (allowed < rows.size()) {
                int firstOmitted = Math.max(section.startLine(), section.endLine() - rows.size() + allowed + 1);
                omitRange(firstOmitted, section.endLine());
                fields.put("rows", rows.subList(0, allowed));
            }
        } else if ("configStanza".equals(section.type())) {
            List<?> stanzas = (List<?>) fields.get("stanzas");
            int allowed = Math.max(0, Math.min(limits.maxRecords() - records, stanzas.size()));
            records += allowed;
            for (int i = allowed; i < stanzas.size(); i++) {
                var stanza = (com.dp.deviceops.parser.semantic.GenericContent.ConfigStanza) stanzas.get(i);
                omitRange(stanza.startLine(), stanza.endLine());
            }
            fields.put("stanzas", stanzas.subList(0, allowed));
        }
        return Optional.of(copy(section, fields));
    }

    private KeyValueEntry acceptEnhancedEntry(KeyValueEntry entry, int depth) {
        if (nodes >= limits.maxStructureNodes() || depth >= 64) {
            omitRange(entry.startLine(), entry.endLine());
            return null;
        }
        nodes++;
        List<KeyValueEntry> children = new ArrayList<>();
        for (KeyValueEntry child : entry.children()) {
            KeyValueEntry accepted = acceptEnhancedEntry(child, depth + 1);
            if (accepted != null) {
                children.add(accepted);
            }
        }
        return new KeyValueEntry(entry.key(), entry.value(), entry.startLine(), entry.endLine(), children);
    }

    private void omitSection(Section section) {
        for (int i = 0; i < section.rawLines().size(); i++) {
            if (!section.rawLines().get(i).isBlank()) {
                omittedLines.add(section.startLine() + i);
            }
        }
        limited = true;
    }

    private void omitRange(int start, int end) {
        for (int line = start; line <= end; line++) {
            omittedLines.add(line);
        }
        limited = true;
    }

    private Section limitList(Section section, String field, int remaining, boolean table) {
        List<?> values = (List<?>) section.fields().get(field);
        int allowed = Math.max(0, Math.min(remaining, values.size()));
        if (allowed < values.size()) {
            omit(values.size() - allowed);
        }
        if (table) {
            tableRows += allowed;
        } else {
            records += allowed;
        }
        if (allowed == values.size()) {
            return section;
        }
        Map<String, Object> fields = new LinkedHashMap<>(section.fields());
        fields.put(field, values.subList(0, allowed));
        return copy(section, fields);
    }

    @SuppressWarnings("unchecked")
    private Section limitEntries(Section section) {
        List<KeyValueEntry> entries = (List<KeyValueEntry>) section.fields().get("entries");
        List<KeyValueEntry> kept = new ArrayList<>();
        for (KeyValueEntry entry : entries) {
            KeyValueEntry accepted = acceptEntry(entry);
            if (accepted == null) {
                break;
            }
            kept.add(accepted);
            if (limited) {
                break;
            }
        }
        if (!limited && kept.size() == entries.size()) {
            return section;
        }
        for (int index = kept.size(); index < entries.size(); index++) {
            omit(countEntries(entries.get(index)));
        }
        Map<String, Object> fields = new LinkedHashMap<>(section.fields());
        fields.put("entries", kept);
        fields.put("data", materialize(kept));
        return copy(section, fields);
    }

    private KeyValueEntry acceptEntry(KeyValueEntry entry) {
        if (nodes >= limits.maxStructureNodes()) {
            return null;
        }
        nodes++;
        List<KeyValueEntry> children = new ArrayList<>();
        for (KeyValueEntry child : entry.children()) {
            KeyValueEntry accepted = acceptEntry(child);
            if (accepted == null) {
                omit(countEntries(child));
                break;
            }
            children.add(accepted);
            if (limited) {
                break;
            }
        }
        if (children.size() < entry.children().size()) {
            for (int index = children.size() + 1; index < entry.children().size(); index++) {
                omit(countEntries(entry.children().get(index)));
            }
            return new KeyValueEntry(entry.key(), entry.value(), entry.startLine(),
                    children.isEmpty() ? entry.startLine() : children.getLast().endLine(), children);
        }
        return entry;
    }

    private static int countEntries(KeyValueEntry entry) {
        int count = 1;
        for (KeyValueEntry child : entry.children()) {
            count = Math.addExact(count, countEntries(child));
        }
        return count;
    }

    private static Map<String, Object> materialize(List<KeyValueEntry> entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (KeyValueEntry entry : entries) {
            Object value;
            if (entry.children().isEmpty()) {
                value = entry.value();
            } else {
                Map<String, Object> wrapped = new LinkedHashMap<>();
                if (!entry.value().isEmpty()) {
                    wrapped.put("nodeValue", entry.value());
                }
                wrapped.put("children", materialize(entry.children()));
                value = wrapped;
            }
            Object existing = result.get(entry.key());
            if (existing == null) {
                result.put(entry.key(), value);
            } else if (existing instanceof List<?> list) {
                List<Object> repeated = new ArrayList<>(list);
                repeated.add(value);
                result.put(entry.key(), repeated);
            } else {
                result.put(entry.key(), new ArrayList<>(List.of(existing, value)));
            }
        }
        return result;
    }

    private static Section copy(Section section, Map<String, Object> fields) {
        return new Section(section.sectionIndex(), section.type(), section.startLine(), section.endLine(),
                section.rawLines(), fields, section.warnings());
    }

    private void omit(int count) {
        if (count > 0) {
            omittedLineCount = Math.addExact(omittedLineCount, count);
            limited = true;
        }
    }

    boolean limited() {
        return limited;
    }

    int omittedLineCount() {
        return enhanced ? omittedLines.size() : omittedLineCount;
    }
}
