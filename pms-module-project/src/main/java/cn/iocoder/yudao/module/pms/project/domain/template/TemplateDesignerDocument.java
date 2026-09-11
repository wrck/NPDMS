package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import lombok.Data;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PM-03 V2 template authoring truth.
 *
 * <p>The designer model is intentionally business-oriented. DeliveryDefinition revision ids are
 * retained only in {@link SourcePin} for import/audit compatibility; compiler output must contain
 * all runtime semantics so project runtime never needs to resolve those ids again.</p>
 */
@Data
public class TemplateDesignerDocument {

    public static final int SCHEMA_VERSION = 2;

    private Integer schemaVersion = SCHEMA_VERSION;
    private Match match = new Match();
    private String processDefinitionKey;
    private JsonNode closurePolicy;
    private List<StageNode> stages = new ArrayList<>();
    private List<TaskNode> tasks = new ArrayList<>();
    private List<MilestoneNode> milestones = new ArrayList<>();
    private List<DeliverableNode> deliverables = new ArrayList<>();
    private List<GateNode> gates = new ArrayList<>();
    private List<TransitionNode> transitions = new ArrayList<>();
    private List<RuleAsset> ruleAssets = new ArrayList<>();
    private DesignerLayout layout = new DesignerLayout();

    /** Immutable import evidence only; excluded from runtime interpretation and semantic hashing. */
    private JsonNode sourceEvidence;

    @Data
    public static class Match {
        private String signingMethod;
        private String projectCategory;
        private String implementationMethod;
        private String majorProjectLevel;
    }

    @Data
    public static class StageNode {
        private String nodeKey;
        private String code;
        private String name;
        private Integer sortOrder;
        private String entryCriteria;
        private String exitCriteria;
        private Boolean start;
        private Boolean terminal;
        private WorkBindingSpec workBinding;
        private PermissionRequirement permission;
        private RuleSpec completionRule;
        private SourcePin source;
    }

    @Data
    public static class TaskNode {
        private String nodeKey;
        private String code;
        private String name;
        private String parentTaskCode;
        private String stageCode;
        private Integer priority;
        private Integer sortOrder;
        private java.math.BigDecimal estimatedHours;
        private String satisfactionTiming;
        private String description;
        private WorkBindingSpec workBinding;
        private PermissionRequirement permission;
        private RuleSpec completionRule;
        private String gateRef;
        private SourcePin source;
    }

    @Data
    public static class MilestoneNode {
        private String nodeKey;
        private String code;
        private String name;
        private String stageCode;
        private String timing;
        private String criteria;
        private JsonNode configuration;
        private SourcePin source;
    }

    @Data
    public static class DeliverableNode {
        private String nodeKey;
        private String code;
        private String name;
        private String stageCode;
        private String taskCode;
        private Boolean required;
        private JsonNode configuration;
        private SourcePin source;
    }

    @Data
    public static class GateNode {
        private String nodeKey;
        private String code;
        private String name;
        private String gateType;
        private String stageCode;
        private String description;
        private List<GateReference> references = new ArrayList<>();
        private SourcePin source;
    }

    @Data
    public static class GateReference {
        private String refType;
        private String refCode;
        private String refVersion;
    }

    @Data
    public static class TransitionNode {
        private String edgeKey;
        private String code;
        private String fromStageCode;
        private String toStageCode;
        private RuleSpec condition;
        private Integer priority;
        private Boolean defaultBranch;
        private SourcePin source;
    }

    @Data
    public static class WorkBindingSpec {
        private String type;
        private String targetContextCode;
        private String targetObjectType;
        private String targetObjectKey;
        private String componentKey;
        private Long dynamicFormRevisionId;
        private String approvalDefinitionKey;
        private JsonNode parameters;
        private JsonNode businessViewSnapshot;
        private Long sourceRevisionId;
    }

    @Data
    public static class PermissionRequirement {
        private String policyRef;
        private JsonNode policySnapshot;
        private Long sourceRevisionId;
    }

    @Data
    public static class RuleSpec {
        private JsonNode expression;
        private Long sourceRevisionId;
    }

    @Data
    public static class RuleAsset {
        private String key;
        private String name;
        private RuleSpec rule;
    }

