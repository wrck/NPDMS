package cn.iocoder.yudao.module.pms.cutover.service.taskv2.port;

/** CUT 编排层对跨模块事实失败的稳定内部分类。 */
public final class CutoverOwnerFactException extends RuntimeException {

    public enum Code {
        INVALID_FACT,
        DATA_SCOPE_FORBIDDEN,
        STALE,
        PROVIDER_UNAVAILABLE
    }

    private final Code code;

    public CutoverOwnerFactException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public CutoverOwnerFactException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
