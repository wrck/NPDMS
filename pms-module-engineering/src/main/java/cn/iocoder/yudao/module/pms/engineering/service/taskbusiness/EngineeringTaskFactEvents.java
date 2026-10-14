package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Wakeups share the Owner transaction and carry no customer confirmation token or signature. */
@Component
@RequiredArgsConstructor
public class EngineeringTaskFactEvents {
    private final PlatformBusinessEventApi outbox;

    @Transactional(propagation = Propagation.MANDATORY)
    public void arrivalSigned(ArrivalDO arrival) {
        publish(new Change(arrival.getTenantId(), arrival.getProjectId(), arrival.getId(), "Arrival"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void trainingConfirmed(TrainingDO training) {
        publish(new Change(training.getTenantId(), training.getProjectId(), training.getId(), "Training"));
    }

    private record Change(Long tenantId, Long projectId, Long objectId, String aggregate) { }

    private void publish(Change change) {
        if (!java.util.Objects.equals(change.tenantId(), TenantContextHolder.getRequiredTenantId())
                || change.projectId() == null || change.objectId() == null) throw new IllegalArgumentException("TASK_FACT_EVENT_SCOPE_INVALID");
        String correlation = "task-fact:" + java.util.UUID.randomUUID();
        var event = new ProjectRuleReevaluationRequested(correlation, change.tenantId(), change.projectId(),
                SecurityFrameworkUtils.getLoginUserId(), correlation);
        outbox.append(change.aggregate(), change.objectId().toString(),
                new BusinessEvent(event.eventId(), ProjectRuleReevaluationRequested.EVENT_TYPE, JsonUtils.toJsonString(event)));
    }
}
