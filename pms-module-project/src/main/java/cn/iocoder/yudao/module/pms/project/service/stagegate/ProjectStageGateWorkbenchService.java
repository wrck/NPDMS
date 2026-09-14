package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateReferenceForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectStageExecutionLookupQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectGateRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;

@Service
@RequiredArgsConstructor
public class ProjectStageGateWorkbenchService {
    private final ProjectManualCreationService access;
    private final ProjectTaskRuntimeMapper projects;
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectNodeExecutionMapper executions;
    private final ProjectGateReferenceInstanceMapper references;
    private final ProjectGateRuleService rules;
    private final ProjectStageGateProviderRegistry providers;

    // The established project lock serializes plan/round changes while assembling this view.
    // This is not @Transactional(readOnly=true): Owner fact readers may acquire locks, but never write business state.
    @Transactional(rollbackFor = Exception.class)
    public ProjectStageGateWorkbench inspect(Long projectId, String stageCode, ProjectAccessActor actor) {
        if (!Objects.equals(actor.tenantId(), TenantContextHolder.getRequiredTenantId())) throw exception(PROJECT_NOT_EXISTS);
        access.getProject(projectId, actor); // Original project query permission/data scope, not a manager-only gate.
        var project = projects.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(actor.tenantId(), projectId));
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())) throw exception(PROJECT_NOT_EXISTS);
        var query = new ProjectRuntimeGraphQuery(actor.tenantId(), projectId);
        var stages = graph.selectStagesForUpdate(query).stream().filter(stage -> Objects.equals(stageCode, stage.getStageCode())
                && Objects.equals(stage.getTenantId(), actor.tenantId()) && Objects.equals(stage.getProjectId(), projectId)).toList();
        if (stages.size() != 1) return unavailable(project, stageCode, null, "STAGE_NOT_FOUND");
        var stage = stages.getFirst();
        var rounds = executions.selectCurrentForUpdate(new ProjectPlanScopeQuery(actor.tenantId(), projectId)).stream()
                .filter(round -> "STAGE".equals(round.getNodeKind()) && Objects.equals(round.getNodeInstanceId(), stage.getId())
                        && Objects.equals(round.getTenantId(), actor.tenantId()) && Objects.equals(round.getProjectId(), projectId)
                        && Objects.equals(round.getPlanVersionId(), project.getActivePlanVersionId())
                        && Integer.valueOf(1).equals(round.getCurrentMarker())).toList();
        if (project.getActivePlanVersionId() == null || rounds.size() != 1 || rounds.getFirst().getId() == null)
            return unavailable(project, stageCode, stage.getId(), "STAGE_EXECUTION_UNAVAILABLE");
        var round = rounds.getFirst();
        var current = executions.selectCurrentStageContextForUpdate(new ProjectStageExecutionLookupQuery(
                actor.tenantId(), projectId, stage.getId(), round.getContractId()));
        if (current == null || !Objects.equals(current.executionId(), round.getId()))
            return unavailable(project, stageCode, stage.getId(), "STAGE_EXECUTION_UNAVAILABLE");
        var gates = graph.selectGatesForUpdate(query).stream().filter(gate -> Objects.equals(stageCode, gate.getStageCode())
                && Objects.equals(gate.getTenantId(), actor.tenantId()) && Objects.equals(gate.getProjectId(), projectId)).toList();
        var refs = gates.isEmpty() ? List.<ProjectGateReferenceInstanceDO>of()
                : references.selectOrderedForUpdate(new ProjectGateReferenceForUpdateQuery(actor.tenantId(),
                        gates.stream().map(ProjectGateInstanceDO::getId).toList()));
        var results = gates.stream().map(gate -> new ProjectStageGateWorkbench.Gate(gate.getId(), gate.getGateCode(),
                gate.getName(), gate.getGateType(), gate.getStatus(), evaluate(projectId, gate.getGateCode()),
                refs.stream().filter(ref -> Objects.equals(ref.getGateId(), gate.getId()) && Objects.equals(ref.getTenantId(), actor.tenantId()))
                        .map(ref -> reference(project, gate, ref, current.stageStatus(), current.executionStatus())).toList())).toList();
        return new ProjectStageGateWorkbench(projectId, project.getVersion(), project.getActivePlanVersionId(), stage.getId(),
                stageCode, round.getId(), round.getRoundNo(), null, results);
    }

    private ProjectStageGateWorkbench.Reference reference(ProjectMasterDO project, ProjectGateInstanceDO gate,
            ProjectGateReferenceInstanceDO ref, String stageStatus, String executionStatus) {
        ProjectStageGateProcessState process = null;
        if (ProjectStageReadinessService.isProcess(ref)) {
            var fact = providers.lockAndRevalidate(ProjectStageReadinessService.providerKey(ref.getRefType()),
                    new ProjectStageGateFactQuery(project.getTenantId(), project.getId(), gate.getStageCode(), gate.getId(),
                            gate.getGateCode(), gate.getVersion(), ref.getId(), ref.getVersion(), ref.getRefType(),
                            ref.getRefCode(), ref.getRefVersion(), null));
            process = ProjectStageGateProcessState.from(fact);
        }
        boolean phaseAllowsStart = "ACTIVE".equals(project.getLifecycleStatus())
                && ("ACTIVE".equals(stageStatus) && "ACTIVE".equals(executionStatus)
                    || "ENTRY".equals(gate.getGateType()) && "PENDING".equals(stageStatus) && "PENDING".equals(executionStatus));
        return new ProjectStageGateWorkbench.Reference(ref.getId(), ref.getRefType(), ref.getRefCode(), ref.getRefVersion(),
                process, phaseAllowsStart && process != null && process.canStart());
    }

    private RuleEvaluation evaluate(Long projectId, String gateCode) {
        // The shared evaluator handles unavailable facts as UNKNOWN inside its transaction.
        // Do not swallow transaction/locking failures from the service proxy.
        var result = rules.inspect(projectId, gateCode);
        if (result != null) return result;
        return new RuleEvaluation("project:" + projectId + ":gate:" + gateCode, RuleEvaluation.Outcome.UNKNOWN,
                "GATE_RULE_OR_FACT_UNAVAILABLE", List.of(), List.of(), List.of());
    }

    private ProjectStageGateWorkbench unavailable(ProjectMasterDO project, String stageCode, Long stageId, String reason) {
        return new ProjectStageGateWorkbench(project.getId(), project.getVersion(), project.getActivePlanVersionId(),
                stageId, stageCode, null, null, reason, List.of());
    }
}
