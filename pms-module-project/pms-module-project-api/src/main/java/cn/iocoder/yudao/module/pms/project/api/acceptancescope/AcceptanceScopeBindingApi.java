package cn.iocoder.yudao.module.pms.project.api.acceptancescope;

import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeBindingCloseCommand;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeBindingCloseResult;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeBindingResult;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceStageEntryBindingCommand;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.EffectiveScopeBindingCommand;

public interface AcceptanceScopeBindingApi {

    AcceptanceScopeBindingResult bindForStageEntry(AcceptanceStageEntryBindingCommand command);

    AcceptanceScopeBindingResult bindEffectiveScope(EffectiveScopeBindingCommand command);

    /**
     * 关闭项目全部活跃锁定（binding_status=LOCKED 且 effective_to 为空），逐条落
     * effective_to 至调用时点并解锁交付范围收缩判定；在调用方事务内执行。
     * 无活跃锁定时幂等返回 replayed=true。
     */
    AcceptanceScopeBindingCloseResult closeProjectBindings(AcceptanceScopeBindingCloseCommand command);
}
