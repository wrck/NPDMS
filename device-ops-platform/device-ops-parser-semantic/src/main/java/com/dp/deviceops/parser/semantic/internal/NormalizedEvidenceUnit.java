package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record NormalizedEvidenceUnit(
        int unitIndex,
        String unitType,
        Map<String, String> attributes,
        List<String> contentLines,
        List<String> errorLines,
        boolean truncated,
        @JsonIgnore
        EvidenceProvenance provenance) {

    public NormalizedEvidenceUnit(
            int unitIndex,
            String unitType,
            Map<String, String> attributes,
            List<String> contentLines,
            List<String> errorLines,
            boolean truncated) {
        this(unitIndex, unitType, attributes, contentLines, errorLines, truncated,
                EvidenceProvenance.topLevel(unitIndex, attributes.getOrDefault("commandText", "")));
    }

    public NormalizedEvidenceUnit {
        if (unitIndex < 1) {
            throw new IllegalArgumentException("unitIndex must be positive");
        }
        unitType = Objects.requireNonNull(unitType, "unitType");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        contentLines = List.copyOf(Objects.requireNonNull(contentLines, "contentLines"));
        errorLines = List.copyOf(Objects.requireNonNull(errorLines, "errorLines"));
        provenance = Objects.requireNonNull(provenance, "provenance");
    }

    public int commandIndex() {
        return unitIndex;
    }

    public String commandText() {
        return attributes.getOrDefault("commandText", "");
    }

    public CommandBlockStatus status() {
        String value = attributes.getOrDefault("status", CommandBlockStatus.SUCCEEDED.name());
        try {
            return CommandBlockStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new SemanticParserError(SemanticParserError.INVALID_INPUT,
                    "evidence status is invalid", exception);
        }
    }

    public List<String> stdoutLines() {
        return contentLines;
    }

    public List<String> stderrLines() {
        return errorLines;
    }

    public long receivedBytes() {
        return longAttribute("receivedBytes", 0L);
    }

    public int pageCount() {
        return Math.toIntExact(longAttribute("pageCount", 0L));
    }

    public Integer exitCode() {
        String value = attributes.get("exitCode");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new SemanticParserError(SemanticParserError.INVALID_INPUT,
                    "evidence exitCode is invalid", exception);
        }
    }

    private long longAttribute(String name, long defaultValue) {
        String value = attributes.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new SemanticParserError(SemanticParserError.INVALID_INPUT,
                    "evidence numeric attribute is invalid", exception);
        }
    }
}

record EvidenceProvenance(
        int commandIndex,
        String commandText,
        int nestingDepth,
        String nestedCommandText,
        Integer sectionIndex,
        int lineOffset) {

    EvidenceProvenance {
        if (commandIndex < 1) {
            throw new IllegalArgumentException("commandIndex must be positive");
        }
        commandText = Objects.requireNonNull(commandText, "commandText");
        if (nestingDepth < 0 || nestingDepth > 1) {
            throw new IllegalArgumentException("nestingDepth must be zero or one");
        }
    }

    static EvidenceProvenance topLevel(int commandIndex, String commandText) {
        return new EvidenceProvenance(commandIndex, commandText, 0, null, null, 0);
    }

    int absoluteLine(int localLine) {
        return Math.addExact(lineOffset, localLine);
    }
}
