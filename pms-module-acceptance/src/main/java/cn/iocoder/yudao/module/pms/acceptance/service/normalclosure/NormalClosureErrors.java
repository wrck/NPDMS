package cn.iocoder.yudao.module.pms.acceptance.service.normalclosure;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import static cn.iocoder.yudao.module.pms.acceptance.enums.ErrorCodeConstants.ACC_PROJECT_CLOSURE_VALIDATION_FAILED;

public final class NormalClosureErrors {
    private NormalClosureErrors() {}
    public static ServiceException failure(String reason) {
        return new ServiceException(ACC_PROJECT_CLOSURE_VALIDATION_FAILED.getCode(), reason);
    }
}
