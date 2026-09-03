package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port;

/** Owner事实与调用方期望版本不一致；调用方可重新读取当前事实并返回STALE。 */
public class OwnerFactVersionMismatchException extends RuntimeException {

    private final String ownerContext;
    private final String reasonCode;

    public OwnerFactVersionMismatchException(String message) {
        this(null, null, message);
    }

    public OwnerFactVersionMismatchException(String ownerContext, String reasonCode, String message) {
        super(message);
        this.ownerContext = ownerContext;
        this.reasonCode = reasonCode;
    }

    public String ownerContext() { return ownerContext; }
    public String reasonCode() { return reasonCode; }
}
