package cn.iocoder.yudao.module.pms.customer.api.servicelevel;

public final class CustomerServiceLevelFactException extends RuntimeException {

    private final Code code;

    public CustomerServiceLevelFactException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public CustomerServiceLevelFactException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        INVALID_REQUEST,
        TENANT_CONTEXT_MISMATCH,
        CUSTOMER_NOT_FOUND,
        OWNER_DATA_CORRUPTED,
        PROVIDER_UNAVAILABLE
    }
}
