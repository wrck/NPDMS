package cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity;

import cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity.dto.AcceptanceActivityInitializationCommand;
import cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity.dto.AcceptanceActivityInitializationResult;

public interface AcceptanceActivityInitializationApi {

    AcceptanceActivityInitializationResult initialize(AcceptanceActivityInitializationCommand command);
}
