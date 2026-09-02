package cn.iocoder.yudao.module.pms.cutover.service.closure.port;

/** CUT闭环消费跨模块事实时使用的稳定失败分类。 */
public final class CutoverClosureOwnerFactException extends RuntimeException {

    public enum Code {
        INVALID_REQUEST,
        FILE_INVALID,
        COLLECTION_INVALID,
        IDEMPOTENCY_CONFLICT,
        SOURCE_STALE,
        PROVIDER_UNAVAILABLE,
        OWNER_DATA_CORRUPTED
    }

    private final Code code;

    public CutoverClosureOwnerFactException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public CutoverClosureOwnerFactException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
