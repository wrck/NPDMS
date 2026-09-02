package cn.iocoder.yudao.module.pms.cutover.service.configuration;

public class CutoverNavigationRuleException extends RuntimeException {

    public CutoverNavigationRuleException(String message) {
        super(message);
    }

    public CutoverNavigationRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
