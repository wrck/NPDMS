package cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness;

import java.util.Objects;

/** ImplementationReadinessApi的稳定公共失败。 */
public final class ImplementationReadinessException extends RuntimeException {

    private final Code code;

    public ImplementationReadinessException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ImplementationReadinessException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        INVALID_REQUEST,
        DUPLICATE_DEVICE,
        TENANT_CONTEXT_MISMATCH,
        SNAPSHOT_NOT_FOUND,
        OWNER_DATA_CORRUPTED,
        PROVIDER_UNAVAILABLE
    }
}
