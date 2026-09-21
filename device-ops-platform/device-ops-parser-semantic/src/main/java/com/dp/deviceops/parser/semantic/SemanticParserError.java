package com.dp.deviceops.parser.semantic;

import java.util.Objects;

/** Stable and safe parser error that never carries source content. */
public final class SemanticParserError extends RuntimeException {

    public static final String INVALID_INPUT = "INVALID_INPUT";
    public static final String INPUT_UNAVAILABLE = "INPUT_UNAVAILABLE";
    public static final String INVALID_RULES = "INVALID_RULES";
    public static final String INVALID_PROJECTIONS = "INVALID_PROJECTIONS";
    public static final String RESOURCE_LIMIT = "RESOURCE_LIMIT";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final String code;

    public SemanticParserError(String code, String safeMessage) {
        super(Objects.requireNonNull(safeMessage, "safeMessage"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public SemanticParserError(String code, String safeMessage, Throwable cause) {
        super(Objects.requireNonNull(safeMessage, "safeMessage"), cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public String code() {
        return code;
    }
}
