package cn.iocoder.yudao.module.pms.cutover.service.plan.port;

public final class CutoverPlanOwnerFactException extends RuntimeException {
    public enum Code { SOURCE_STALE, ASSESSMENT_STALE, CHECKLIST_STALE, PROJECT_OR_DEVICE_STALE,
        CONFIGURATION_OR_TEMPLATE_STALE, FILE_INVALID, PROVIDER_UNAVAILABLE, OWNER_DATA_CORRUPTED }

    private final Code code;

    public CutoverPlanOwnerFactException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() { return code; }
}
