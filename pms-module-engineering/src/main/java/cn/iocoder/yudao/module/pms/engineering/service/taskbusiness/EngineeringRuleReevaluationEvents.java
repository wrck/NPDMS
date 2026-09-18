package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import lombok.RequiredArgsConstructor;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.ProjectBusinessResultRecordingApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Legacy wakeups remain compatible. Exact business results are appended in the same Owner transaction. */
@Component
@RequiredArgsConstructor
public class EngineeringRuleReevaluationEvents {
    private final ObjectProvider<ProjectBusinessResultRecordingApi> resultRecording;
    private final PlatformBusinessEventApi outbox;
    private final ObjectProvider<OwnerOperationResultSource> sources;

    @Transactional(propagation = Propagation.MANDATORY)
    public void changed(Long projectId, String aggregateType, Long objectId, Long actorId, String correlationId) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var event = ProjectRuleReevaluationRequested.create(tenant,projectId,actorId,correlationId);
        outbox.append(aggregateType,objectId.toString(),new BusinessEvent(event.eventId(),
                ProjectRuleReevaluationRequested.EVENT_TYPE,JsonUtils.toJsonString(event)));
        var frame = ProjectVerifiedOperationScope.current();
        // The controlled executor emits its exact command result after POST; do not emit a second result here.
        if (frame != null && java.util.Objects.equals(frame.tenantId(),tenant)
                && java.util.Objects.equals(frame.actorId(),actorId) && java.util.Objects.equals(frame.projectId(),projectId)
                && "SOL".equals(frame.ownerContext())
                && ("SiteSurvey".equals(aggregateType) && "SITE_SURVEY".equals(frame.objectType())
                    || "RequirementAnalysis".equals(aggregateType) && "REQUIREMENT_ANALYSIS".equals(frame.objectType()))
                && (frame.objectId() == null || frame.objectId().equals(objectId.toString()))) return;
        var matches = sources.orderedStream().filter(source -> source.supports(aggregateType)).toList();
        if (matches.isEmpty()) return;
        if (matches.size() != 1) throw new IllegalStateException("OWNER_RESULT_SOURCE_NOT_UNIQUE");
        var result = matches.getFirst().current(tenant,projectId,aggregateType,objectId);
        if (result == null) return; // Deleted/legacy records keep their real legacy wakeup, not a fabricated success fact.
        var committed = BusinessOperationResultEvent.create(tenant,projectId,"OWNER." + aggregateType + ".CHANGED",
                event.eventId(),result,actorId,correlationId);
        resultRecording.getObject().record(committed);
        outbox.append(result.objectType(),result.objectId(),new BusinessEvent(committed.eventId(),
                BusinessOperationResultEvent.EVENT_TYPE,JsonUtils.toJsonString(committed)));
    }
    /** Called only after the Owner's successful native formation transition, not after a generic observation. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void formed(Long projectId, String aggregateType, Long objectId, Long actorId, String correlationId) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var matches = sources.orderedStream().filter(source -> source.supports(aggregateType)).toList();
        if (matches.size() != 1) throw new IllegalStateException("OWNER_RESULT_SOURCE_NOT_UNIQUE");
        var result = matches.getFirst().current(tenant, projectId, aggregateType, objectId);
        if (result == null) throw new IllegalStateException("OWNER_FORMED_RESULT_UNAVAILABLE");
        var event = BusinessOperationResultEvent.create(tenant, projectId, "OWNER." + aggregateType + ".FORMED",
                java.util.UUID.randomUUID().toString(), result, actorId, correlationId);
        // Only the recovery journal receives this transition; the original business event remains unchanged.
        resultRecording.getObject().record(event);
    }
}
