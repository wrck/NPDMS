package cn.iocoder.yudao.module.pms.lowcode.enums;

/**
 * 低代码模块业务异常。
 *
 * <p>迁移自源工程 com.dp.plat.common.exception.LowCodeException 的 message-only 语义：
 * 固定使用模块通用错误码 {@link ErrorCodeConstants#LOWCODE_ERROR}，消息动态传入。
 * yudao {@code ServiceException} 为 final 类不可继承，故直接继承 {@code RuntimeException}
 * 并保留 {@code code} 字段（与源工程兼容层 BusinessException 的做法一致）。</p>
 */
public class LowCodeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Integer code;

    public LowCodeException(String message) {
        super(message);
        this.code = ErrorCodeConstants.LOWCODE_ERROR.getCode();
    }

    public LowCodeException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCodeConstants.LOWCODE_ERROR.getCode();
    }

    public Integer getCode() {
        return code;
    }
}
