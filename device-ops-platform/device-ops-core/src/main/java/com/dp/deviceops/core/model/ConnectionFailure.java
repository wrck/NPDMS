package com.dp.deviceops.core.model;

import java.util.Objects;

public final class ConnectionFailure extends RuntimeException {

    private final Code code;
    private final Stage stage;
    private final String safeMessage;

    public ConnectionFailure(Code code, Stage stage, String safeMessage) {
        this(code, stage, safeMessage, null);
    }

    public ConnectionFailure(Code code, Stage stage, String safeMessage, Throwable cause) {
        super(requireMessage(safeMessage), cause);
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
        this.safeMessage = safeMessage.strip();
    }

    public Code code() {
        return code;
    }

    public Stage stage() {
        return stage;
    }

    public String safeMessage() {
        return safeMessage;
    }

    private static String requireMessage(String value) {
        Objects.requireNonNull(value, "safeMessage must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("safeMessage must not be blank");
        }
        return value.strip();
    }

    public enum Code {
        UNREACHABLE,
        CONNECT_TIMEOUT,
        AUTH_FAILED,
        HOST_KEY_MISMATCH,
        PROMPT_NOT_FOUND,
        PROTOCOL_DISABLED,
        EXECUTION_TIMEOUT,
        CONNECTION_CLOSED,
        CONNECTION_BUSY_TIMEOUT
    }

    public enum Stage {
        RESOLVE,
        CONNECT,
        VERIFY_HOST,
        AUTHENTICATE,
        LOGIN,
        EXECUTE
    }
}
