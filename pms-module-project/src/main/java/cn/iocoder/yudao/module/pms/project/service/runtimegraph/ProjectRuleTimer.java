package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable one-shot wakeup; never a command to apply an old boolean result. */
public record ProjectRuleTimer(String eventId, Long tenantId, Long projectId, Long planVersionId,
                               Long executionId, List<Long> closureExecutionIds, Purpose purpose,
                               String ruleKey, Instant dueAt) {
    public static final String EVENT_TYPE = "ProjectRuleTimerRequested";
    public enum Purpose { ADMISSION, COMPLETION, EXIT, CLOSURE }
    public ProjectRuleTimer {
        closureExecutionIds = List.copyOf(closureExecutionIds);
    }
    public static ProjectRuleTimer create(Long tenant, Long project, Long plan, Long execution,
                                          List<Long> closureExecutions, Purpose purpose, String rule, Instant due) {
        return new ProjectRuleTimer("rule-time:" + UUID.randomUUID(), tenant, project, plan, execution,
                closureExecutions, purpose, rule, due);
    }
    public BusinessEvent event() { return new BusinessEvent(eventId, EVENT_TYPE, JsonUtils.toJsonString(this)); }
}
