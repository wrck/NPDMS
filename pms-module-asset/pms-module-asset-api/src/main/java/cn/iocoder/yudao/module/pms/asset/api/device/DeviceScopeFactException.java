package cn.iocoder.yudao.module.pms.asset.api.device;

import java.util.Objects;

/** DeviceScopeFactApi的稳定公共失败；不包含消费方内部异常类型。 */
public final class DeviceScopeFactException extends RuntimeException {

    private final Code code;

    public DeviceScopeFactException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public DeviceScopeFactException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        INVALID_REQUEST,
        DUPLICATE_SERIAL,
        TENANT_CONTEXT_MISMATCH,
        PROVIDER_UNAVAILABLE,
        OWNER_DATA_CORRUPTED
    }
}
