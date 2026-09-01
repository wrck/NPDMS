package cn.iocoder.yudao.module.pms.cutover.service.plan;

public final class CutoverPlanApplicationException extends RuntimeException {
    public enum Code { INVALID_REQUEST, NOT_FOUND, STATE_CONFLICT, VERSION_CONFLICT, TASK_VERSION_STALE,
        PROJECT_SCOPE_STALE, ASSESSMENT_STALE, CHECKLIST_STALE, PROJECT_OR_DEVICE_STALE,
        CONFIGURATION_OR_TEMPLATE_STALE, FILE_FACT_STALE, OWNER_PROVIDER_UNAVAILABLE, OWNER_DATA_CORRUPTED,
        PLAN_SECTION_INCOMPLETE, RISK_MITIGATION_INCOMPLETE, SUPPORT_ARRANGEMENT_INCOMPLETE,
        IDEMPOTENCY_CONFLICT, IDEMPOTENCY_IN_PROGRESS }

    private final Code code;
    private final String reasonCode;
    private final String ownerContext;
    private final Integer currentTaskVersion;
    private final Integer currentPlanVersion;
    private final Integer currentApprovalVersion;

    public CutoverPlanApplicationException(Code code, String message) {
        this(code, defaultReason(code), defaultOwner(code), null, null, null, message);
    }

    public CutoverPlanApplicationException(Code code, String reasonCode, String ownerContext,
                                           Integer currentTaskVersion, Integer currentPlanVersion,
                                           Integer currentApprovalVersion, String message) {
        super(message);
        this.code = code;
        this.reasonCode = reasonCode;
        this.ownerContext = ownerContext;
        this.currentTaskVersion = currentTaskVersion;
        this.currentPlanVersion = currentPlanVersion;
        this.currentApprovalVersion = currentApprovalVersion;
    }

    public Code code() { return code; }
    public String reasonCode() { return reasonCode; }
    public String ownerContext() { return ownerContext; }
    public Integer currentTaskVersion() { return currentTaskVersion; }
    public Integer currentPlanVersion() { return currentPlanVersion; }
    public Integer currentApprovalVersion() { return currentApprovalVersion; }

    private static String defaultReason(Code code) {
        return switch (code) {
            case INVALID_REQUEST -> "REQUEST_SCHEMA_INVALID";
            case NOT_FOUND -> "TASK_OR_PLAN_NOT_VISIBLE";
            case STATE_CONFLICT -> "PLAN_NOT_EDITABLE";
            case VERSION_CONFLICT -> "PLAN_VERSION_STALE";
            case TASK_VERSION_STALE -> "TASK_VERSION_STALE";
            case PROJECT_SCOPE_STALE, PROJECT_OR_DEVICE_STALE -> "PROJECT_OR_DEVICE_STALE";
            case ASSESSMENT_STALE -> "ASSESSMENT_STALE";
            case CHECKLIST_STALE -> "CHECKLIST_STALE";
            case CONFIGURATION_OR_TEMPLATE_STALE -> "CONFIGURATION_OR_TEMPLATE_STALE";
            case FILE_FACT_STALE -> "FILE_FACT_INVALID";
            case PLAN_SECTION_INCOMPLETE -> "PLAN_SECTION_INCOMPLETE";
            case RISK_MITIGATION_INCOMPLETE -> "RISK_MITIGATION_INCOMPLETE";
            case SUPPORT_ARRANGEMENT_INCOMPLETE -> "SUPPORT_ARRANGEMENT_INCOMPLETE";
            case IDEMPOTENCY_CONFLICT -> "IDEMPOTENCY_PAYLOAD_CONFLICT";
            case IDEMPOTENCY_IN_PROGRESS -> "IDEMPOTENCY_OPERATION_IN_PROGRESS";
            case OWNER_PROVIDER_UNAVAILABLE -> "SOURCE_PROVIDER_UNAVAILABLE";
            case OWNER_DATA_CORRUPTED -> "OWNER_FACT_CORRUPTED";
        };
    }

    private static String defaultOwner(Code code) {
        return switch (code) {
            case FILE_FACT_STALE -> "PLT";
            case PROJECT_SCOPE_STALE, ASSESSMENT_STALE, CHECKLIST_STALE,
                 PROJECT_OR_DEVICE_STALE, CONFIGURATION_OR_TEMPLATE_STALE,
                 OWNER_PROVIDER_UNAVAILABLE, OWNER_DATA_CORRUPTED -> "CUT";
            default -> null;
        };
    }
}
