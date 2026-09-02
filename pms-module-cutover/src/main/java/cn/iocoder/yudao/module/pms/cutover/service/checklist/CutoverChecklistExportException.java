package cn.iocoder.yudao.module.pms.cutover.service.checklist;

public final class CutoverChecklistExportException extends RuntimeException {

    private final Code code;

    public CutoverChecklistExportException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public CutoverChecklistExportException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        NOT_VISIBLE_OR_NOT_FOUND,
        CHECKLIST_VERSION_STALE,
        OWNER_FACT_STALE,
        INVALID_EXPORT_REQUEST,
        EXPORT_PROJECTION_INVALID,
        OWNER_PROVIDER_UNAVAILABLE
    }
}
