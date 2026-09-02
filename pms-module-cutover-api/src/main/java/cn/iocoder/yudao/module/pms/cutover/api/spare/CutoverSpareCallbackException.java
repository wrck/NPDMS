package cn.iocoder.yudao.module.pms.cutover.api.spare;

import java.util.Objects;

/** CutoverSpareCallbackApi的稳定公共失败。 */
public final class CutoverSpareCallbackException extends RuntimeException {

    public enum Code {
        INVALID_REQUEST,
        TENANT_CONTEXT_MISMATCH,
        NOT_VISIBLE_OR_NOT_FOUND,
        IDEMPOTENCY_CONFLICT,
        IDEMPOTENCY_IN_PROGRESS,
        REFERENCE_IDENTITY_CONFLICT,
        STATUS_VERSION_CONFLICT,
        OWNER_DATA_CORRUPTED,
        PROVIDER_UNAVAILABLE
    }

    private final Code code;

    public CutoverSpareCallbackException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public CutoverSpareCallbackException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }
}
