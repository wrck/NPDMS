package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.domain.template.ApprovalWorkBindingSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Coordinates an authorized task command with BPM. Approval results never directly write task state. */
@Service
@RequiredArgsConstructor
public class ProjectTaskApprovalService {
    private final ProjectNodeExecutionApi executions;
    private final ProjectNodeApprovalApi approvals;

    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectNodeApprovalApi.Fact start(Long tenantId, Long projectId, Long taskId,
            ProjectTaskExecutionContractDO binding, Long actorId, String operationId, ProjectNodeApprovalApi.Submission submission) {
        var context = executions.inspect(new ProjectTaskExecutionQuery(projectId, taskId, binding.getId()));
        return approvals.start(new ProjectNodeApprovalApi.Start(scope(tenantId, projectId, taskId, binding,
                context.executionId(), context.startedAt()), context, actorId, operationId,
                submission == null || submission.variables() == null ? Map.of() : submission.variables(),
                submission == null || submission.selectedApprovers() == null ? Map.of() : submission.selectedApprovers()));
    }

    @Transactional(readOnly = true)
    public ProjectNodeApprovalApi.View view(Long tenantId, Long projectId, Long taskId, ProjectTaskExecutionContractDO binding) {
        var parameters = JsonUtils.parseTree(binding.getBindingParameterSnapshot());
        var definition = ApprovalWorkBindingSchema.read(parameters.path("approvalDefinitionKey").asText(null), parameters);
        var context = executions.inspect(new ProjectTaskExecutionQuery(projectId, taskId, binding.getId()));
        var fact = context.startedAt() == null
                ? new ProjectNodeApprovalApi.Fact(ProjectNodeApprovalApi.Outcome.NOT_SATISFIED,"NOT_STARTED",null,definition.id(),"APPROVAL_NOT_STARTED")
                : inspect(tenantId, projectId, taskId, binding, context.executionId(), context.startedAt());
        return new ProjectNodeApprovalApi.View(definition.key(), definition.id(), context.executionId(), fact);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectNodeApprovalApi.Fact inspect(Long tenantId, Long projectId, Long taskId,
            ProjectTaskExecutionContractDO binding, Long executionId, java.time.LocalDateTime startedAt) {
        try {
            var result = approvals.inspect(scope(tenantId, projectId, taskId, binding, executionId, startedAt));
            return result == null ? ProjectNodeApprovalApi.Fact.unknown("TASK_APPROVAL_FACT_UNAVAILABLE") : result;
        } catch (RuntimeException ex) {
            return ProjectNodeApprovalApi.Fact.unknown("TASK_APPROVAL_FACT_UNAVAILABLE");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireMayCancel(Long tenantId, Long projectId, Long taskId, ProjectTaskExecutionContractDO binding) {
        var context = executions.inspect(new ProjectTaskExecutionQuery(projectId, taskId, binding.getId()));
        if (context.startedAt() == null) return; // No approval can have started before this task round starts.
        var fact = inspect(tenantId, projectId, taskId, binding, context.executionId(), context.startedAt());
        if (fact.outcome() == ProjectNodeApprovalApi.Outcome.UNKNOWN || "RUNNING".equals(fact.status()))
            throw new IllegalStateException("请先在原审批模块结束或取消本轮审批，再关闭任务");
    }

    private ProjectNodeApprovalApi.Scope scope(Long tenantId, Long projectId, Long taskId,
            ProjectTaskExecutionContractDO binding, Long executionId, java.time.LocalDateTime startedAt) {
        if (!"APPROVAL".equals(binding.getWorkBindingTypeCode())) throw new IllegalArgumentException("APPROVAL_BINDING_REQUIRED");
        var parameters = JsonUtils.parseTree(binding.getBindingParameterSnapshot());
        var definition = ApprovalWorkBindingSchema.read(parameters.path("approvalDefinitionKey").asText(null), parameters);
        return new ProjectNodeApprovalApi.Scope(ProjectNodeApprovalApi.NodeKind.TASK, tenantId, projectId, taskId, executionId, binding.getId(),
                definition.key(), definition.id(), startedAt);
    }
}
