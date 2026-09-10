package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateReferenceForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.JsonNode;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_STAGE_ADVANCE_INVALID;

/** PM-03: shared runtime decision for preview and command. No sort-order fallback, no state writes. */
@Service @RequiredArgsConstructor
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
    public Resolution resolve(ProjectMasterDO project) {
        return resolve(project, true);
    }

    /** Preview reads do not lock every runtime node; commands always resolve again under locks. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Resolution inspect(ProjectMasterDO project) {
        return resolve(project, false);
    }

    private Resolution resolve(ProjectMasterDO project, boolean locked) {
        if (!Objects.equals(project.getTenantId(), TenantContextHolder.getRequiredTenantId()))
            throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_TENANT_MISMATCH");
        var query = new ProjectRuntimeGraphQuery(project.getTenantId(), project.getId());
        List<ProjectStageInstanceDO> stages = locked ? mapper.selectStagesForUpdate(query) : mapper.selectStages(query);
        List<ProjectStageExecutionContractDO> contracts = mapper.selectContracts(query);
        if (stages.isEmpty() || contracts.isEmpty() || stages.stream().anyMatch(s -> s.getGraphVersion() == null))
            throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_NOT_FROZEN");
        Map<Long, ProjectStageInstanceDO> byId = stages.stream().collect(Collectors.toMap(ProjectStageInstanceDO::getId, s -> s));
        Map<Long, ProjectStageExecutionContractDO> byStage = new HashMap<>();
        for (var c : contracts) {
            var stage = byId.get(c.getStageId());
            if (stage == null || !Objects.equals(c.getTenantId(), project.getTenantId())
                    || !Objects.equals(c.getProjectId(), project.getId()) || c.getEffectiveTo() != null
                    || !Objects.equals(c.getGraphVersion(), stage.getGraphVersion())
                    || !Objects.equals(c.getDefinitionRevisionId(), stage.getDefinitionRevisionId())
                    || byStage.putIfAbsent(c.getStageId(), c) != null)
                throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_CONTRACT_STALE");
        }
        if (byStage.size() != stages.size()) throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_NOT_FROZEN");
        var current = stages.stream().filter(s -> Objects.equals(s.getStageCode(), project.getCurrentStage()))
                .findFirst().orElseThrow(() -> exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_CURRENT_STAGE_MISSING"));
        if (!"ACTIVE".equals(project.getLifecycleStatus()) || !"ACTIVE".equals(current.getStatus())
                || stages.stream().filter(s -> "ACTIVE".equals(s.getStatus())).count() != 1
                || stages.stream().anyMatch(s -> !Objects.equals(s.getTenantId(), project.getTenantId())
                || !Objects.equals(s.getProjectId(), project.getId()) || !Objects.equals(s.getGraphVersion(), current.getGraphVersion())))
            throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_NODE_STALE");
        var edges = mapper.selectTransitions(query);
        List<StageTransitionDefinition> definitions = new ArrayList<>();
        for (var edge : edges) {
            var from = byId.get(edge.getFromStageId()); var to = byId.get(edge.getToStageId());
            if (from == null || to == null || !Objects.equals(edge.getTenantId(), project.getTenantId())
                    || !Objects.equals(edge.getProjectId(), project.getId())
                    || !Objects.equals(edge.getGraphVersion(), current.getGraphVersion()))
                throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_EDGE_STALE");
            definitions.add(new StageTransitionDefinition(edge.getTransitionCode(), from.getStageCode(), to.getStageCode(),
                    edge.getConditionRuleRevisionId(), edge.getPriority(), edge.getIsDefault()));
        }
        var graph = new StageTransitionGraph(stages.stream().map(s -> new StageTransitionGraph.Stage(
                s.getStageCode(), s.getStartNode(), s.getTerminalNode())).toList(), definitions);
        var allGates = locked ? mapper.selectGatesForUpdate(query) : mapper.selectGates(query);
        var referenceQuery = new ProjectGateReferenceForUpdateQuery(project.getTenantId(),
                allGates.stream().map(ProjectGateInstanceDO::getId).toList());
        var allRefs = allGates.isEmpty() ? List.<ProjectGateReferenceInstanceDO>of()
                : locked ? referenceMapper.selectOrderedForUpdate(referenceQuery) : referenceMapper.selectOrdered(referenceQuery);
        var tasks = locked ? mapper.selectTasksForUpdate(query) : mapper.selectTasks(query);
        var facts = new ProjectRuntimeRuleEvaluator.Facts(project, current, tasks, allGates, allRefs, false);
        Map<Long, StageTransitionTargetResolver.ConditionStatus> conditions = new LinkedHashMap<>();
        for (var edge : edges) {
            if (!Objects.equals(edge.getFromStageId(), current.getId()) || edge.getConditionRuleRevisionId() == null) continue;
            var rule = new FrozenDefinitions(byStage.get(current.getId()).getDefinitionSnapshot())
                    .require(edge.getConditionRuleRevisionId(), "COMPLETION_RULE");
            if (!rule.equals(JsonUtils.parseObject(edge.getConditionSnapshot(), JsonNode.class)))
                throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_CONDITION_SNAPSHOT_MISMATCH");
            conditions.putIfAbsent(edge.getConditionRuleRevisionId(), evaluator.evaluate(rule, facts));
        }
        var transition = StageTransitionTargetResolver.resolve(graph, current.getStageCode(), conditions.entrySet().stream()
                .map(e -> new StageTransitionTargetResolver.ConditionFact(e.getKey(), e.getValue())).toList());
        var target = transition.targetStageCode() == null ? null : stages.stream()
                .filter(s -> s.getStageCode().equals(transition.targetStageCode())).findFirst().orElseThrow();
        if (target != null && !"PENDING".equals(target.getStatus()))
            throw exception(PROJECT_STAGE_ADVANCE_INVALID, "GRAPH_TARGET_STALE");
        var contract = byStage.get(current.getId());
        var rule = new FrozenDefinitions(contract.getDefinitionSnapshot()).require(contract.getCompletionRuleRevisionId(), "COMPLETION_RULE");
        var completion = evaluator.evaluate(rule, new ProjectRuntimeRuleEvaluator.Facts(project, current, tasks, allGates, allRefs, true));
        List<ProjectGateInstanceDO> gates = allGates.stream().filter(g ->
                (Objects.equals(g.getStageCode(), current.getStageCode()) && "EXIT".equals(g.getGateType()))
                || (target != null && Objects.equals(g.getStageCode(), target.getStageCode()) && "ENTRY".equals(g.getGateType()))).toList();
        Set<Long> gateIds = gates.stream().map(ProjectGateInstanceDO::getId).collect(Collectors.toSet());
        var refs = allRefs.stream().filter(r -> gateIds.contains(r.getGateId())).toList();
        return new Resolution(current, target, transition, completion, gates, refs);
    }
}
