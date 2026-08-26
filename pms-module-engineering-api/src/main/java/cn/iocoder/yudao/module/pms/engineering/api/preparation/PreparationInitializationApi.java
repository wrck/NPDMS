package cn.iocoder.yudao.module.pms.engineering.api.preparation;

import cn.iocoder.yudao.module.pms.engineering.api.preparation.dto.PreparationInitializationCommand;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.dto.PreparationInitializationResult;

/** 项目创建与受权恢复使用的PRE-02初始化命令契约。 */
public interface PreparationInitializationApi {

    String TRIGGER_PROJECT_CREATION = "PROJECT_CREATION";
    String TRIGGER_AUTHORIZED_RECOVERY = "AUTHORIZED_RECOVERY";

    PreparationInitializationResult initialize(PreparationInitializationCommand command);

}
