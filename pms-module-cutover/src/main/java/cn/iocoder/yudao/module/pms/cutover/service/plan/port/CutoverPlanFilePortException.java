package cn.iocoder.yudao.module.pms.cutover.service.plan.port;

public final class CutoverPlanFilePortException extends RuntimeException {
    public enum Code { PROVIDER_UNAVAILABLE, OWNER_DATA_CORRUPTED }

    private final Code code;

    public CutoverPlanFilePortException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
