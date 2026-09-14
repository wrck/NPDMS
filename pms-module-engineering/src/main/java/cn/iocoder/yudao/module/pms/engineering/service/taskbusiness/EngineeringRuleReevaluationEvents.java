package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** No notification/event consumer reuse: a committed Owner change gets its own reevaluation wakeup. */
@Component
@RequiredArgsConstructor
public class EngineeringRuleReevaluationEvents {
    private final PlatformBusinessEventApi outbox;

    @Transactional(propagation = Propagation.MANDATORY)
    public void changed(Long projectId, String aggregateType, Long objectId, Long actorId, String correlationId) {
        var event = ProjectRuleReevaluationRequested.create(TenantContextHolder.getRequiredTenantId(),
                projectId, actorId, correlationId);
        outbox.append(aggregateType, objectId.toString(), new BusinessEvent(event.eventId(),
                ProjectRuleReevaluationRequested.EVENT_TYPE, JsonUtils.toJsonString(event)));
    }
}
