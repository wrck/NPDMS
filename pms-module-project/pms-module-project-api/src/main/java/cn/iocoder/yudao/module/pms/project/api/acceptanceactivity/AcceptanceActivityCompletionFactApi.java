package cn.iocoder.yudao.module.pms.project.api.acceptanceactivity;

import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto.AcceptanceActivityCompletionCommand;
import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto.AcceptanceActivityCompletionFact;

public interface AcceptanceActivityCompletionFactApi {

    AcceptanceActivityCompletionFact lockAndComplete(AcceptanceActivityCompletionCommand command);
}
