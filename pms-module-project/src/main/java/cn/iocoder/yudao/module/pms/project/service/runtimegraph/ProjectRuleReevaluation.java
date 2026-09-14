package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;

/** A dedicated reevaluation event, never a competing consumer of notification events. */
public record ProjectRuleReevaluation(Long tenantId, Long projectId, Long actorId, String correlationId) {
    public static final String EVENT_TYPE = cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested.EVENT_TYPE;

    public BusinessEvent event() {
        // The platform event_id column is bounded; command idempotency owns producer replay.
        var payload = cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested.create(
                tenantId, projectId, actorId, correlationId);
        return new BusinessEvent(payload.eventId(), EVENT_TYPE, JsonUtils.toJsonString(payload));
    }

}
