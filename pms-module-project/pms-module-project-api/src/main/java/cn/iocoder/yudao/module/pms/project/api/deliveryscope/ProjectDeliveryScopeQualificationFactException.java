package cn.iocoder.yudao.module.pms.project.api.deliveryscope;

import java.util.Objects;

/** ProjectDeliveryScopeQualificationFactApi的稳定公共失败。 */
public final class ProjectDeliveryScopeQualificationFactException extends RuntimeException {

    private final Code code;

    public ProjectDeliveryScopeQualificationFactException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ProjectDeliveryScopeQualificationFactException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        INVALID_REQUEST,
        TENANT_CONTEXT_MISMATCH,
        SUBJECT_NOT_ELIGIBLE,
        DATA_SCOPE_DENIED,
        FACT_STALE,
        OWNER_DATA_CORRUPTED,
        PROVIDER_UNAVAILABLE
    }
}
