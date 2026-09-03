package cn.iocoder.yudao.module.pms.cutover.service.checklist;

public final class CutoverChecklistException extends RuntimeException {

    private final Code code;

    public CutoverChecklistException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        INVALID_REQUEST,
        FROZEN_CONFIGURATION_NOT_FOUND,
        FROZEN_CONFIGURATION_INVALID,
        NOT_FOUND,
        DATA_SCOPE_FORBIDDEN,
        STATE_CONFLICT,
        VERSION_CONFLICT,
        IDEMPOTENCY_CONFLICT,
        IDEMPOTENCY_IN_PROGRESS,
        FILE_FACT_INVALID,
        COLLECTION_FACT_INVALID
    }
}
