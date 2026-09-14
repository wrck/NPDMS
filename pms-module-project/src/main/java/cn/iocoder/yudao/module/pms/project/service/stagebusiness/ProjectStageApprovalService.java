package cn.iocoder.yudao.module.pms.project.service.stagebusiness;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.ApprovalWorkBindingSchema;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.BindingContract;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.*;

/** Reads original BPM evidence against the frozen binding and actual project-owned stage round. */
@Service
@RequiredArgsConstructor
public class ProjectStageApprovalService {
    private final ProjectNodeExecutionMapper rounds;
    private final ProjectNodeApprovalApi approvals;

    // This external service entry establishes the transaction required by the BPM Owner's inspect method.
    @Transactional(readOnly = true)
    public View view(Long tenantId, ProjectStageExecutionContext execution, BindingContract binding) {
        if (binding == null || !"APPROVAL".equals(binding.getType()))
            throw new IllegalArgumentException("APPROVAL_BINDING_REQUIRED");
        var definition = ApprovalWorkBindingSchema.read(binding.getApprovalDefinitionKey(), binding.getParameters());
        Fact fact;
        try {
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
}