    @Data
    public static class DesignerLayout {
        private Map<String, JsonNode> nodes = new LinkedHashMap<>();
        private Map<String, JsonNode> views = new LinkedHashMap<>();
    }

    @Data
    public static class SourcePin {
        private Long definitionRevisionId;
        private Long workBindingRevisionId;
        private Long permissionPolicyRevisionId;
        private Long completionRuleRevisionId;
        private Long transitionId;
        private Long transitionRevisionNo;
    }

    /**
     * Convert a fully-resolved legacy template into the V2 designer model.
     * Caller must run TemplateDefinitionReferenceAssembler.resolve first.
     */
    public static TemplateDesignerDocument fromResolvedLegacy(TemplateDefinitionContent content) {
        Objects.requireNonNull(content, "template content");
        TemplateDesignerDocument result = new TemplateDesignerDocument();
        result.getMatch().setSigningMethod(content.getSigningMethod());
        result.getMatch().setProjectCategory(content.getProjectCategory());
        result.getMatch().setImplementationMethod(content.getImplementationMethod());
        result.getMatch().setMajorProjectLevel(content.getMajorProjectLevel());
        result.setProcessDefinitionKey(content.getProcessDefinitionKey());
        result.setClosurePolicy(content.getClosurePolicy() == null ? null : content.getClosurePolicy().toJson());
        result.setSourceEvidence(content.getDefinitionSnapshot());

        DefinitionSnapshotIndex definitions = new DefinitionSnapshotIndex(content.getDefinitionSnapshot());
        if (content.getStages() != null) {
            for (TemplateDefinitionContent.StageDef source : content.getStages()) {
                if (source == null) continue;
                StageNode stage = new StageNode();
                stage.setNodeKey("stage:" + source.getStageCode());
                stage.setCode(source.getStageCode());
                stage.setName(source.getName());
                stage.setSortOrder(source.getSortOrder());
                stage.setEntryCriteria(source.getEntryCriteria());
                stage.setExitCriteria(source.getExitCriteria());
                stage.setStart(source.getStart());
                stage.setTerminal(source.getTerminal());
                stage.setWorkBinding(definitions.binding(source.getWorkBindingRevisionId()));
                stage.setPermission(definitions.permission(source.getPermissionPolicyRevisionId()));
                stage.setCompletionRule(definitions.rule(source.getCompletionRuleRevisionId()));
                stage.setSource(sourcePin(source.getDefinitionRevisionId(), source.getWorkBindingRevisionId(),
                        source.getPermissionPolicyRevisionId(), source.getCompletionRuleRevisionId(), null, null));
                result.getStages().add(stage);
            }
        }
        if (content.getTasks() != null) {
            for (TemplateDefinitionContent.TaskDef source : content.getTasks()) {
                if (source == null) continue;
                TaskNode task = new TaskNode();
                task.setNodeKey("task:" + source.getTaskCode());
                task.setCode(source.getTaskCode());
                task.setName(source.getName());
                task.setParentTaskCode(source.getParentTaskCode());
                task.setStageCode(source.getStageCode());
                task.setPriority(source.getPriority());
                task.setSortOrder(source.getSortOrder());
                task.setEstimatedHours(source.getEstimatedHours());
                task.setSatisfactionTiming(source.getSatisfactionTiming());
                task.setDescription(source.getDescription());
                task.setWorkBinding(binding(source));
                task.setPermission(permission(source, definitions));
                task.setCompletionRule(rule(source, definitions));
                task.setGateRef(source.getGateRef());
                task.setSource(sourcePin(source.getDefinitionRevisionId(), source.getWorkBindingRevisionId(),
                        source.getPermissionPolicyRevisionId(), source.getCompletionRuleRevisionId(), null, null));
                result.getTasks().add(task);
            }
        }
        if (content.getMilestones() != null) {
            for (TemplateDefinitionContent.MilestoneDef source : content.getMilestones()) {
                if (source == null) continue;
                MilestoneNode node = new MilestoneNode();
                node.setNodeKey("milestone:" + source.getMilestoneCode());
                node.setCode(source.getMilestoneCode());
                node.setName(source.getName());
                node.setStageCode(source.getStageCode());
                node.setTiming(source.getTiming());
                node.setCriteria(source.getCriteria());
                node.setConfiguration(definitions.payload(source.getDefinitionRevisionId()));
                node.setSource(sourcePin(source.getDefinitionRevisionId(), null, null, null, null, null));
                result.getMilestones().add(node);
            }
        }
        if (content.getDeliverables() != null) {
            for (TemplateDefinitionContent.DeliverableDef source : content.getDeliverables()) {
                if (source == null) continue;
                DeliverableNode node = new DeliverableNode();
                node.setNodeKey("deliverable:" + source.getDeliverableCode());
                node.setCode(source.getDeliverableCode());
                node.setName(source.getName());
                node.setStageCode(source.getStageCode());
                node.setTaskCode(source.getTaskCode());
                node.setRequired(source.getRequired());
                node.setConfiguration(definitions.payload(source.getDefinitionRevisionId()));
                node.setSource(sourcePin(source.getDefinitionRevisionId(), null, null, null, null, null));
                result.getDeliverables().add(node);
            }
        }
        if (content.getGates() != null) {
            for (TemplateDefinitionContent.GateDef source : content.getGates()) {
                if (source == null) continue;
                GateNode gate = new GateNode();
                gate.setNodeKey("gate:" + source.getGateCode());
                gate.setCode(source.getGateCode());
                gate.setName(source.getName());
                gate.setGateType(source.getGateType());
                gate.setStageCode(source.getStageCode());
                gate.setDescription(source.getDescription());
                if (source.getReferences() != null) for (TemplateDefinitionContent.GateRef reference : source.getReferences()) {
                    if (reference == null) continue;
                    GateReference target = new GateReference();
                    target.setRefType(reference.getRefType());
                    target.setRefCode(reference.getRefCode());
                    target.setRefVersion(reference.getRefVersion());
                    gate.getReferences().add(target);
                }
                gate.setSource(sourcePin(source.getDefinitionRevisionId(), null, null, null, null, null));
                result.getGates().add(gate);
            }
        }
        if (content.getTransitions() != null) {
            for (TemplateDefinitionContent.TransitionDef source : content.getTransitions()) {
                if (source == null) continue;
                TransitionNode edge = new TransitionNode();
                edge.setEdgeKey("transition:" + source.getTransitionCode());
                edge.setCode(source.getTransitionCode());
                edge.setFromStageCode(source.getFromStageCode());
                edge.setToStageCode(source.getToStageCode());
                edge.setCondition(definitions.rule(source.getConditionRuleRevisionId()));
                edge.setPriority(source.getPriority());
                edge.setDefaultBranch(source.getDefaultBranch());
                edge.setSource(sourcePin(null, null, null, source.getConditionRuleRevisionId(),
                        source.getId(), source.getRevisionNo()));
                result.getTransitions().add(edge);
            }
        }
        return result;
    }

