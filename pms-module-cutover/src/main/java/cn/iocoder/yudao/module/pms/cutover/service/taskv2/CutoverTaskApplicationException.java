package cn.iocoder.yudao.module.pms.cutover.service.taskv2;

public final class CutoverTaskApplicationException extends RuntimeException {

    public enum Code {
        INVALID_REQUEST,
        NOT_FOUND,
        DATA_SCOPE_FORBIDDEN,
        STATE_CONFLICT,
        VERSION_CONFLICT,
        ACTIVE_DEVICE_CONFLICT,
        READINESS_NOT_READY,
        CUSTOMER_CONTEXT_INVALID,
        OWNER_PROVIDER_UNAVAILABLE,
        IDEMPOTENCY_CONFLICT,
        IDEMPOTENCY_IN_PROGRESS
    }

    private final Code code;

    public CutoverTaskApplicationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
