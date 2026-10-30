package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.ResultSubscriptionTaskContract;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectResultEvidenceGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 闭环消费原完成轮次与原计划证据；项目改版不把已完成节点重绑定到新快照。 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class NormalClosureResultEvidence {
    private final ProjectNodeExecutionMapper executions;
    private final ProjectPlanVersionMapper plans;
    private final ProjectResultEvidenceGuard evidence;

    public List<ProjectNodeExecutionDO> lockRounds(ProjectMasterDO project) {
        return executions.selectCurrentForUpdate(new ProjectPlanScopeQuery(project.getTenantId(), project.getId()));
    }

    public ProjectResultEvidenceGuard.Proof revalidate(ProjectMasterDO project, ProjectTaskInstanceDO task,
            ProjectTaskExecutionContractDO contract, List<ProjectNodeExecutionDO> rounds) {
        if (!"DONE".equals(task.getStatus()) || !Objects.equals(project.getTenantId(), task.getTenantId())
                || !Objects.equals(project.getId(), task.getProjectId())) return unavailable("TASK_RESULT_IDENTITY_INVALID");
        var matching = rounds.stream().filter(round -> "TASK".equals(round.getNodeKind())
                && Objects.equals(round.getNodeInstanceId(), task.getId())
                && Objects.equals(round.getContractId(), contract.getId())
                && Objects.equals(round.getNodeKey(), contract.getSourceNodeKey())
                && Objects.equals(round.getTenantId(), project.getTenantId())
                && Objects.equals(round.getProjectId(), project.getId())).toList();
        if (matching.size() != 1) {
            if (matching.isEmpty() && !ResultSubscriptionTaskContract.TYPE.equals(contract.getWorkBindingTypeCode())
                    && (contract.getSourceDefinitionVersion() == null || contract.getSourceDefinitionVersion() < 3))
                return new ProjectResultEvidenceGuard.Proof(true, "NO_RESULT_SUBSCRIPTION", List.of());
            return unavailable("TASK_RESULT_EXECUTION_UNAVAILABLE");
        }
        var round = matching.getFirst();
        var plan = plans.selectById(round.getPlanVersionId());
        if (plan == null || !Objects.equals(plan.getTenantId(), project.getTenantId())
                || !Objects.equals(plan.getProjectId(), project.getId())
                || !Objects.equals(plan.getId(), round.getPlanVersionId())
                || !("SUPERSEDED".equals(plan.getStatus()) || "EFFECTIVE".equals(plan.getStatus())
                    && Objects.equals(plan.getId(), project.getActivePlanVersionId())))
            return unavailable("TASK_RESULT_PLAN_UNAVAILABLE");
        var snapshot = TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot());
        var definitions = snapshot.getTasks().stream().filter(node -> Objects.equals(node.getNodeKey(), round.getNodeKey())
                && Objects.equals(node.getCode(), task.getCode())).toList();
        if (definitions.size() != 1)
            return unavailable("TASK_RESULT_CONTRACT_MISMATCH");
        var definition = definitions.getFirst();
        if (ResultSubscriptionTaskContract.TYPE.equals(contract.getWorkBindingTypeCode())) {
            if (!ResultSubscriptionTaskContract.matches(contract, definition)) return unavailable("TASK_RESULT_CONTRACT_MISMATCH");
        } else {
            if (definition.getBinding() == null || !Objects.equals(definition.getBinding().getType(), contract.getWorkBindingTypeCode()))
                return unavailable("TASK_RESULT_CONTRACT_MISMATCH");
            if (!ProjectResultEvidenceGuard.configured(definition.getExecution()))
                return new ProjectResultEvidenceGuard.Proof(true, "NO_RESULT_SUBSCRIPTION", List.of());
        }
        return evidence.revalidateCompleted(snapshot, plan, round);
    }

    private ProjectResultEvidenceGuard.Proof unavailable(String reason) {
        return new ProjectResultEvidenceGuard.Proof(false, reason, List.of());
    }
}
