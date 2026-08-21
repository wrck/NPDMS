package cn.iocoder.yudao.module.pms.project.api.acceptance;

import cn.iocoder.yudao.module.pms.project.api.acceptance.dto.DeliverableInitializationCommand;
import cn.iocoder.yudao.module.pms.project.api.acceptance.dto.DeliverableInitializationResult;

/** PROJ 可调用的 ACC 同事务内部应用边界。 */
public interface AcceptanceDeliverableInitializationApi {

    DeliverableInitializationResult initialize(DeliverableInitializationCommand command);
}
