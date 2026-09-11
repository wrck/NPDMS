package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import lombok.Data;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * PM-03 V2 published runtime truth.
 *
 * <p>This object is self-contained: project creation and runtime graph initialization must not
 * re-resolve DeliveryDefinition revisions. source* ids are audit evidence only.</p>
 */
@Data
public class TemplateExecutionSnapshot {

    public static final int SCHEMA_VERSION = 2;

    private Integer executionSchemaVersion = SCHEMA_VERSION;
    private String compilerVersion;
    private TemplateDesignerDocument.Match match = new TemplateDesignerDocument.Match();
    private String processDefinitionKey;
    private JsonNode closurePolicy;
    private List<StageContract> stages = new ArrayList<>();
    private List<TaskContract> tasks = new ArrayList<>();
    private List<MilestoneContract> milestones = new ArrayList<>();
    private List<DeliverableContract> deliverables = new ArrayList<>();
    private List<GateContract> gates = new ArrayList<>();
    private List<TransitionContract> transitions = new ArrayList<>();

    @Data
    public static class StageContract {
        private String nodeKey;
        private String code;
        private String name;
        private Integer sortOrder;
        private String entryCriteria;
        private String exitCriteria;
        private Boolean start;
        private Boolean terminal;
        private BindingContract binding;
        private PermissionContract permission;
        private JsonNode completionRule;
        private Long sourceDefinitionRevisionId;
        private Long sourceWorkBindingRevisionId;
        private Long sourcePermissionPolicyRevisionId;
        private Long sourceCompletionRuleRevisionId;
    }

    @Data
    public static class TaskContract {
        private String nodeKey;
        private String code;
        private String name;
        private String parentTaskCode;
        private String stageCode;
        private Integer priority;
        private Integer sortOrder;
        private BigDecimal estimatedHours;
        private String satisfactionTiming;
        private String description;
        private BindingContract binding;
        private PermissionContract permission;
        private JsonNode completionRule;
        private String gateRef;
        private Long sourceDefinitionRevisionId;
        private Long sourceWorkBindingRevisionId;
        private Long sourcePermissionPolicyRevisionId;
        private Long sourceCompletionRuleRevisionId;
    }

    @Data
    public static class BindingContract {
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
    public static class PermissionContract {
        private String policyRef;
        private JsonNode policySnapshot;
        private Long sourceRevisionId;
    }

    @Data
    public static class TransitionContract {
        private String edgeKey;
        private String code;
        private String fromStageCode;
        private String toStageCode;
        private JsonNode conditionRule;
        private Integer priority;
        private Boolean defaultBranch;
        private Long sourceTransitionId;
        private Long sourceTransitionRevisionNo;
        private Long sourceConditionRuleRevisionId;
    }

    @Data
    public static class MilestoneContract {
        private String nodeKey;
        private String code;
        private String name;
        private String stageCode;
        private String timing;
        private String criteria;
        private JsonNode configuration;
        private Long sourceDefinitionRevisionId;
    }

    @Data
    public static class DeliverableContract {
        private String nodeKey;
        private String code;
        private String name;
        private String stageCode;
        private String taskCode;
        private Boolean required;
        private JsonNode configuration;
        private Long sourceDefinitionRevisionId;
    }

    @Data
    public static class GateContract {
        private String nodeKey;
        private String code;
        private String name;
        private String gateType;
        private String stageCode;
        private String description;
        private List<GateReference> references = new ArrayList<>();
        private Long sourceDefinitionRevisionId;
    }

    @Data
    public static class GateReference {
        private String refType;
        private String refCode;
        private String refVersion;
    }

