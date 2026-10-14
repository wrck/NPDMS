package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.NavigationDecision;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverNavigationRuleCodec;

public final class CutoverNavigationDecisionPolicy {

    public record Context(Long configurationRevisionId, String navigationTargetCode) { }

    public NavigationDecision decide(Context context) {
        if (context.configurationRevisionId() == null || context.configurationRevisionId() <= 0) {
            throw new IllegalStateException("任务冻结配置身份无效");
        }
        String target = context.navigationTargetCode() == null ? "CURRENT_STAGE_WORKBENCH" :
                CutoverNavigationRuleCodec.NavigationTarget.valueOf(context.navigationTargetCode()).name();
        return new NavigationDecision("POST_SUBMIT", context.configurationRevisionId(), target);
    }

    public NavigationDecision decide(Long configurationRevisionId, String navigationRuleSnapshot) {
        if (configurationRevisionId == null || configurationRevisionId <= 0) {
            throw new IllegalStateException("任务冻结配置身份无效");
        }
        return new NavigationDecision("POST_SUBMIT", configurationRevisionId,
                CutoverNavigationRuleCodec.targetOrDefault(navigationRuleSnapshot).name());
    }
}
