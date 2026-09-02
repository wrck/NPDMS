package cn.iocoder.yudao.module.pms.project.api.satisfaction;

import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionTaskInitializationCommand;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionTaskInitializationResult;

public interface SatisfactionTaskInitializationApi {
    SatisfactionTaskInitializationResult initialize(SatisfactionTaskInitializationCommand command);
}
