package cn.iocoder.yudao.module.pms.cutover.service.approval.port;

public final class CutoverApprovalOwnerFactException extends RuntimeException {

    public enum Code {
        INVALID_REQUEST,
        TENANT_CONTEXT_MISMATCH,
        OWNER_DATA_CORRUPTED,
        PROVIDER_UNAVAILABLE
    }

    private final Code code;
    private final String ownerContext;

    public CutoverApprovalOwnerFactException(Code code, String message) {
        this(code, null, message, null);
    }

    public CutoverApprovalOwnerFactException(Code code, String message, Throwable cause) {
        this(code, null, message, cause);
    }

    public CutoverApprovalOwnerFactException(Code code, String ownerContext, String message) {
        this(code, ownerContext, message, null);
    }

    public CutoverApprovalOwnerFactException(Code code, String ownerContext, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        if (ownerContext != null && !"PROJ".equals(ownerContext) && !"SYSTEM".equals(ownerContext)) {
            throw new IllegalArgumentException("invalid approval owner context");
        }
        this.ownerContext = ownerContext;
    }

    public Code code() {
        return code;
    }

    public String ownerContext() { return ownerContext; }
}
