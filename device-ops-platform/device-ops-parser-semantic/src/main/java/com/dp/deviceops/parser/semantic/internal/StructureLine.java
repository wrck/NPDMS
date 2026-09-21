package com.dp.deviceops.parser.semantic.internal;

import java.util.Objects;

public record StructureLine(int lineNumber, String text) {

    public StructureLine {
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be positive");
        }
        text = Objects.requireNonNull(text, "text");
    }

    public int indentationColumns() {
        int columns = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == ' ') {
                columns++;
            } else if (current == '\t') {
                columns += 4 - columns % 4;
            } else {
                break;
            }
        }
        return columns;
    }

    public String content() {
        return text.stripLeading();
    }
}
