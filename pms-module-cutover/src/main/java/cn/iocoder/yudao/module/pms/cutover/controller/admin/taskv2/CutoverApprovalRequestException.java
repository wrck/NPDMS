package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

public final class CutoverApprovalRequestException extends IllegalArgumentException {
    public enum Reason { REQUEST_SCHEMA_INVALID, HEADER_REQUIRED_OR_INVALID }

    private final Reason reason;

    public CutoverApprovalRequestException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
