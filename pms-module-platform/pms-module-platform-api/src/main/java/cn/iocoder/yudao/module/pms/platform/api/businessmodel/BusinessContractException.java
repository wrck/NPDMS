package cn.iocoder.yudao.module.pms.platform.api.businessmodel;

/**
 * 统一业务模型契约异常：重复编码、版本不兼容、未开放字段、能力缺失等在发现、
 * 发布或调用期的显式诊断。禁止静默回退到另一操作版本或任意实体。
 */
public class BusinessContractException extends RuntimeException {

    private final String errorCode;

    public BusinessContractException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
