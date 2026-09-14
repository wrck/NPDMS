package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateReferenceForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectStageExecutionLookupQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_STAGE_PROCESS_INVALID;

/** Resolves a configured process entry, not a transition target or a second activation rule. */
@Component
@RequiredArgsConstructor
public class ProjectStageGateProcessContextResolver {
    private final ProjectPlanVersionMapper plans;
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectGateReferenceInstanceMapper references;
    private final ProjectNodeExecutionMapper executions;

    public record Context(ProjectGateInstanceDO gate, ProjectGateReferenceInstanceDO reference, ProjectNodeExecutionDO execution) { }

    /** Caller holds the project lock and owns operation authorization; all local reads precede the BPM Owner call. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Context resolve(ProjectMasterDO project, Long referenceId) {
        if (!"ACTIVE".equals(project.getLifecycleStatus()) || project.getActivePlanVersionId() == null)
            throw exception(PROJECT_STAGE_PROCESS_INVALID);
        var scope = new ProjectPlanScopeQuery(project.getTenantId(), project.getId());
        var plan = plans.selectEffective(scope);
        if (plan == null || !Objects.equals(plan.getId(), project.getActivePlanVersionId()))
            throw exception(PROJECT_STAGE_PROCESS_INVALID);
        var snapshot = JsonUtils.parseObject(plan.getExecutionSnapshot(), TemplateExecutionSnapshot.class);
        var query = new ProjectRuntimeGraphQuery(project.getTenantId(), project.getId());
        var stages = graph.selectStagesForUpdate(query);
        var gates = graph.selectGatesForUpdate(query);
        if (gates.isEmpty()) throw exception(PROJECT_STAGE_PROCESS_INVALID);
        var refs = references.selectOrderedForUpdate(new ProjectGateReferenceForUpdateQuery(project.getTenantId(),
                gates.stream().map(ProjectGateInstanceDO::getId).toList())).stream()
                .filter(ref -> Objects.equals(ref.getId(), referenceId)).toList();
        if (refs.size() != 1 || !ProjectStageReadinessService.isProcess(refs.getFirst())
                || !Objects.equals(refs.getFirst().getTenantId(), project.getTenantId()))
            throw exception(PROJECT_STAGE_PROCESS_INVALID);
        var ref = refs.getFirst();
        if (ref.getRefVersion() == null || ref.getRefVersion().isBlank())
            throw exception(PROJECT_STAGE_PROCESS_INVALID);
        var selected = gates.stream().filter(gate -> Objects.equals(gate.getId(), ref.getGateId())).toList();
        if (selected.size() != 1) throw exception(PROJECT_STAGE_PROCESS_INVALID);
        var gate = selected.getFirst();
        if (!Objects.equals(gate.getTenantId(), project.getTenantId()) || !Objects.equals(gate.getProjectId(), project.getId()))
            throw exception(PROJECT_STAGE_PROCESS_INVALID);
        var definitions = snapshot.getGates().stream().filter(def -> Objects.equals(def.getCode(), gate.getGateCode())).toList();
        if (definitions.size() != 1) throw exception(PROJECT_STAGE_PROCESS_INVALID);
        var definition = definitions.getFirst();
        if (!Objects.equals(definition.getStageCode(), gate.getStageCode()) || !Objects.equals(definition.getGateType(), gate.getGateType())
                || definition.getReferences().stream().filter(item -> Objects.equals(item.getRefType(), ref.getRefType())
                        && Objects.equals(item.getRefCode(), ref.getRefCode()) && Objects.equals(item.getRefVersion(), ref.getRefVersion())).count() != 1)
            throw exception(PROJECT_STAGE_PROCESS_INVALID);
        var parents = stages.stream().filter(stage -> Objects.equals(stage.getStageCode(), gate.getStageCode())
                && Objects.equals(stage.getTenantId(), project.getTenantId()) && Objects.equals(stage.getProjectId(), project.getId())).toList();
        if (parents.size() != 1) throw exception(PROJECT_STAGE_PROCESS_INVALID);
        var stage = parents.getFirst();
        var stageDefinitions = snapshot.getStages().stream().filter(def -> Objects.equals(def.getCode(), stage.getStageCode())).toList();
        if (stageDefinitions.size() != 1) throw exception(PROJECT_STAGE_PROCESS_INVALID);
        var rounds = executions.selectCurrentForUpdate(scope).stream().filter(round -> "STAGE".equals(round.getNodeKind())
                && Objects.equals(round.getTenantId(), project.getTenantId()) && Objects.equals(round.getProjectId(), project.getId())
                && Objects.equals(round.getNodeInstanceId(), stage.getId()) && Objects.equals(round.getPlanVersionId(), plan.getId())
                && Objects.equals(round.getNodeKey(), stageDefinitions.getFirst().getNodeKey())
                && Integer.valueOf(1).equals(round.getCurrentMarker())).toList();
        if (rounds.size() != 1) throw exception(PROJECT_STAGE_PROCESS_INVALID);
        var round = rounds.getFirst();
        // The existing join also checks the effective contract, node key, graph version and active plan identity.
        var current = executions.selectCurrentStageContextForUpdate(new ProjectStageExecutionLookupQuery(
                project.getTenantId(), project.getId(), stage.getId(), round.getContractId()));
        if (current == null || !Objects.equals(current.executionId(), round.getId())
                || !"ACTIVE".equals(current.projectStatus())
                || !("ACTIVE".equals(current.stageStatus()) && "ACTIVE".equals(current.executionStatus())
                    || "ENTRY".equals(gate.getGateType()) && "PENDING".equals(current.stageStatus()) && "PENDING".equals(current.executionStatus())))
            throw exception(PROJECT_STAGE_PROCESS_INVALID);
        return new Context(gate, ref, round);
    }

    /** Successful Owner start and the handling evidence commit or roll back together; no admission/status change. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordStarted(Context context, Long actorId) {
        var round = context.execution();
        // ENTRY work before admission is protected by its live process, not a fictitious stage start.
        if ("PENDING".equals(round.getStatus())) return;
        if (round.getStartedAt() != null) return;
        if (executions.beginStageHandlingIfCurrent(new ProjectNodeExecutionMapper.StageHandlingStart(
                round.getTenantId(), round.getProjectId(), round.getId(), round.getPlanVersionId(), round.getContractId(),
                round.getVersion(), java.time.LocalDateTime.now(), actorId)) != 1)
            throw exception(PROJECT_STAGE_PROCESS_INVALID);
    }
}
