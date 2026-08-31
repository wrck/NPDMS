package cn.iocoder.yudao.module.pms.cutover.api.task;

import java.util.Objects;

/** CutoverTaskIntakeApi的稳定公共失败。 */
public final class CutoverTaskIntakeException extends RuntimeException {

    public enum Code {
        INVALID_REQUEST,
        SOURCE_IDENTITY_CONFLICT,
        CONFIGURATION_CONFLICT,
        DATA_SCOPE_FORBIDDEN,
        ACTIVE_DEVICE_CONFLICT,
        READINESS_NOT_READY,
        CUSTOMER_CONTEXT_INVALID,
        OWNER_PROVIDER_UNAVAILABLE
    }

    private final Code code;

    public CutoverTaskIntakeException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public CutoverTaskIntakeException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }
}
