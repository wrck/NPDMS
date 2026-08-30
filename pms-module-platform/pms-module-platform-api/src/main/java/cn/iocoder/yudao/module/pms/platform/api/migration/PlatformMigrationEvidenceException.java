package cn.iocoder.yudao.module.pms.platform.api.migration;

import java.util.Objects;

/** PlatformMigrationEvidenceApi的稳定公共失败。 */
public final class PlatformMigrationEvidenceException extends RuntimeException {

    private final Code code;

    public PlatformMigrationEvidenceException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public PlatformMigrationEvidenceException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        INVALID_REQUEST,
        TENANT_CONTEXT_MISMATCH,
        CALLER_TRANSACTION_REQUIRED,
        IDEMPOTENCY_CONFLICT,
        IDEMPOTENCY_IN_PROGRESS,
        BATCH_NOT_FOUND,
        BATCH_STATE_CONFLICT,
        SOURCE_NOT_FOUND,
        SOURCE_RECORD_CONFLICT,
        SOURCE_ALREADY_CLASSIFIED,
        MAPPING_CONFLICT,
        ISSUE_NOT_FOUND,
        ISSUE_CONFLICT,
        ISSUE_STATE_CONFLICT,
        COUNT_MISMATCH,
        OWNER_DATA_CORRUPTED,
        PROVIDER_UNAVAILABLE
    }
}
