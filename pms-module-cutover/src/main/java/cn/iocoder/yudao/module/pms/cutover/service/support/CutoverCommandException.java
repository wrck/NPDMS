package cn.iocoder.yudao.module.pms.cutover.service.support;

public final class CutoverCommandException extends RuntimeException {
    public enum Reason { INVALID_INTENT, IDEMPOTENCY_CONFLICT, IDEMPOTENCY_IN_PROGRESS }
    private final Reason reason;
    public CutoverCommandException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
