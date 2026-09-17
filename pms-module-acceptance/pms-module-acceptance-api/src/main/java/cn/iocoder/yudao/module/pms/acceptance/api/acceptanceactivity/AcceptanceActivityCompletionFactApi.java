package cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity;

import cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity.dto.AcceptanceActivityCompletionCommand;
import cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity.dto.AcceptanceActivityCompletionFact;

public interface AcceptanceActivityCompletionFactApi {

    AcceptanceActivityCompletionFact lockAndComplete(AcceptanceActivityCompletionCommand command);
}
