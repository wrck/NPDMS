package cn.iocoder.yudao.module.pms.cutover.service.taskv2;

public final class CutoverTaskApplicationException extends RuntimeException {

    public enum Code {
        INVALID_REQUEST,
        NOT_FOUND,
        DATA_SCOPE_FORBIDDEN,
        STATE_CONFLICT,
        VERSION_CONFLICT,
        CONFIGURATION_CONFLICT,
        ACTIVE_DEVICE_CONFLICT,
        PROJECT_SCOPE_STALE,
        PROJECT_CONTEXT_STALE,
        DEVICE_SCOPE_STALE,
        CUSTOMER_SERVICE_LEVEL_STALE,
        IMPLEMENTATION_READINESS_STALE,
        READINESS_NOT_READY,
        CUSTOMER_CONTEXT_INVALID,
        BUSINESS_GATE_INVALID,
        PROJ_PROVIDER_UNAVAILABLE,
        AST_PROVIDER_UNAVAILABLE,
        CUS_PROVIDER_UNAVAILABLE,
        IMP_PROVIDER_UNAVAILABLE,
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
