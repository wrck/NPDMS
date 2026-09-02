package cn.iocoder.yudao.module.pms.cutover.api.approval;

import java.util.Objects;

/** CutoverApprovalFactApi的稳定公共失败。 */
public final class CutoverApprovalFactException extends RuntimeException {

    public enum Code {
        INVALID_REQUEST,
        TENANT_CONTEXT_MISMATCH,
        NOT_FOUND,
        STATE_CONFLICT,
        VERSION_CONFLICT,
        IDEMPOTENCY_CONFLICT,
        IDEMPOTENCY_IN_PROGRESS,
        OWNER_DATA_CORRUPTED,
        PROVIDER_UNAVAILABLE
    }

    private final Code code;

    public CutoverApprovalFactException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public CutoverApprovalFactException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }
}
