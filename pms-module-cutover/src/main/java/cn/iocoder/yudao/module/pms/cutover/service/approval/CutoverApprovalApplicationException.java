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
    private final String reasonCode;
    private final String ownerContext;
    private final Long currentApprovalVersion;
    private final Long currentTaskVersion;

    public CutoverApprovalApplicationException(Code code, String message) {
        this(code, defaultReason(code), null, null, null, message);
    }

    public CutoverApprovalApplicationException(Code code, String reasonCode, String ownerContext,
            Long currentApprovalVersion, Long currentTaskVersion, String message) {
        super(message);
        this.code = code;
        this.reasonCode = reasonCode;
        this.ownerContext = ownerContext;
        this.currentApprovalVersion = currentApprovalVersion;
        this.currentTaskVersion = currentTaskVersion;
    }

    public Code code() {
        return code;
    }

    public String reasonCode() { return reasonCode; }
    public String ownerContext() { return ownerContext; }
    public Long currentApprovalVersion() { return currentApprovalVersion; }
    public Long currentTaskVersion() { return currentTaskVersion; }

    private static String defaultReason(Code code) {
        return switch (code) {
            case INVALID_REQUEST -> "REQUEST_SCHEMA_INVALID";
            case STATE_CONFLICT -> "APPROVAL_NOT_PENDING";
            case VERSION_CONFLICT -> "APPROVAL_VERSION_STALE";
            case IDEMPOTENCY_CONFLICT -> "IDEMPOTENCY_PAYLOAD_CONFLICT";
            case IDEMPOTENCY_IN_PROGRESS -> "IDEMPOTENCY_OPERATION_IN_PROGRESS";
            case SOURCE_STALE -> "APPROVER_FACT_STALE";
            case BUSINESS_INCOMPLETE -> "DECISION_ACTION_RESULT_MISMATCH";
            case OWNER_PROVIDER_UNAVAILABLE -> "PROJ_OR_SYSTEM_PROVIDER_UNAVAILABLE";
            case OWNER_DATA_CORRUPTED -> "OWNER_FACT_CORRUPTED";
        };
    }
}
