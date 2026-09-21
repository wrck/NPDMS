package com.dp.deviceops.parser.semantic;

import com.dp.deviceops.parser.semantic.internal.ImmutableValues;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic vendor-neutral structure extracted from command evidence. */
public record GenericContent(List<Unit> units) {

    public GenericContent {
        units = List.copyOf(Objects.requireNonNull(units, "units"));
    }

    public enum StructureStatus {
        STRUCTURED,
        PARTIAL,
        TEXT_ONLY,
        EMPTY,
        LIMITED
    }

    public record Unit(
            int commandIndex,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer parentCommandIndex,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer sectionIndex,
            int nestingDepth,
            String commandText,
            int sourceLineStart,
            int sourceLineEnd,
            StructureStatus structureStatus,
            List<Section> sections,
            List<Warning> warnings,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer omittedLineCount) {

        public Unit {
            if (commandIndex < 1) {
                throw new IllegalArgumentException("commandIndex must be positive");
            }
            if (nestingDepth < 0 || nestingDepth > 1) {
                throw new IllegalArgumentException("nestingDepth must be zero or one");
            }
            if (nestingDepth == 0 && (parentCommandIndex != null || sectionIndex != null)) {
                throw new IllegalArgumentException("top-level unit must not contain parent identity");
            }
            if (nestingDepth == 1 && (parentCommandIndex == null || parentCommandIndex < 1
                    || sectionIndex == null || sectionIndex < 1
                    || commandIndex != parentCommandIndex)) {
                throw new IllegalArgumentException("nested unit requires matching parent identity");
            }
            commandText = requireText(commandText, "commandText");
            requireLineRange(sourceLineStart, sourceLineEnd);
            Objects.requireNonNull(structureStatus, "structureStatus");
            sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
            if (omittedLineCount != null && omittedLineCount < 1) {
                throw new IllegalArgumentException("omittedLineCount must be positive when present");
            }
        }
    }

    public record Section(
            int sectionIndex,
            String type,
            int startLine,
            int endLine,
            List<String> rawLines,
            @JsonIgnore Map<String, Object> fields,
            List<Warning> warnings) {

        private static final Set<String> RESERVED_FIELDS = Set.of(
                "sectionIndex", "type", "startLine", "endLine", "rawLines", "warnings");

        public Section {
            if (sectionIndex < 1) {
                throw new IllegalArgumentException("sectionIndex must be positive");
            }
            type = requireText(type, "type");
            requireLineRange(startLine, endLine);
            rawLines = List.copyOf(Objects.requireNonNull(rawLines, "rawLines"));
            fields = ImmutableValues.deepImmutableMap(fields);
            if (fields.keySet().stream().anyMatch(RESERVED_FIELDS::contains)) {
                throw new IllegalArgumentException("section payload must not replace common fields");
            }
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        }

        @JsonAnyGetter
        public Map<String, Object> flattenedFields() {
            return fields;
        }
    }

    public record Warning(String code, int lineNumber) {

        public Warning {
            code = requireText(code, "code");
            if (lineNumber < 1) {
                throw new IllegalArgumentException("lineNumber must be positive");
            }
        }
    }

    public record KeyValueEntry(
            String key,
            String value,
            int startLine,
            int endLine,
            List<KeyValueEntry> children) {

        public KeyValueEntry {
            key = requireText(key, "key");
            value = Objects.requireNonNull(value, "value");
            requireLineRange(startLine, endLine);
            children = List.copyOf(Objects.requireNonNull(children, "children"));
        }
    }

    public record TableColumn(String id, String label, int index) {

        public TableColumn {
            id = requireText(id, "id");
            if (index < 0) {
                throw new IllegalArgumentException("table column index must not be negative");
            }
        }
    }

    public record TableRow(int rowIndex, Map<String, String> values) {

        public TableRow {
            if (rowIndex < 1) {
                throw new IllegalArgumentException("table row index must be positive");
            }
            values = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(values, "values")));
        }
    }

    public record UnparsedLine(int lineNumber, String value) {

        public UnparsedLine {
            if (lineNumber < 1) {
                throw new IllegalArgumentException("lineNumber must be positive");
            }
            value = Objects.requireNonNull(value, "value");
        }
    }

    public record RecordValue(
            int recordIndex,
            String identity,
            int startLine,
            int endLine,
            List<KeyValueEntry> entries,
            List<Section> sections) {

        public RecordValue {
            if (recordIndex < 1) {
                throw new IllegalArgumentException("recordIndex must be positive");
            }
            requireLineRange(startLine, endLine);
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        }
    }

    public record ConfigStanza(
            String header,
            int startLine,
            int endLine,
            List<String> lines) {

        public ConfigStanza {
            header = requireText(header, "header");
            requireLineRange(startLine, endLine);
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        }
    }

    public record ListItem(int itemIndex, String value, int startLine, int endLine) {

        public ListItem {
            if (itemIndex < 1) {
                throw new IllegalArgumentException("itemIndex must be positive");
            }
            value = requireText(value, "value");
            requireLineRange(startLine, endLine);
        }
    }

    public record TextLine(int lineNumber, String value) {

        public TextLine {
            if (lineNumber < 1) {
                throw new IllegalArgumentException("lineNumber must be positive");
            }
            value = Objects.requireNonNull(value, "value");
        }
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }

    private static void requireLineRange(int startLine, int endLine) {
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException("source line range is invalid");
        }
    }
}
