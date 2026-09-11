package cn.iocoder.yudao.module.pms.project.service.taskworkbench;
import cn.iocoder.yudao.framework.xss.core.clean.JsoupXssCleaner;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_COMMAND_INVALID;
public final class TaskRichText {
    public static final String PLAIN = "PLAIN", HTML = "HTML";
    public static final int MAX_LENGTH = 65535;
    private TaskRichText() { }
    public static String clean(String input) {
        if (input == null) return "";
        if (input.length() > MAX_LENGTH) throw exception(PROJECT_TASK_COMMAND_INVALID);
        String value = new JsoupXssCleaner().clean(input);
        if (value.length() > MAX_LENGTH) throw exception(PROJECT_TASK_COMMAND_INVALID);
        return value;
    }
}
