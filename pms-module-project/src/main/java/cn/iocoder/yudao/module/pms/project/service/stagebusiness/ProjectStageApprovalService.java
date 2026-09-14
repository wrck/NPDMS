package cn.iocoder.yudao.module.pms.project.service.stagebusiness;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.ApprovalWorkBindingSchema;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.BindingContract;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Objects;

import static cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.*;

/** Reads original BPM evidence against the frozen binding and actual project-owned stage round. */
@Service
@RequiredArgsConstructor
public class ProjectStageApprovalService {
    private final ProjectNodeExecutionMapper rounds;
    private final ProjectNodeApprovalApi approvals;
    private final ProjectNodeExecutionApi executions;

    /** Caller has authorized and resolved the frozen definition under the project lock. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Fact start(Long tenantId, ProjectStageExecutionContext expected, ApprovalWorkBindingSchema.Definition definition,
                      Long actorId, String operationId, Submission submission) {
        var current = executions.beginStageHandling(expected, actorId);
        var round = requireRound(tenantId, current);
        if (round.getStartedAt() == null) throw new IllegalStateException("STAGE_APPROVAL_START_EVIDENCE_REQUIRED");
        return approvals.startStage(new StageStart(new Scope(NodeKind.STAGE, tenantId, current.projectId(), current.stageId(),
                round.getId(), round.getContractId(), definition.key(), definition.id(), round.getStartedAt()), current,
                actorId, operationId, submission == null ? null : submission.variables(),
                submission == null ? null : submission.selectedApprovers()));
    }

    // This external service entry establishes the transaction required by the BPM Owner's inspect method.
    @Transactional(readOnly = true)
    public View view(Long tenantId, ProjectStageExecutionContext execution, BindingContract binding) {
        if (binding == null || !"APPROVAL".equals(binding.getType()))
            throw new IllegalArgumentException("APPROVAL_BINDING_REQUIRED");
        var definition = ApprovalWorkBindingSchema.read(binding.getApprovalDefinitionKey(), binding.getParameters());
        Fact fact;
        try {
            var round = requireRound(tenantId, execution);
            fact = round.getStartedAt() == null
                    ? new Fact(Outcome.NOT_SATISFIED, "NOT_STARTED", null, definition.id(), "APPROVAL_NOT_STARTED")
                    : approvals.inspect(new Scope(NodeKind.STAGE, tenantId, execution.projectId(), execution.stageId(),
                            round.getId(), round.getContractId(), definition.key(), definition.id(), round.getStartedAt()));
            if (fact == null) fact = Fact.unknown("STAGE_APPROVAL_FACT_UNAVAILABLE");
        } catch (RuntimeException unavailable) {
            fact = Fact.unknown("STAGE_APPROVAL_FACT_UNAVAILABLE");
        }
        return new View(definition.key(), definition.id(), execution == null ? null : execution.executionId(), fact);
    }

    private ProjectNodeExecutionDO requireRound(Long tenantId, ProjectStageExecutionContext execution) {
        if (execution == null || !Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("STAGE_APPROVAL_EXECUTION_UNAVAILABLE");
        var round = rounds.selectById(execution.executionId());
        if (round == null || !Objects.equals(tenantId, round.getTenantId())
                    || !Objects.equals(execution.projectId(), round.getProjectId())
                    || !"STAGE".equals(round.getNodeKind())
                    || !Objects.equals(execution.stageId(), round.getNodeInstanceId())
                    || !Objects.equals(execution.executionId(), round.getId())
                    || !Objects.equals(execution.executionContractId(), round.getContractId())
                    || !Objects.equals(execution.planVersionId(), round.getPlanVersionId())
                    || !Objects.equals(execution.executionVersion(), round.getVersion())
                    || !Objects.equals(execution.roundNo(), round.getRoundNo())
                    || !Integer.valueOf(1).equals(round.getCurrentMarker()))
            throw new IllegalArgumentException("STAGE_APPROVAL_EXECUTION_MISMATCH");
        return round;
    }
}
