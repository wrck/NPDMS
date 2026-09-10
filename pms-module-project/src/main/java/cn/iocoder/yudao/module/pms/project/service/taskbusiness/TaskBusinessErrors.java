package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_COMMAND_INVALID;

final class TaskBusinessErrors {
    private TaskBusinessErrors() {}
    static ServiceException failure(String reason) {
        return new ServiceException(PROJECT_TASK_COMMAND_INVALID.getCode(), "TASK_BUSINESS_" + reason);
    }
}
