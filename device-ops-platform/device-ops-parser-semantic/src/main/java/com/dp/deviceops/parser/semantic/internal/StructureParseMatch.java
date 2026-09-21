package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent;

import java.util.Objects;

public record StructureParseMatch(GenericContent.Section section, int nextOffset) {

    public StructureParseMatch {
        Objects.requireNonNull(section, "section");
        if (nextOffset < 1) {
            throw new IllegalArgumentException("nextOffset must be positive");
        }
    }
}
