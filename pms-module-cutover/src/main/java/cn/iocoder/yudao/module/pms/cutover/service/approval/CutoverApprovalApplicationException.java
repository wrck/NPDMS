package cn.iocoder.yudao.module.pms.cutover.service.approval;

public final class CutoverApprovalApplicationException extends RuntimeException {

    public enum Code {
        INVALID_REQUEST,
        STATE_CONFLICT,
        VERSION_CONFLICT,
        IDEMPOTENCY_CONFLICT,
        IDEMPOTENCY_IN_PROGRESS,
        SOURCE_STALE,
        BUSINESS_INCOMPLETE,
        OWNER_PROVIDER_UNAVAILABLE,
        OWNER_DATA_CORRUPTED
    }

    private final Code code;

    public CutoverApprovalApplicationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
