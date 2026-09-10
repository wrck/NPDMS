package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.*;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** PM-01/PM-03: append graph and execution contracts inside the project creation transaction. */
@Service @RequiredArgsConstructor
public class ProjectRuntimeGraphFreezer {
    private final ProjectRuntimeGraphMapper graphMapper;
    private final ProjectStageExecutionContractMapper contractMapper;

    public void validate(TemplateDefinitionContent content) {
        var definitions = new FrozenDefinitions(content.getDefinitionSnapshot());
        StageTransitionGraph graph = new StageTransitionGraph(content.getStages().stream()
                .map(s -> new StageTransitionGraph.Stage(s.getStageCode(), s.getStart(), s.getTerminal())).toList(),
                content.getTransitions().stream().map(e -> new StageTransitionDefinition(e.getTransitionCode(),
                        e.getFromStageCode(), e.getToStageCode(), e.getConditionRuleRevisionId(),
                        e.getPriority(), e.getDefaultBranch())).toList());
        var failures = StageTransitionGraphValidator.validate(graph);
        if (!failures.isEmpty() || graph.stages().stream().noneMatch(s -> "S0".equals(s.stageCode()) && Boolean.TRUE.equals(s.start())))
            throw new IllegalArgumentException("INVALID_FROZEN_GRAPH: " + failures);
        for (var stage : content.getStages()) {
            definitions.require(stage.getDefinitionRevisionId(), "STAGE");
            definitions.require(stage.getWorkBindingRevisionId(), "WORK_BINDING");
            definitions.require(stage.getPermissionPolicyRevisionId(), "PERMISSION_POLICY");
            definitions.require(stage.getCompletionRuleRevisionId(), "COMPLETION_RULE");
        }
        for (var task : content.getTasks()) {
            if ("S0".equals(task.getStageCode())) {
                throw new IllegalArgumentException("S0不生成任务，请通过项目基本功能办理；模板任务：" + task.getTaskCode());
            }
            definitions.require(task.getDefinitionRevisionId(), "TASK");
            definitions.require(task.getWorkBindingRevisionId(), "WORK_BINDING");
            definitions.require(task.getPermissionPolicyRevisionId(), "PERMISSION_POLICY");
            definitions.require(task.getCompletionRuleRevisionId(), "COMPLETION_RULE");
        }
        Set<Long> identities = new HashSet<>();
        for (var edge : content.getTransitions()) {
            if (edge.getId() == null || edge.getId() <= 0 || !identities.add(edge.getId())
                    || edge.getRevisionNo() == null || edge.getRevisionNo() <= 0)
                throw new IllegalArgumentException("SOURCE_TRANSITION_IDENTITY_REQUIRED");
            if (edge.getConditionRuleRevisionId() != null)
                definitions.require(edge.getConditionRuleRevisionId(), "COMPLETION_RULE");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void freeze(Long tenantId, Long projectId, Long templateRevisionId, TemplateDefinitionContent content,
                       List<ProjectStageInstanceDO> stages, LocalDateTime now) {
        validate(content);
        FrozenDefinitions definitions = new FrozenDefinitions(content.getDefinitionSnapshot());
        Map<String, ProjectStageInstanceDO> byCode = stages.stream().collect(Collectors.toMap(ProjectStageInstanceDO::getStageCode, s -> s));
        for (var definition : content.getStages()) {
            var stage = byCode.get(definition.getStageCode());
            if (stage == null || stage.getId() == null || !Objects.equals(projectId, stage.getProjectId()))
                throw new IllegalArgumentException("STAGE_INSTANCE_IDENTITY_REQUIRED");
            var contract = new ProjectStageExecutionContractDO();
            contract.setTenantId(tenantId); contract.setProjectId(projectId); contract.setStageId(stage.getId());
            contract.setGraphVersion(1L); contract.setDefinitionRevisionId(definition.getDefinitionRevisionId());
            contract.setWorkBindingRevisionId(definition.getWorkBindingRevisionId()); contract.setBindingVersion(1);
            var binding = definitions.require(definition.getWorkBindingRevisionId(), "WORK_BINDING");
            contract.setBindingType(binding.path("bindingType").asText());
            contract.setBindingSnapshot(JsonUtils.toJsonString(binding));
            contract.setPermissionPolicyRevisionId(definition.getPermissionPolicyRevisionId());
            contract.setCompletionRuleRevisionId(definition.getCompletionRuleRevisionId());
            contract.setDefinitionSnapshot(JsonUtils.toJsonString(content.getDefinitionSnapshot()));
            // V220 stores DATETIME(0): avoid MySQL rounding a fractional instant into the next
            // second, which would hide a just-created contract from the current-contract query.
            contract.setEffectiveFrom(now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)); contract.setVersion(0);
            if (contractMapper.insert(contract) != 1) throw new IllegalStateException("STAGE_CONTRACT_FREEZE_FAILED");
        }
        for (var definition : content.getTransitions()) {
            var edge = new ProjectStageTransitionDO();
            edge.setTenantId(tenantId); edge.setProjectId(projectId); edge.setTemplateRevisionId(templateRevisionId);
            edge.setSourceTransitionId(definition.getId()); edge.setTransitionCode(definition.getTransitionCode());
            edge.setTransitionRevision(definition.getRevisionNo()); edge.setFromStageId(byCode.get(definition.getFromStageCode()).getId());
            edge.setToStageId(byCode.get(definition.getToStageCode()).getId()); edge.setPriority(definition.getPriority());
            edge.setIsDefault(definition.getDefaultBranch()); edge.setConditionRuleRevisionId(definition.getConditionRuleRevisionId());
            if (definition.getConditionRuleRevisionId() != null)
                edge.setConditionSnapshot(JsonUtils.toJsonString(definitions.require(definition.getConditionRuleRevisionId(), "COMPLETION_RULE")));
            edge.setGraphVersion(1L);
            if (graphMapper.insert(edge) != 1) throw new IllegalStateException("STAGE_GRAPH_FREEZE_FAILED");
        }
    }
}
