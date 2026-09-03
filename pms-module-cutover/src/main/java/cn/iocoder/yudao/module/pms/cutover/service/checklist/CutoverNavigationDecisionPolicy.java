package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.NavigationDecision;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverNavigationRuleCodec;

public final class CutoverNavigationDecisionPolicy {

    public NavigationDecision decide(Long configurationRevisionId, String navigationRuleSnapshot) {
        if (configurationRevisionId == null || configurationRevisionId <= 0) {
            throw new IllegalStateException("任务冻结配置身份无效");
        }
        return new NavigationDecision("POST_SUBMIT", configurationRevisionId,
                CutoverNavigationRuleCodec.targetOrDefault(navigationRuleSnapshot).name());
    }
}