    private static WorkBindingSpec binding(TemplateDefinitionContent.TaskDef source) {
        WorkBindingSpec binding = new WorkBindingSpec();
        binding.setType(source.getWorkBindingTypeCode());
        binding.setTargetContextCode(source.getTargetContextCode());
        binding.setTargetObjectType(source.getTargetObjectType());
        binding.setTargetObjectKey(source.getTargetObjectKey());
        binding.setComponentKey(source.getComponentKey());
        binding.setDynamicFormRevisionId(source.getDynamicFormRevisionId());
        binding.setApprovalDefinitionKey(source.getApprovalDefinitionKey());
        binding.setParameters(parseJson(source.getBindingConfig()));
        binding.setSourceRevisionId(source.getWorkBindingRevisionId());
        return binding;
    }

    private static PermissionRequirement permission(TemplateDefinitionContent.TaskDef source,
                                                    DefinitionSnapshotIndex definitions) {
        PermissionRequirement permission = definitions.permission(source.getPermissionPolicyRevisionId());
        if (permission == null) permission = new PermissionRequirement();
        if (permission.getPolicyRef() == null) permission.setPolicyRef(source.getPermissionPolicyRef());
        permission.setSourceRevisionId(source.getPermissionPolicyRevisionId());
        return permission;
    }

