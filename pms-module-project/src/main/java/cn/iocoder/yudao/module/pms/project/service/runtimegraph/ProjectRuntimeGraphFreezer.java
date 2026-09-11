package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectStageExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionDefinition;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionGraph;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionGraphValidator;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PM-01/PM-03 runtime graph freezer.
 *
 * <p>V2 consumes server-generated snapshots carried by TemplateDefinitionContent and never resolves
 * DeliveryDefinition revisions. The legacy branch is retained only for historical templates.</p>
 */
@Service
@RequiredArgsConstructor
public class ProjectRuntimeGraphFreezer {
    /** Runtime graph version remains 1 because the project Stage rows already freeze this value. */
    private static final long GRAPH_VERSION = 1L;

    private final ProjectRuntimeGraphMapper graphMapper;
    private final ProjectStageExecutionContractMapper contractMapper;

    public void validate(TemplateDefinitionContent content) {
        if (isV2(content)) validateV2(content);
        else validateLegacy(content);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void freeze(Long tenantId, Long projectId, Long templateRevisionId, TemplateDefinitionContent content,
                       List<ProjectStageInstanceDO> stages, LocalDateTime now) {
        validate(content);
        if (isV2(content)) freezeV2(tenantId, projectId, templateRevisionId, content, stages, now);
        else freezeLegacy(tenantId, projectId, templateRevisionId, content, stages, now);
    }

    private boolean isV2(TemplateDefinitionContent content) {
        return content != null && content.getExecutionSnapshot() != null
                && content.getExecutionSnapshot().path("executionSchemaVersion").asInt() >= 2;
    }

    private void validateV2(TemplateDefinitionContent content) {
        StageTransitionGraph graph = graph(content, true);
        List<StageTransitionGraphValidator.Failure> failures = StageTransitionGraphValidator.validate(graph);
        if (!failures.isEmpty()) throw new IllegalArgumentException("INVALID_COMPILED_GRAPH: " + failures);
        if (content.getStages().stream().noneMatch(stage -> "S0".equals(stage.getStageCode())
                && Boolean.TRUE.equals(stage.getStart()))) {
            throw new IllegalArgumentException("INVALID_COMPILED_GRAPH: S0 start required");
        }
        Set<String> nodeKeys = new HashSet<>();
        for (TemplateDefinitionContent.StageDef stage : content.getStages()) {
            if (stage == null || blank(stage.getSourceNodeKey()) || !nodeKeys.add(stage.getSourceNodeKey()))
                throw new IllegalArgumentException("COMPILED_STAGE_NODE_KEY_REQUIRED");
            requireObject(stage.getBindingSnapshot(), "COMPILED_STAGE_BINDING_REQUIRED");
            requireObject(stage.getPermissionSnapshot(), "COMPILED_STAGE_PERMISSION_REQUIRED");
            requireObject(stage.getCompletionRuleSnapshot(), "COMPILED_STAGE_COMPLETION_RULE_REQUIRED");
        }
        for (TemplateDefinitionContent.TaskDef task : content.getTasks()) {
            if (task == null || "S0".equals(task.getStageCode()))
                throw new IllegalArgumentException("S0不生成任务");
            if (blank(task.getSourceNodeKey()) || !nodeKeys.add(task.getSourceNodeKey()))
                throw new IllegalArgumentException("COMPILED_TASK_NODE_KEY_REQUIRED");
            if (blank(task.getWorkBindingTypeCode()) || blank(task.getPermissionPolicyRef())
                    || blank(task.getCompletionRuleTypeCode()) || blank(task.getBindingConfig())
                    || blank(task.getCompletionRuleConfig()))
                throw new IllegalArgumentException("COMPILED_TASK_CONTRACT_REQUIRED");
        }
        Set<String> edgeKeys = new HashSet<>();
        for (TemplateDefinitionContent.TransitionDef edge : content.getTransitions()) {
            if (edge == null || blank(edge.getSourceTransitionKey()) || !edgeKeys.add(edge.getSourceTransitionKey()))
                throw new IllegalArgumentException("COMPILED_TRANSITION_KEY_REQUIRED");
            if (Boolean.TRUE.equals(edge.getDefaultBranch()) && edge.getConditionRuleSnapshot() != null)
                throw new IllegalArgumentException("DEFAULT_BRANCH_MUST_NOT_HAVE_CONDITION");
        }
    }

    private void freezeV2(Long tenantId, Long projectId, Long templateRevisionId,
                          TemplateDefinitionContent content, List<ProjectStageInstanceDO> stages, LocalDateTime now) {
        Map<String, ProjectStageInstanceDO> byCode = stages.stream()
                .collect(Collectors.toMap(ProjectStageInstanceDO::getStageCode, stage -> stage));
        String executionSnapshot = JsonUtils.toJsonString(content.getExecutionSnapshot());
        for (TemplateDefinitionContent.StageDef definition : content.getStages()) {
            ProjectStageInstanceDO stage = requireStage(byCode, projectId, definition.getStageCode());
            ProjectStageExecutionContractDO contract = new ProjectStageExecutionContractDO();
            contract.setTenantId(tenantId);
            contract.setProjectId(projectId);
            contract.setStageId(stage.getId());
            contract.setSourceNodeKey(definition.getSourceNodeKey());
            contract.setGraphVersion(GRAPH_VERSION);
            contract.setDefinitionRevisionId(definition.getDefinitionRevisionId());
            contract.setWorkBindingRevisionId(definition.getWorkBindingRevisionId());
            contract.setBindingVersion(1);
            contract.setBindingType(definition.getBindingSnapshot().path("type").asText());
            contract.setBindingSnapshot(JsonUtils.toJsonString(definition.getBindingSnapshot()));
            contract.setPermissionSnapshot(JsonUtils.toJsonString(definition.getPermissionSnapshot()));
            contract.setPermissionPolicyRevisionId(definition.getPermissionPolicyRevisionId());
            contract.setCompletionRuleSnapshot(JsonUtils.toJsonString(definition.getCompletionRuleSnapshot()));
            contract.setCompletionRuleRevisionId(definition.getCompletionRuleRevisionId());
            contract.setDefinitionSnapshot(executionSnapshot);
            contract.setEffectiveFrom(truncate(now));
            contract.setVersion(0);
            if (contractMapper.insert(contract) != 1) throw new IllegalStateException("STAGE_CONTRACT_FREEZE_FAILED");
        }
        for (TemplateDefinitionContent.TransitionDef definition : content.getTransitions()) {
            ProjectStageTransitionDO edge = new ProjectStageTransitionDO();
            edge.setTenantId(tenantId);
            edge.setProjectId(projectId);
            edge.setTemplateRevisionId(templateRevisionId);
            edge.setSourceTransitionKey(definition.getSourceTransitionKey());
            edge.setSourceTransitionId(definition.getId());
            edge.setTransitionCode(definition.getTransitionCode());
            edge.setTransitionRevision(definition.getRevisionNo());
            edge.setFromStageId(requireStage(byCode, projectId, definition.getFromStageCode()).getId());
            edge.setToStageId(requireStage(byCode, projectId, definition.getToStageCode()).getId());
            edge.setPriority(definition.getPriority());
            edge.setIsDefault(definition.getDefaultBranch());
            edge.setConditionRuleRevisionId(definition.getConditionRuleRevisionId());
            edge.setConditionSnapshot(definition.getConditionRuleSnapshot() == null ? null
                    : JsonUtils.toJsonString(definition.getConditionRuleSnapshot()));
            edge.setGraphVersion(GRAPH_VERSION);
            if (graphMapper.insert(edge) != 1) throw new IllegalStateException("STAGE_GRAPH_FREEZE_FAILED");
        }
    }

    private void validateLegacy(TemplateDefinitionContent content) {
        FrozenDefinitions definitions = new FrozenDefinitions(content.getDefinitionSnapshot());
        StageTransitionGraph graph = graph(content, false);
        List<StageTransitionGraphValidator.Failure> failures = StageTransitionGraphValidator.validate(graph);
        if (!failures.isEmpty() || graph.stages().stream().noneMatch(stage -> "S0".equals(stage.stageCode())
                && Boolean.TRUE.equals(stage.start()))) {
            throw new IllegalArgumentException("INVALID_FROZEN_GRAPH: " + failures);
        }
        for (TemplateDefinitionContent.StageDef stage : content.getStages()) {
            definitions.require(stage.getDefinitionRevisionId(), "STAGE");
            definitions.require(stage.getWorkBindingRevisionId(), "WORK_BINDING");
            definitions.require(stage.getPermissionPolicyRevisionId(), "PERMISSION_POLICY");
            definitions.require(stage.getCompletionRuleRevisionId(), "COMPLETION_RULE");
        }
        for (TemplateDefinitionContent.TaskDef task : content.getTasks()) {
            if ("S0".equals(task.getStageCode())) throw new IllegalArgumentException("S0不生成任务，请通过项目基本功能办理；模板任务：" + task.getTaskCode());
            definitions.require(task.getDefinitionRevisionId(), "TASK");
            definitions.require(task.getWorkBindingRevisionId(), "WORK_BINDING");
            definitions.require(task.getPermissionPolicyRevisionId(), "PERMISSION_POLICY");
            definitions.require(task.getCompletionRuleRevisionId(), "COMPLETION_RULE");
        }
        Set<Long> identities = new HashSet<>();
        for (TemplateDefinitionContent.TransitionDef edge : content.getTransitions()) {
            if (edge.getId() == null || edge.getId() <= 0 || !identities.add(edge.getId())
                    || edge.getRevisionNo() == null || edge.getRevisionNo() <= 0)
                throw new IllegalArgumentException("SOURCE_TRANSITION_IDENTITY_REQUIRED");
            if (edge.getConditionRuleRevisionId() != null)
                definitions.require(edge.getConditionRuleRevisionId(), "COMPLETION_RULE");
        }
    }

    private void freezeLegacy(Long tenantId, Long projectId, Long templateRevisionId,
                              TemplateDefinitionContent content, List<ProjectStageInstanceDO> stages, LocalDateTime now) {
        FrozenDefinitions definitions = new FrozenDefinitions(content.getDefinitionSnapshot());
        Map<String, ProjectStageInstanceDO> byCode = stages.stream()
                .collect(Collectors.toMap(ProjectStageInstanceDO::getStageCode, stage -> stage));
        for (TemplateDefinitionContent.StageDef definition : content.getStages()) {
            ProjectStageInstanceDO stage = requireStage(byCode, projectId, definition.getStageCode());
            ProjectStageExecutionContractDO contract = new ProjectStageExecutionContractDO();
            contract.setTenantId(tenantId);
            contract.setProjectId(projectId);
            contract.setStageId(stage.getId());
            contract.setGraphVersion(GRAPH_VERSION);
            contract.setDefinitionRevisionId(definition.getDefinitionRevisionId());
            contract.setWorkBindingRevisionId(definition.getWorkBindingRevisionId());
            contract.setBindingVersion(1);
            JsonNode binding = definitions.require(definition.getWorkBindingRevisionId(), "WORK_BINDING");
            contract.setBindingType(binding.path("bindingType").asText());
            contract.setBindingSnapshot(JsonUtils.toJsonString(binding));
            contract.setPermissionPolicyRevisionId(definition.getPermissionPolicyRevisionId());
            contract.setCompletionRuleRevisionId(definition.getCompletionRuleRevisionId());
            contract.setDefinitionSnapshot(JsonUtils.toJsonString(content.getDefinitionSnapshot()));
            contract.setEffectiveFrom(truncate(now));
            contract.setVersion(0);
            if (contractMapper.insert(contract) != 1) throw new IllegalStateException("STAGE_CONTRACT_FREEZE_FAILED");
        }
        for (TemplateDefinitionContent.TransitionDef definition : content.getTransitions()) {
            ProjectStageTransitionDO edge = new ProjectStageTransitionDO();
            edge.setTenantId(tenantId);
            edge.setProjectId(projectId);
            edge.setTemplateRevisionId(templateRevisionId);
            edge.setSourceTransitionId(definition.getId());
            edge.setTransitionCode(definition.getTransitionCode());
            edge.setTransitionRevision(definition.getRevisionNo());
            edge.setFromStageId(requireStage(byCode, projectId, definition.getFromStageCode()).getId());
            edge.setToStageId(requireStage(byCode, projectId, definition.getToStageCode()).getId());
            edge.setPriority(definition.getPriority());
            edge.setIsDefault(definition.getDefaultBranch());
            edge.setConditionRuleRevisionId(definition.getConditionRuleRevisionId());
            if (definition.getConditionRuleRevisionId() != null)
                edge.setConditionSnapshot(JsonUtils.toJsonString(
                        definitions.require(definition.getConditionRuleRevisionId(), "COMPLETION_RULE")));
            edge.setGraphVersion(GRAPH_VERSION);
            if (graphMapper.insert(edge) != 1) throw new IllegalStateException("STAGE_GRAPH_FREEZE_FAILED");
        }
    }

    private StageTransitionGraph graph(TemplateDefinitionContent content, boolean v2) {
        return new StageTransitionGraph(content.getStages().stream()
                .map(stage -> new StageTransitionGraph.Stage(stage.getStageCode(), stage.getStart(), stage.getTerminal())).toList(),
                content.getTransitions().stream().map(edge -> new StageTransitionDefinition(edge.getTransitionCode(),
                        edge.getFromStageCode(), edge.getToStageCode(),
                        v2 ? (edge.getConditionRuleSnapshot() == null ? null : 1L) : edge.getConditionRuleRevisionId(),
                        edge.getPriority(), edge.getDefaultBranch())).toList());
    }

    private ProjectStageInstanceDO requireStage(Map<String, ProjectStageInstanceDO> byCode,
                                                Long projectId, String stageCode) {
        ProjectStageInstanceDO stage = byCode.get(stageCode);
        if (stage == null || stage.getId() == null || !Objects.equals(projectId, stage.getProjectId()))
            throw new IllegalArgumentException("STAGE_INSTANCE_IDENTITY_REQUIRED");
        return stage;
    }

    private LocalDateTime truncate(LocalDateTime value) {
        return value.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }

    private void requireObject(JsonNode value, String message) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException(message);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
