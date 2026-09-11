package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateReferenceForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionDefinition;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionGraph;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionTargetResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_STAGE_ADVANCE_INVALID;

/** PM-03 shared runtime decision. V2 evaluates only immutable rule snapshots. */
@Service
@RequiredArgsConstructor
public class ProjectRuntimeGraphResolver {
    private final ProjectRuntimeGraphMapper mapper;
    private final ProjectGateReferenceInstanceMapper referenceMapper;
    private final ProjectRuntimeRuleEvaluator evaluator;

    public record Resolution(ProjectStageInstanceDO current, ProjectStageInstanceDO target,
                             StageTransitionTargetResolver.Result transition,
                             StageTransitionTargetResolver.ConditionStatus completion,
                             List<ProjectGateInstanceDO> gates, List<ProjectGateReferenceInstanceDO> references) {
        public boolean terminal() { return transition.status() == StageTransitionTargetResolver.Status.TERMINAL; }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Resolution resolve(ProjectMasterDO project) { return resolve(project, true); }

    @Transactional(propagation = Propagation.MANDATORY)
    public Resolution inspect(ProjectMasterDO project) { return resolve(project, false); }

    private Resolution resolve(ProjectMasterDO project, boolean locked) {
        if (!Objects.equals(project.getTenantId(), TenantContextHolder.getRequiredTenantId()))
            throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_TENANT_MISMATCH");
        var query = new ProjectRuntimeGraphQuery(project.getTenantId(), project.getId());
        List<ProjectStageInstanceDO> stages = locked ? mapper.selectStagesForUpdate(query) : mapper.selectStages(query);
        List<ProjectStageExecutionContractDO> contracts = mapper.selectContracts(query);
        if (stages.isEmpty() || contracts.isEmpty() || stages.stream().anyMatch(stage -> stage.getGraphVersion() == null))
            throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_NOT_FROZEN");

        Map<Long, ProjectStageInstanceDO> byId = stages.stream()
                .collect(Collectors.toMap(ProjectStageInstanceDO::getId, stage -> stage));
        Map<Long, ProjectStageExecutionContractDO> byStage = new HashMap<>();
        for (ProjectStageExecutionContractDO contract : contracts) {
            ProjectStageInstanceDO stage = byId.get(contract.getStageId());
            boolean v2 = !blank(contract.getSourceNodeKey());
            if (stage == null || !Objects.equals(contract.getTenantId(), project.getTenantId())
                    || !Objects.equals(contract.getProjectId(), project.getId()) || contract.getEffectiveTo() != null
                    || !Objects.equals(contract.getGraphVersion(), stage.getGraphVersion())
                    || (!v2 && !Objects.equals(contract.getDefinitionRevisionId(), stage.getDefinitionRevisionId()))
                    || byStage.putIfAbsent(contract.getStageId(), contract) != null)
                throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_CONTRACT_STALE");
        }
        if (byStage.size() != stages.size()) throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_NOT_FROZEN");

        ProjectStageInstanceDO current = stages.stream()
                .filter(stage -> Objects.equals(stage.getStageCode(), project.getCurrentStage()))
                .findFirst().orElseThrow(() -> exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_CURRENT_STAGE_MISSING"));
        if (!"ACTIVE".equals(project.getLifecycleStatus()) || !"ACTIVE".equals(current.getStatus())
                || stages.stream().filter(stage -> "ACTIVE".equals(stage.getStatus())).count() != 1
                || stages.stream().anyMatch(stage -> !Objects.equals(stage.getTenantId(), project.getTenantId())
                || !Objects.equals(stage.getProjectId(), project.getId())
                || !Objects.equals(stage.getGraphVersion(), current.getGraphVersion())))
            throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_NODE_STALE");

        List<ProjectStageTransitionDO> edges = mapper.selectTransitions(query);
        List<StageTransitionDefinition> definitions = new ArrayList<>();
        Map<Long, ProjectStageTransitionDO> edgeByConditionToken = new LinkedHashMap<>();
        for (ProjectStageTransitionDO edge : edges) {
            ProjectStageInstanceDO from = byId.get(edge.getFromStageId());
            ProjectStageInstanceDO to = byId.get(edge.getToStageId());
            if (from == null || to == null || !Objects.equals(edge.getTenantId(), project.getTenantId())
                    || !Objects.equals(edge.getProjectId(), project.getId())
                    || !Objects.equals(edge.getGraphVersion(), current.getGraphVersion()))
                throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_EDGE_STALE");
            Long token = conditionToken(edge);
            definitions.add(new StageTransitionDefinition(edge.getTransitionCode(), from.getStageCode(), to.getStageCode(),
                    token, edge.getPriority(), edge.getIsDefault()));
            if (token != null && edgeByConditionToken.putIfAbsent(token, edge) != null)
                throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_CONDITION_IDENTITY_CONFLICT");
        }
        StageTransitionGraph graph = new StageTransitionGraph(stages.stream().map(stage -> new StageTransitionGraph.Stage(
                stage.getStageCode(), stage.getStartNode(), stage.getTerminalNode())).toList(), definitions);

        List<ProjectGateInstanceDO> allGates = locked ? mapper.selectGatesForUpdate(query) : mapper.selectGates(query);
        var referenceQuery = new ProjectGateReferenceForUpdateQuery(project.getTenantId(),
                allGates.stream().map(ProjectGateInstanceDO::getId).toList());
        List<ProjectGateReferenceInstanceDO> allRefs = allGates.isEmpty() ? List.of()
                : locked ? referenceMapper.selectOrderedForUpdate(referenceQuery) : referenceMapper.selectOrdered(referenceQuery);
        List<ProjectTaskInstanceDO> tasks = locked ? mapper.selectTasksForUpdate(query) : mapper.selectTasks(query);
        var facts = new ProjectRuntimeRuleEvaluator.Facts(project, current, tasks, allGates, allRefs, false);

        List<StageTransitionTargetResolver.ConditionFact> conditionFacts = new ArrayList<>();
        for (Map.Entry<Long, ProjectStageTransitionDO> entry : edgeByConditionToken.entrySet()) {
            ProjectStageTransitionDO edge = entry.getValue();
            if (!Objects.equals(edge.getFromStageId(), current.getId())) continue;
            JsonNode rule = conditionRule(edge, byStage.get(current.getId()));
            conditionFacts.add(new StageTransitionTargetResolver.ConditionFact(entry.getKey(), evaluator.evaluate(rule, facts)));
        }
        StageTransitionTargetResolver.Result transition = StageTransitionTargetResolver.resolve(
                graph, current.getStageCode(), conditionFacts);
        ProjectStageInstanceDO target = transition.targetStageCode() == null ? null : stages.stream()
                .filter(stage -> stage.getStageCode().equals(transition.targetStageCode())).findFirst().orElseThrow();
        if (target != null && !"PENDING".equals(target.getStatus()))
            throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_TARGET_STALE");

        ProjectStageExecutionContractDO contract = byStage.get(current.getId());
        JsonNode completionRule = completionRule(contract);
        StageTransitionTargetResolver.ConditionStatus completion = evaluator.evaluate(completionRule,
                new ProjectRuntimeRuleEvaluator.Facts(project, current, tasks, allGates, allRefs, true));

        List<ProjectGateInstanceDO> gates = allGates.stream().filter(gate ->
                (Objects.equals(gate.getStageCode(), current.getStageCode()) && "EXIT".equals(gate.getGateType()))
                || (target != null && Objects.equals(gate.getStageCode(), target.getStageCode()) && "ENTRY".equals(gate.getGateType())))
                .toList();
        Set<Long> gateIds = gates.stream().map(ProjectGateInstanceDO::getId).collect(Collectors.toSet());
        List<ProjectGateReferenceInstanceDO> refs = allRefs.stream().filter(ref -> gateIds.contains(ref.getGateId())).toList();
        return new Resolution(current, target, transition, completion, gates, refs);
    }

    private Long conditionToken(ProjectStageTransitionDO edge) {
        if (edge.getConditionSnapshot() == null) return null;
        if (edge.getConditionRuleRevisionId() != null) return edge.getConditionRuleRevisionId();
        if (edge.getId() == null || edge.getId() <= 0)
            throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_CONDITION_IDENTITY_REQUIRED");
        return edge.getId();
    }

    private JsonNode conditionRule(ProjectStageTransitionDO edge, ProjectStageExecutionContractDO stageContract) {
        if (edge.getConditionSnapshot() == null)
            throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_CONDITION_SNAPSHOT_MISSING");
        JsonNode snapshot = JsonUtils.parseObject(edge.getConditionSnapshot(), JsonNode.class);
        if (!blank(edge.getSourceTransitionKey())) return snapshot;
        if (edge.getConditionRuleRevisionId() == null)
            throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_CONDITION_REVISION_MISSING");
        JsonNode legacy = new FrozenDefinitions(stageContract.getDefinitionSnapshot())
                .require(edge.getConditionRuleRevisionId(), "COMPLETION_RULE");
        if (!legacy.equals(snapshot)) throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_CONDITION_SNAPSHOT_MISMATCH");
        return snapshot;
    }

    private JsonNode completionRule(ProjectStageExecutionContractDO contract) {
        if (!blank(contract.getSourceNodeKey())) {
            if (contract.getCompletionRuleSnapshot() == null)
                throw exception(PROJECT_STAGE_ADVANCE_INVALID, "STAGE_COMPLETION_SNAPSHOT_MISSING");
            return JsonUtils.parseObject(contract.getCompletionRuleSnapshot(), JsonNode.class);
        }
        if (contract.getCompletionRuleRevisionId() == null)
            throw exception(PROJECT_STAGE_ADVANCE_INVALID, "STAGE_COMPLETION_REVISION_MISSING");
        return new FrozenDefinitions(contract.getDefinitionSnapshot())
                .require(contract.getCompletionRuleRevisionId(), "COMPLETION_RULE");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
