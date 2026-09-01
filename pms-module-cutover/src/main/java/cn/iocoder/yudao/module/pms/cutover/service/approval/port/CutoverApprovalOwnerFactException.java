package cn.iocoder.yudao.module.pms.cutover.service.approval.port;

public final class CutoverApprovalOwnerFactException extends RuntimeException {

    public enum Code {
        INVALID_REQUEST,
        TENANT_CONTEXT_MISMATCH,
        OWNER_DATA_CORRUPTED,
        PROVIDER_UNAVAILABLE
    }

    private final Code code;

    public CutoverApprovalOwnerFactException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public CutoverApprovalOwnerFactException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
