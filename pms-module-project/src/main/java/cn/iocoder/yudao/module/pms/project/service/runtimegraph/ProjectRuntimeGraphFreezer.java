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
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
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
 * PM-01/PM-03 runtime graph freezer for newly created projects.
 *
 * <p>New writes are interpreted only from the immutable V2 {@link TemplateExecutionSnapshot}.
 * The TemplateDefinitionContent overloads are temporary in-process adapters for unchanged Project
 * instantiation code and only unwrap the embedded execution snapshot; they never read legacy
 * DefinitionRevision/definitionSnapshot data. Historical legacy projects are read from their
 * already-frozen contracts by {@link ProjectRuntimeGraphResolver}.</p>
 */
@Service
@RequiredArgsConstructor
public class ProjectRuntimeGraphFreezer {
    /** Runtime graph version remains 1 because project Stage rows already freeze this value. */
    private static final long GRAPH_VERSION = 1L;
    /** Old PM-01 bootstrap mapped these codes directly to ACC. V2 must express ACC by binding/facts instead. */
    private static final Set<String> LEGACY_ACCEPTANCE_TASK_CODES = Set.of("T-INITIAL-ACCEPT", "T-FINAL-ACCEPT");

    private final ProjectRuntimeGraphMapper graphMapper;
    private final ProjectStageExecutionContractMapper contractMapper;

    /** Compatibility overload: accepts only a V2 runtime projection carrying the full immutable snapshot. */
    public void validate(TemplateDefinitionContent content) {
        validate(requireExecutionSnapshot(content));
    }

    public void validate(TemplateExecutionSnapshot snapshot) {
        if (snapshot == null || snapshot.getExecutionSchemaVersion() == null
                || snapshot.getExecutionSchemaVersion() < TemplateExecutionSnapshot.SCHEMA_VERSION) {
            throw new IllegalArgumentException("EXECUTION_SNAPSHOT_V2_REQUIRED");
        }
        if (snapshot.getStages() == null || snapshot.getTasks() == null || snapshot.getTransitions() == null) {
            throw new IllegalArgumentException("COMPILED_TEMPLATE_COLLECTION_REQUIRED");
        }
        StageTransitionGraph graph = graph(snapshot);
        List<StageTransitionGraphValidator.Failure> failures = StageTransitionGraphValidator.validate(graph);
        if (!failures.isEmpty()) throw new IllegalArgumentException("INVALID_COMPILED_GRAPH: " + failures);
        if (snapshot.getStages().stream().noneMatch(stage -> stage != null && "S0".equals(stage.getCode())
                && Boolean.TRUE.equals(stage.getStart()))) {
            throw new IllegalArgumentException("INVALID_COMPILED_GRAPH: S0 start required");
        }

        Set<String> nodeKeys = new HashSet<>();
        for (TemplateExecutionSnapshot.StageContract stage : snapshot.getStages()) {
            if (stage == null || blank(stage.getNodeKey()) || !nodeKeys.add(stage.getNodeKey())) {
                throw new IllegalArgumentException("COMPILED_STAGE_NODE_KEY_REQUIRED");
            }
            if (stage.getBinding() == null) throw new IllegalArgumentException("COMPILED_STAGE_BINDING_REQUIRED");
            if (stage.getPermission() == null) throw new IllegalArgumentException("COMPILED_STAGE_PERMISSION_REQUIRED");
            requireObject(stage.getCompletionRule(), "COMPILED_STAGE_COMPLETION_RULE_REQUIRED");
        }
        for (TemplateExecutionSnapshot.TaskContract task : snapshot.getTasks()) {
            if (task == null) throw new IllegalArgumentException("COMPILED_TASK_REQUIRED");
            if ("S0".equals(task.getStageCode())) throw new IllegalArgumentException("S0不生成任务");
            if (blank(task.getNodeKey()) || !nodeKeys.add(task.getNodeKey())) {
                throw new IllegalArgumentException("COMPILED_TASK_NODE_KEY_REQUIRED");
            }
            if (LEGACY_ACCEPTANCE_TASK_CODES.contains(task.getCode()) && task.getSourceDefinitionRevisionId() == null) {
                throw new IllegalArgumentException("V2模板不得使用旧验收任务保留码：" + task.getCode()
                        + "；请用ACC WorkBinding/CompletionRule显式表达验收");
            }
            if (task.getBinding() == null || blank(task.getBinding().getType())) {
                throw new IllegalArgumentException("COMPILED_TASK_BINDING_REQUIRED");
            }
            if (task.getPermission() == null) throw new IllegalArgumentException("COMPILED_TASK_PERMISSION_REQUIRED");
            requireObject(task.getCompletionRule(), "COMPILED_TASK_COMPLETION_RULE_REQUIRED");
        }

        Set<String> edgeKeys = new HashSet<>();
        for (TemplateExecutionSnapshot.TransitionContract edge : snapshot.getTransitions()) {
            if (edge == null || blank(edge.getEdgeKey()) || !edgeKeys.add(edge.getEdgeKey())) {
                throw new IllegalArgumentException("COMPILED_TRANSITION_KEY_REQUIRED");
            }
            if (Boolean.TRUE.equals(edge.getDefaultBranch()) && edge.getConditionRule() != null) {
                throw new IllegalArgumentException("DEFAULT_BRANCH_MUST_NOT_HAVE_CONDITION");
            }
            if (edge.getConditionRule() != null) {
                requireObject(edge.getConditionRule(), "COMPILED_TRANSITION_CONDITION_INVALID");
            }
        }
    }

