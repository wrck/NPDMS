package cn.iocoder.yudao.module.pms.cutover.service.closure;

public final class CutoverClosureApplicationException extends RuntimeException {
    public enum Code {
        INVALID_REQUEST, FUNCTION_OR_SCOPE_DENIED, NOT_FOUND, STATE_CONFLICT,
        TASK_VERSION_STALE, CLOSURE_VERSION_STALE,
        SOURCE_STALE, FILE_INVALID, COLLECTION_INVALID, BUSINESS_INCOMPLETE, IDEMPOTENCY_CONFLICT,
        IDEMPOTENCY_IN_PROGRESS, OWNER_PROVIDER_UNAVAILABLE, OWNER_DATA_CORRUPTED
    }

    public enum Reason {
        REQUEST_SCHEMA_INVALID,
        PROJECT_OR_TASK_SCOPE_DENIED,
        NOT_TASK_OWNER,
        TASK_OR_CLOSURE_NOT_VISIBLE,
        TASK_NOT_IN_P6,
        CLOSURE_ALREADY_SUBMITTED,
        CLOSURE_ARCHIVED,
        TASK_VERSION_STALE,
        CLOSURE_VERSION_STALE,
        APPROVAL_OR_PLAN_STALE,
        PROJECT_SCOPE_STALE,
        DEVICE_SCOPE_STALE,
        FILE_FACT_INVALID,
        REQUIRED_ATTACHMENT_MISSING,
        COLLECTION_EVIDENCE_MISMATCH,
        FAILED_COLLECTION_REQUIRED_FOR_MANUAL_RESULT,
        CLOSURE_RESULT_INCOMPLETE,
        ROLLBACK_DETAIL_INCOMPLETE,
        IDEMPOTENCY_PAYLOAD_CONFLICT,
        IDEMPOTENCY_OPERATION_IN_PROGRESS,
        PROJECT_SCOPE_PROVIDER_UNAVAILABLE,
        PLT_PROVIDER_UNAVAILABLE,
        INT12_PROVIDER_UNAVAILABLE,
        OWNER_FACT_CORRUPTED
    }

    private final Code code;
    private final Reason reason;
    private final String ownerContext;
    private final Integer currentTaskVersion;
    private final Integer currentClosureVersion;

    public CutoverClosureApplicationException(Code code, Reason reason, String ownerContext,
                                              Integer currentTaskVersion, Integer currentClosureVersion,
                                              String message) {
        super(message);
        this.code = code;
        this.reason = reason;
        this.ownerContext = ownerContext;
        this.currentTaskVersion = currentTaskVersion;
        this.currentClosureVersion = currentClosureVersion;
    }

    public Code code() {
        return code;
    }

    public Reason reason() { return reason; }
    public String ownerContext() { return ownerContext; }
    public Integer currentTaskVersion() { return currentTaskVersion; }
    public Integer currentClosureVersion() { return currentClosureVersion; }
}
