package cn.iocoder.yudao.module.pms.project.api.acceptancescope;

import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeBindingResult;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceStageEntryBindingCommand;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.EffectiveScopeBindingCommand;

public interface AcceptanceScopeBindingApi {

    AcceptanceScopeBindingResult bindForStageEntry(AcceptanceStageEntryBindingCommand command);

    AcceptanceScopeBindingResult bindEffectiveScope(EffectiveScopeBindingCommand command);
}
