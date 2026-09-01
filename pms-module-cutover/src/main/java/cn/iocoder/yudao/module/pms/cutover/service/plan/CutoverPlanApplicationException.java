package cn.iocoder.yudao.module.pms.cutover.service.plan;

public final class CutoverPlanApplicationException extends RuntimeException {
    public enum Code { INVALID_REQUEST, NOT_FOUND, STATE_CONFLICT, VERSION_CONFLICT, TASK_VERSION_STALE,
        PROJECT_SCOPE_STALE, ASSESSMENT_STALE, CHECKLIST_STALE, PROJECT_OR_DEVICE_STALE,
        CONFIGURATION_OR_TEMPLATE_STALE, FILE_FACT_STALE, OWNER_PROVIDER_UNAVAILABLE, OWNER_DATA_CORRUPTED,
        PLAN_SECTION_INCOMPLETE, RISK_MITIGATION_INCOMPLETE, SUPPORT_ARRANGEMENT_INCOMPLETE,
        IDEMPOTENCY_CONFLICT, IDEMPOTENCY_IN_PROGRESS }

    private final Code code;

    public CutoverPlanApplicationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() { return code; }
}
