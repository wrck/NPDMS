package cn.iocoder.yudao.module.pms.cutover.service.plan;

public final class CutoverPlanApplicationException extends RuntimeException {
    public enum Code { INVALID_REQUEST, NOT_FOUND, STATE_CONFLICT, VERSION_CONFLICT,
        PROJECT_SCOPE_STALE, SOURCE_STALE, FILE_FACT_STALE, IDEMPOTENCY_CONFLICT, IDEMPOTENCY_IN_PROGRESS }

    private final Code code;

    public CutoverPlanApplicationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() { return code; }
}
