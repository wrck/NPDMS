package cn.iocoder.yudao.module.pms.acceptance.api.satisfaction;

import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionTaskInitializationCommand;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionTaskInitializationResult;

public interface SatisfactionTaskInitializationApi {
    SatisfactionTaskInitializationResult initialize(SatisfactionTaskInitializationCommand command);
}
