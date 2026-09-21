package com.dp.deviceops.parser.runtime.model;

import java.util.Objects;

final class ModelSupport {

    private ModelSupport() {
    }

    static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return text;
    }

    static String optionalText(String value, String name) {
        return value == null ? null : requireText(value, name);
    }
}
