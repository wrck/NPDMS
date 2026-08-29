package cn.iocoder.yudao.module.pms.project.api.acceptanceactivity;

import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto.AcceptanceActivityInitializationCommand;
import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto.AcceptanceActivityInitializationResult;

public interface AcceptanceActivityInitializationApi {

    AcceptanceActivityInitializationResult initialize(AcceptanceActivityInitializationCommand command);
}
