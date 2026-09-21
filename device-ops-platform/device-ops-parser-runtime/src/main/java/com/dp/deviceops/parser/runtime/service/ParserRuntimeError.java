package com.dp.deviceops.parser.runtime.service;

import java.util.Objects;

public final class ParserRuntimeError extends RuntimeException {

    private final String code;

    public ParserRuntimeError(String code) {
        this(code, code, null);
    }

    public ParserRuntimeError(String code, String message) {
        this(code, message, null);
    }

    public ParserRuntimeError(String code, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public String code() {
        return code;
    }
}
