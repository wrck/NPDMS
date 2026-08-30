package cn.iocoder.yudao.module.pms.commerce.service.scope;

/** Task 7将这些稳定分类映射到COM REST错误合同。 */
public class CommerceDeliveryScopeCommandException extends RuntimeException {

    private final Code code;

    public CommerceDeliveryScopeCommandException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public CommerceDeliveryScopeCommandException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        INVALID_REQUEST,
        TENANT_CONTEXT_MISMATCH,
        PROJECT_SUBJECT_NOT_ELIGIBLE,
        PROJECT_DATA_SCOPE_DENIED,
        PROJECT_FACT_STALE,
        OWNER_PROVIDER_UNAVAILABLE,
        ORDER_LINE_NOT_QUALIFIED,
        ORDER_LINE_SOURCE_STALE,
        OVER_ALLOCATION,
        DEVICE_SCOPE_INVALID,
        LOCATION_INVALID,
        SCOPE_STALE,
        STATE_CONFLICT,
        IDEMPOTENCY_CONFLICT,
        IDEMPOTENCY_IN_PROGRESS,
        OWNER_DATA_CORRUPTED
    }
}
