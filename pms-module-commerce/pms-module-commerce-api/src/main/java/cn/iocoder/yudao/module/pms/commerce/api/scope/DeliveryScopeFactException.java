package cn.iocoder.yudao.module.pms.commerce.api.scope;

import java.util.Objects;

/** COM当前已分配范围事实的稳定公共失败。 */
public final class DeliveryScopeFactException extends RuntimeException {

    private final Code code;

    public DeliveryScopeFactException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public DeliveryScopeFactException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        INVALID_REQUEST,
        TENANT_CONTEXT_MISMATCH,
        PROJECT_NOT_VISIBLE_OR_INELIGIBLE,
        SCOPE_STALE,
        SCOPE_CONFLICT,
        OWNER_DATA_CORRUPTED,
        PROVIDER_UNAVAILABLE
    }
}
