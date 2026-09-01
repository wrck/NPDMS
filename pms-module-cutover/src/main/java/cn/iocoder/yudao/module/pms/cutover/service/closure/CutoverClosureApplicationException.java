package cn.iocoder.yudao.module.pms.cutover.service.closure;

public final class CutoverClosureApplicationException extends RuntimeException {
    public enum Code {
        INVALID_REQUEST, NOT_FOUND, STATE_CONFLICT, TASK_VERSION_STALE, CLOSURE_VERSION_STALE,
        SOURCE_STALE, FILE_INVALID, BUSINESS_INCOMPLETE, IDEMPOTENCY_CONFLICT,
        IDEMPOTENCY_IN_PROGRESS, OWNER_PROVIDER_UNAVAILABLE, OWNER_DATA_CORRUPTED
    }

    private final Code code;

    public CutoverClosureApplicationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
