package cn.iocoder.yudao.module.pms.integration.stagegate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener;
import org.flowable.engine.delegate.event.FlowableCancelledEvent;
import org.flowable.engine.delegate.event.FlowableProcessStartedEvent;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Objects;

import static cn.iocoder.yudao.module.pms.integration.stagegate.FlowableProjectStageGateProvider.*;
import static org.flowable.common.engine.api.delegate.event.FlowableEngineEventType.*;

/** Registered by the existing BPM ObjectProvider<FlowableEventListener>; no second scheduler or status writer. */
@Component
public class FlowableProjectRuleEventListener extends AbstractFlowableEngineEventListener {
    private final RuntimeService runtime;
    private final PlatformBusinessEventApi outbox;
    private final boolean tenantEnabled;

    public FlowableProjectRuleEventListener(@Lazy RuntimeService runtime, PlatformBusinessEventApi outbox,
                                            @Value("${yudao.tenant.enable:true}") boolean tenantEnabled) {
        super(EnumSet.of(PROCESS_STARTED, PROCESS_COMPLETED, PROCESS_CANCELLED,
                PROCESS_COMPLETED_WITH_TERMINATE_END_EVENT, PROCESS_COMPLETED_WITH_ERROR_END_EVENT,
                PROCESS_COMPLETED_WITH_ESCALATION_END_EVENT));
        this.runtime = runtime;
        this.outbox = outbox;
        this.tenantEnabled = tenantEnabled;
    }

    // Synchronous dispatch joins the engine's Spring transaction; persistence failures must roll it back.
    // https://www.flowable.com/open-source/docs/all-javadocs/org/flowable/engine/delegate/event/AbstractFlowableEngineEventListener.html
    @Override public boolean isFailOnException() { return true; }

    @Override protected void processStarted(FlowableProcessStartedEvent event) {
        // Flowable 8 emits the first child execution here, not the business-key-bearing process instance.
        changedProcess(((DelegateExecution) event.getEntity()).getProcessInstanceId());
    }
    @Override protected void processCompleted(FlowableEngineEntityEvent event) { changed((ProcessInstance) event.getEntity()); }
    @Override protected void processCompletedWithTerminateEnd(FlowableEngineEntityEvent event) { changed((ProcessInstance) event.getEntity()); }
    @Override protected void processCompletedWithErrorEnd(FlowableEngineEntityEvent event) { changed((ProcessInstance) event.getEntity()); }
    @Override protected void processCompletedWithEscalationEnd(FlowableEngineEntityEvent event) { changed((ProcessInstance) event.getEntity()); }

    @Override protected void processCancelled(FlowableCancelledEvent event) {
        // Flowable dispatches cancellation before deleting the runtime instance.
        changedProcess(event.getProcessInstanceId());
    }

    private void changedProcess(String processInstanceId) {
        changed(runtime.createProcessInstanceQuery().processInstanceId(processInstanceId)
                .includeProcessVariables().singleResult());
    }

    private void changed(ProcessInstance process) {
        if (process != null) {
            for (var kind : ProjectNodeApprovalApi.NodeKind.values()) if (kind.owns(process.getBusinessKey())) {
                nodeApprovalChanged(process, kind);
                return;
            }
        }
        if (process == null || process.getBusinessKey() == null
                || !process.getBusinessKey().startsWith("PROJECT_STAGE_GATE:")) return;
        var variables = process.getProcessVariables();
        if (variables == null) throw new IllegalStateException("GATE_EVENT_IDENTITY_UNAVAILABLE");
        Long tenantId = number(variables.get(VAR_TENANT_ID));
        Long projectId = number(variables.get(VAR_PROJECT_ID));
        Long referenceId = number(variables.get(VAR_GATE_REFERENCE_ID));
        Long actorId = number(variables.get(VAR_ACTOR_USER_ID));
        if (tenantId == null || tenantId < 0 || projectId == null || projectId <= 0
                || referenceId == null || referenceId <= 0 || actorId == null || actorId <= 0
                || !Objects.equals(process.getBusinessKey(), businessKey(referenceId))
                || !Objects.equals(process.getProcessDefinitionId(), variables.get(VAR_DEFINITION_ID))
                || tenantEnabled && !Objects.equals(process.getTenantId(), tenantId.toString()))
            throw new IllegalStateException("GATE_EVENT_IDENTITY_INVALID");
        append(process, tenantId, projectId, actorId);
    }

    private void nodeApprovalChanged(ProcessInstance process, ProjectNodeApprovalApi.NodeKind kind) {
        var variables = process.getProcessVariables();
        if (variables == null) throw new IllegalStateException(kind + "_APPROVAL_EVENT_IDENTITY_UNAVAILABLE");
        Long tenantId = number(variables.get(kind.variable("TenantId")));
        Long projectId = number(variables.get(kind.variable("ProjectId")));
        Long executionId = number(variables.get(kind.variable("ExecutionId")));
        Long nodeId = number(variables.get(kind.variable("Id")));
        Long contractId = number(variables.get(kind.variable("ContractId")));
        Long actorId = number(variables.get(kind.variable("ActorId")));
        if (tenantId == null || tenantId < 0 || projectId == null || projectId <= 0
                || executionId == null || executionId <= 0 || nodeId == null || nodeId <= 0
                || contractId == null || contractId <= 0 || actorId == null || actorId <= 0
                || !Objects.equals(process.getBusinessKey(), kind.businessKey(executionId))
                || !Objects.equals(process.getProcessDefinitionId(), variables.get(kind.variable("ProcessDefinitionId")))
                || tenantEnabled && !Objects.equals(process.getTenantId(), tenantId.toString()))
            throw new IllegalStateException(kind + "_APPROVAL_EVENT_IDENTITY_INVALID");
        append(process, tenantId, projectId, actorId);
    }

    private void append(ProcessInstance process, Long tenantId, Long projectId, Long actorId) {
        var event = ProjectRuleReevaluationRequested.create(tenantId, projectId, actorId, "bpm:" + process.getId());
        // No business values or approval outcome in the message: the consumer rereads authoritative committed facts.
        TenantUtils.execute(tenantId, () -> outbox.append("Project", projectId.toString(),
                new BusinessEvent(event.eventId(), ProjectRuleReevaluationRequested.EVENT_TYPE, JsonUtils.toJsonString(event))));
    }

    private static Long number(Object value) { return value instanceof Long id ? id : value instanceof Integer id ? id.longValue() : null; }
}
