package cn.iocoder.yudao.module.pms.acceptance.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.IndependentSatisfactionService;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.event.IndependentSatisfactionResultChanged;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxAppended;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.ProjectBusinessResultRecordingApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Component @RequiredArgsConstructor
public class SatisfactionOperationResultBridge {
    private final ObjectProvider<ProjectBusinessResultRecordingApi> recording;
    private final ObjectProvider<PlatformBusinessEventApi> outbox;
    private final SatisfactionCollectionTaskMapper tasks;
    private final SatisfactionResultMapper results;

    @EventListener @Transactional(propagation = Propagation.MANDATORY)
    public void onAppended(PlatformOutboxAppended appended) {
        var message = appended.message();
        boolean direct = IndependentSatisfactionResultChanged.TYPE.equals(message.eventType());
        if (!direct && !"SatisfactionResultVersionChanged".equals(message.eventType())) return;
        var legacy = direct ? null : JsonUtils.parseObject(message.payload(),
                cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.event.SatisfactionResultVersionChangedMessage.class);
        var event = direct ? JsonUtils.parseObject(message.payload(), IndependentSatisfactionResultChanged.class)
                : legacy == null ? null : new IndependentSatisfactionResultChanged(legacy.eventId(), legacy.tenantId(), legacy.projectId(),
                    legacy.taskId(), legacy.resultId(), legacy.archiveActorUserId(), legacy.changeType());
        Long tenant = TenantContextHolder.getRequiredTenantId();
        if (event == null || !Objects.equals(tenant, message.tenantId()) || !Objects.equals(tenant, event.tenantId())
                || !Objects.equals(message.eventId(), event.eventId())) throw new IllegalArgumentException("SATISFACTION_EVENT_IDENTITY_INVALID");
        var task = tasks.selectById(event.taskId()); var result = results.selectById(event.resultId());
        if (task == null || direct && !IndependentSatisfactionService.direct(task) || !Objects.equals(task.getTenantId(), tenant)
                || !Objects.equals(task.getProjectId(), event.projectId()) || result == null
                || !Objects.equals(result.getTenantId(), tenant) || !Objects.equals(result.getCollectionTaskId(), task.getId()))
            throw new IllegalArgumentException("SATISFACTION_EVENT_IDENTITY_INVALID");
        var operation = new ProjectOperationResult("ACC", "SATISFACTION", task.getId().toString(), result.getId().toString(),
                result.getVersion(), "ACC:SATISFACTION:" + result.getId() + ":" + result.getVersion(),
                "INVALIDATED".equals(event.changeType()) ? "SATISFACTION_RESULT_INVALIDATED" : "SATISFACTION_RESULT_RECORDED", null, false);
        var changed = BusinessOperationResultEvent.create(tenant, task.getProjectId(), "OWNER.SATISFACTION.RESULT_CHANGED",
                event.eventId(), operation, event.actorId(), event.eventId());
        recording.getObject().record(changed);
        outbox.getObject().append("SATISFACTION", operation.objectId(), new BusinessEvent(changed.eventId(),
                BusinessOperationResultEvent.EVENT_TYPE, JsonUtils.toJsonString(changed)));
    }
}