    /**
     * Compatibility projection for unchanged Project/Task initialization code. This conversion uses
     * only the immutable snapshot and never reads DefinitionRevision tables.
     */
    public TemplateDefinitionContent toRuntimeContent() {
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        if (match != null) {
            content.setSigningMethod(match.getSigningMethod());
            content.setProjectCategory(match.getProjectCategory());
            content.setImplementationMethod(match.getImplementationMethod());
            content.setMajorProjectLevel(match.getMajorProjectLevel());
        }
        content.setProcessDefinitionKey(processDefinitionKey);
        content.setProcessDefinitionVersion(null);
        content.setClosurePolicy(closurePolicy == null ? null : new TemplateDefinitionContent.ClosurePolicy(closurePolicy));
        content.setDefinitionSnapshot(null);
        content.setExecutionSnapshot(JsonUtils.parseObject(JsonUtils.toJsonString(this), JsonNode.class));

        for (StageContract source : stages) {
            TemplateDefinitionContent.StageDef target = new TemplateDefinitionContent.StageDef();
            target.setDefinitionRevisionId(source.getSourceDefinitionRevisionId());
            target.setWorkBindingRevisionId(source.getSourceWorkBindingRevisionId());
            target.setPermissionPolicyRevisionId(source.getSourcePermissionPolicyRevisionId());
            target.setCompletionRuleRevisionId(source.getSourceCompletionRuleRevisionId());
            target.setStageCode(source.getCode());
            target.setName(source.getName());
            target.setSortOrder(source.getSortOrder());
            target.setEntryCriteria(source.getEntryCriteria());
            target.setExitCriteria(source.getExitCriteria());
            target.setStart(source.getStart());
            target.setTerminal(source.getTerminal());
            target.setSourceNodeKey(source.getNodeKey());
            target.setBindingSnapshot(source.getBinding() == null ? null
                    : JsonUtils.parseObject(JsonUtils.toJsonString(source.getBinding()), JsonNode.class));
            target.setPermissionSnapshot(source.getPermission() == null ? null
                    : JsonUtils.parseObject(JsonUtils.toJsonString(source.getPermission()), JsonNode.class));
            target.setCompletionRuleSnapshot(copy(source.getCompletionRule()));
            content.getStages().add(target);
        }
        for (TaskContract source : tasks) {
            TemplateDefinitionContent.TaskDef target = new TemplateDefinitionContent.TaskDef();
            target.setDefinitionRevisionId(source.getSourceDefinitionRevisionId());
            target.setWorkBindingRevisionId(source.getSourceWorkBindingRevisionId());
            target.setPermissionPolicyRevisionId(source.getSourcePermissionPolicyRevisionId());
            target.setCompletionRuleRevisionId(source.getSourceCompletionRuleRevisionId());
            target.setTaskCode(source.getCode());
            target.setName(source.getName());
            target.setParentTaskCode(source.getParentTaskCode());
            target.setStageCode(source.getStageCode());
            target.setPriority(source.getPriority());
            target.setSortOrder(source.getSortOrder());
            target.setEstimatedHours(source.getEstimatedHours());
            target.setSatisfactionTiming(source.getSatisfactionTiming());
            target.setDescription(source.getDescription());
            applyBinding(target, source.getBinding());
            if (source.getPermission() != null) {
                target.setPermissionPolicyRef(source.getPermission().getPolicyRef());
                target.setPermissionSnapshot(JsonUtils.parseObject(JsonUtils.toJsonString(source.getPermission()), JsonNode.class));
            }
            applyRule(target, source.getCompletionRule());
            target.setGateRef(source.getGateRef());
            target.setDefinitionVersion(1);
            target.setSourceNodeKey(source.getNodeKey());
            target.setBindingViewSnapshot(source.getBinding() == null ? null : copy(source.getBinding().getBusinessViewSnapshot()));
            content.getTasks().add(target);
        }
        for (MilestoneContract source : milestones) {
            TemplateDefinitionContent.MilestoneDef target = new TemplateDefinitionContent.MilestoneDef();
            target.setDefinitionRevisionId(source.getSourceDefinitionRevisionId());
            target.setMilestoneCode(source.getCode());
            target.setName(source.getName());
            target.setStageCode(source.getStageCode());
            target.setTiming(source.getTiming());
            target.setCriteria(source.getCriteria());
            content.getMilestones().add(target);
        }
        for (DeliverableContract source : deliverables) {
            TemplateDefinitionContent.DeliverableDef target = new TemplateDefinitionContent.DeliverableDef();
            target.setDefinitionRevisionId(source.getSourceDefinitionRevisionId());
            target.setDeliverableCode(source.getCode());
            target.setName(source.getName());
            target.setStageCode(source.getStageCode());
            target.setTaskCode(source.getTaskCode());
            target.setRequired(source.getRequired());
            content.getDeliverables().add(target);
        }
        for (GateContract source : gates) {
            TemplateDefinitionContent.GateDef target = new TemplateDefinitionContent.GateDef();
            target.setDefinitionRevisionId(source.getSourceDefinitionRevisionId());
            target.setGateCode(source.getCode());
            target.setName(source.getName());
            target.setGateType(source.getGateType());
            target.setStageCode(source.getStageCode());
            target.setDescription(source.getDescription());
            for (GateReference reference : source.getReferences()) {
                TemplateDefinitionContent.GateRef ref = new TemplateDefinitionContent.GateRef();
                ref.setRefType(reference.getRefType());
                ref.setRefCode(reference.getRefCode());
                ref.setRefVersion(reference.getRefVersion());
                target.getReferences().add(ref);
            }
            content.getGates().add(target);
        }
        for (TransitionContract source : transitions) {
            TemplateDefinitionContent.TransitionDef target = new TemplateDefinitionContent.TransitionDef();
            target.setId(source.getSourceTransitionId());
            target.setTransitionCode(source.getCode());
            target.setFromStageCode(source.getFromStageCode());
            target.setToStageCode(source.getToStageCode());
            target.setConditionRuleRevisionId(source.getSourceConditionRuleRevisionId());
            target.setPriority(source.getPriority());
            target.setDefaultBranch(source.getDefaultBranch());
            target.setRevisionNo(source.getSourceTransitionRevisionNo());
            target.setSourceTransitionKey(source.getEdgeKey());
            target.setConditionRuleSnapshot(copy(source.getConditionRule()));
            content.getTransitions().add(target);
        }
        return content;
    }

    private static void applyBinding(TemplateDefinitionContent.TaskDef target, BindingContract binding) {
        if (binding == null) return;
        target.setWorkBindingTypeCode(binding.getType());
        target.setTargetContextCode(binding.getTargetContextCode());
        target.setTargetObjectType(binding.getTargetObjectType());
        target.setTargetObjectKey(binding.getTargetObjectKey());
        target.setComponentKey(binding.getComponentKey());
        target.setDynamicFormRevisionId(binding.getDynamicFormRevisionId());
        target.setApprovalDefinitionKey(binding.getApprovalDefinitionKey());
        target.setBindingConfig(binding.getParameters() == null ? "{}" : JsonUtils.toJsonString(binding.getParameters()));
    }

    private static void applyRule(TemplateDefinitionContent.TaskDef target, JsonNode rule) {
        if (rule == null || rule.isNull()) return;
        if (rule.has("operator")) {
            target.setCompletionRuleTypeCode(rule.path("operator").asText());
            target.setCompletionRuleConfig(JsonUtils.toJsonString(rule));
            return;
        }
        target.setCompletionRuleTypeCode(rule.path("predicate").asText());
        target.setCompletionRuleConfig(JsonUtils.toJsonString(rule.path("parameters")));
    }

    private static JsonNode copy(JsonNode value) {
        return value == null || value.isNull() ? null : value.deepCopy();
    }
}