    /** Compatibility overload: unwraps the embedded V2 snapshot and delegates to the native freezer. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void freeze(Long tenantId, Long projectId, Long templateRevisionId, TemplateDefinitionContent content,
                       List<ProjectStageInstanceDO> stages, LocalDateTime now) {
        freeze(tenantId, projectId, templateRevisionId, requireExecutionSnapshot(content), stages, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void freeze(Long tenantId, Long projectId, Long templateRevisionId, TemplateExecutionSnapshot snapshot,
                       List<ProjectStageInstanceDO> stages, LocalDateTime now) {
        validate(snapshot);
        Map<String, ProjectStageInstanceDO> byCode = stages.stream()
                .collect(Collectors.toMap(ProjectStageInstanceDO::getStageCode, stage -> stage));
        String executionSnapshot = JsonUtils.toJsonString(snapshot);
        for (TemplateExecutionSnapshot.StageContract definition : snapshot.getStages()) {
            ProjectStageInstanceDO stage = requireStage(byCode, projectId, definition.getCode());
            ProjectStageExecutionContractDO contract = new ProjectStageExecutionContractDO();
            contract.setTenantId(tenantId);
            contract.setProjectId(projectId);
            contract.setStageId(stage.getId());
            contract.setSourceNodeKey(definition.getNodeKey());
            contract.setGraphVersion(GRAPH_VERSION);
            // Legacy ids are provenance only; runtime evaluates the frozen V2 payloads below.
            contract.setDefinitionRevisionId(definition.getSourceDefinitionRevisionId());
            contract.setWorkBindingRevisionId(definition.getSourceWorkBindingRevisionId());
            contract.setBindingVersion(1);
            contract.setBindingType(definition.getBinding().getType());
            contract.setBindingSnapshot(JsonUtils.toJsonString(definition.getBinding()));
            contract.setPermissionSnapshot(JsonUtils.toJsonString(definition.getPermission()));
            contract.setPermissionPolicyRevisionId(definition.getSourcePermissionPolicyRevisionId());
            contract.setCompletionRuleSnapshot(JsonUtils.toJsonString(definition.getCompletionRule()));
            contract.setCompletionRuleRevisionId(definition.getSourceCompletionRuleRevisionId());
            // Immutable publication evidence only; V2 resolver does not reinterpret it.
            contract.setDefinitionSnapshot(executionSnapshot);
            contract.setEffectiveFrom(truncate(now));
            contract.setVersion(0);
            if (contractMapper.insert(contract) != 1) throw new IllegalStateException("STAGE_CONTRACT_FREEZE_FAILED");
        }
        for (TemplateExecutionSnapshot.TransitionContract definition : snapshot.getTransitions()) {
            ProjectStageTransitionDO edge = new ProjectStageTransitionDO();
            edge.setTenantId(tenantId);
            edge.setProjectId(projectId);
            edge.setTemplateRevisionId(templateRevisionId);
            edge.setSourceTransitionKey(definition.getEdgeKey());
            edge.setSourceTransitionId(definition.getSourceTransitionId());
            edge.setTransitionCode(definition.getCode());
            edge.setTransitionRevision(definition.getSourceTransitionRevisionNo());
            edge.setFromStageId(requireStage(byCode, projectId, definition.getFromStageCode()).getId());
            edge.setToStageId(requireStage(byCode, projectId, definition.getToStageCode()).getId());
            edge.setPriority(definition.getPriority());
            edge.setIsDefault(definition.getDefaultBranch());
            edge.setConditionRuleRevisionId(definition.getSourceConditionRuleRevisionId());
            edge.setConditionSnapshot(definition.getConditionRule() == null ? null
                    : JsonUtils.toJsonString(definition.getConditionRule()));
            edge.setGraphVersion(GRAPH_VERSION);
            if (graphMapper.insert(edge) != 1) throw new IllegalStateException("STAGE_GRAPH_FREEZE_FAILED");
        }
    }

    private TemplateExecutionSnapshot requireExecutionSnapshot(TemplateDefinitionContent content) {
        if (content == null || content.getExecutionSnapshot() == null) {
            throw new IllegalArgumentException("EXECUTION_SNAPSHOT_V2_REQUIRED");
        }
        TemplateExecutionSnapshot snapshot = JsonUtils.parseObject(
                JsonUtils.toJsonString(content.getExecutionSnapshot()), TemplateExecutionSnapshot.class);
        if (snapshot == null) throw new IllegalArgumentException("EXECUTION_SNAPSHOT_V2_REQUIRED");
        return snapshot;
    }

    private StageTransitionGraph graph(TemplateExecutionSnapshot snapshot) {
        return new StageTransitionGraph(snapshot.getStages().stream()
                .map(stage -> new StageTransitionGraph.Stage(stage.getCode(), stage.getStart(), stage.getTerminal()))
                .toList(), snapshot.getTransitions().stream().map(edge -> new StageTransitionDefinition(edge.getCode(),
                        edge.getFromStageCode(), edge.getToStageCode(),
                        edge.getConditionRule() == null ? null : 1L,
                        edge.getPriority(), edge.getDefaultBranch())).toList());
    }

    private ProjectStageInstanceDO requireStage(Map<String, ProjectStageInstanceDO> byCode,
                                                Long projectId, String stageCode) {
        ProjectStageInstanceDO stage = byCode.get(stageCode);
        if (stage == null || stage.getId() == null || !Objects.equals(projectId, stage.getProjectId())) {
            throw new IllegalArgumentException("STAGE_INSTANCE_IDENTITY_REQUIRED");
        }
        return stage;
    }

    private LocalDateTime truncate(LocalDateTime value) {
        return value.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }

    private void requireObject(JsonNode value, String message) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException(message);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