    private static RuleSpec rule(TemplateDefinitionContent.TaskDef source, DefinitionSnapshotIndex definitions) {
        RuleSpec fromDefinition = definitions.rule(source.getCompletionRuleRevisionId());
        if (fromDefinition != null && fromDefinition.getExpression() != null) return fromDefinition;
        RuleSpec rule = new RuleSpec();
        rule.setSourceRevisionId(source.getCompletionRuleRevisionId());
        JsonNode config = parseJson(source.getCompletionRuleConfig());
        if (source.getCompletionRuleTypeCode() == null) {
            rule.setExpression(config);
            return rule;
        }
        if ("ALL".equals(source.getCompletionRuleTypeCode()) || "ANY".equals(source.getCompletionRuleTypeCode())) {
            rule.setExpression(config);
            return rule;
        }
        Map<String, Object> expression = new LinkedHashMap<>();
        expression.put("predicate", source.getCompletionRuleTypeCode());
        expression.put("parameters", config == null ? Map.of() : config);
        rule.setExpression(JsonUtils.parseObject(JsonUtils.toJsonString(expression), JsonNode.class));
        return rule;
    }

    private static JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        return JsonUtils.parseObject(json, JsonNode.class);
    }

    private static SourcePin sourcePin(Long definition, Long binding, Long permission, Long completion,
                                       Long transitionId, Long transitionRevisionNo) {
        SourcePin pin = new SourcePin();
        pin.setDefinitionRevisionId(definition);
        pin.setWorkBindingRevisionId(binding);
        pin.setPermissionPolicyRevisionId(permission);
        pin.setCompletionRuleRevisionId(completion);
        pin.setTransitionId(transitionId);
        pin.setTransitionRevisionNo(transitionRevisionNo);
        return pin;
    }

    /** Index for immutable Legacy publication closure copied into a Designer during explicit import. */
    static final class DefinitionSnapshotIndex {
        private final Map<Long, JsonNode> snapshots = new LinkedHashMap<>();

        DefinitionSnapshotIndex(JsonNode source) {
            if (source == null || !source.isArray()) return;
            for (JsonNode item : source) {
                JsonNode definition = item.path("definition");
                if (definition.path("id").canConvertToLong()) snapshots.put(definition.path("id").asLong(), item);
            }
        }

        JsonNode payload(Long id) {
            JsonNode item = id == null ? null : snapshots.get(id);
            return item == null ? null : item.path("definition").path("payload");
        }

        WorkBindingSpec binding(Long id) {
            JsonNode item = id == null ? null : snapshots.get(id);
            if (item == null) return null;
            JsonNode payload = item.path("definition").path("payload");
            WorkBindingSpec binding = new WorkBindingSpec();
            binding.setType(text(payload, "bindingType"));
            binding.setTargetContextCode(text(payload, "targetContextCode"));
            binding.setTargetObjectType(text(payload, "targetObjectType"));
            binding.setTargetObjectKey(text(payload, "targetObjectKey"));
            binding.setParameters(payload);
            binding.setBusinessViewSnapshot(item.path("businessView").isMissingNode() || item.path("businessView").isNull()
                    ? null : item.path("businessView"));
            if (binding.getBusinessViewSnapshot() != null) {
                binding.setComponentKey(text(binding.getBusinessViewSnapshot(), "componentKey"));
                JsonNode form = binding.getBusinessViewSnapshot().path("dynamicFormRevisionId");
                if (form.canConvertToLong()) binding.setDynamicFormRevisionId(form.asLong());
            }
            binding.setSourceRevisionId(id);
            return binding;
        }

        PermissionRequirement permission(Long id) {
            JsonNode item = id == null ? null : snapshots.get(id);
            if (item == null) return null;
            PermissionRequirement permission = new PermissionRequirement();
            permission.setPolicyRef(text(item.path("definition"), "definitionCode"));
            permission.setPolicySnapshot(item.path("definition").path("payload"));
            permission.setSourceRevisionId(id);
            return permission;
        }

        RuleSpec rule(Long id) {
            JsonNode item = id == null ? null : snapshots.get(id);
            if (item == null) return null;
            RuleSpec rule = new RuleSpec();
            rule.setExpression(item.path("definition").path("payload"));
            rule.setSourceRevisionId(id);
            return rule;
        }

        private static String text(JsonNode node, String field) {
            JsonNode value = node.path(field);
            return value.isTextual() ? value.asText() : null;
        }
    }
}
