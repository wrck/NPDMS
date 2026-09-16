package cn.iocoder.yudao.module.pms.workflow.exception;

/**
 * 业务异常（迁移自源工程 pms-common com.dp.plat.common.exception.BusinessException）。
 *
 * <p>yudao {@code ServiceException} 是 {@code final} 类不可继承，
 * 因此本异常直接继承 {@code RuntimeException} 并保留 {@code code} 字段
 * （默认 1001，与源工程 BUSINESS_ERROR 语义一致）。</p>
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 1001;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = 1001;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public int getCode() {
        return code;
    }
}