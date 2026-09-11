package cn.iocoder.yudao.module.pms.project.domain.template;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy-compatible template content projection.
 *
 * <p>V2 authoring truth is {@link TemplateDesignerDocument}; published runtime truth is
 * {@link TemplateExecutionSnapshot}. This class remains as a compatibility carrier for existing
 * Project initialization, controllers and historical readers. Runtime-only V2 fields are
 * server-generated and read-only to legacy clients.</p>
 */
@Data
public class TemplateDefinitionContent {

    public static final String GATE_TYPE_ENTRY = "ENTRY";
    public static final String GATE_TYPE_EXIT = "EXIT";

    public static final String REF_TYPE_TASK = "TASK";
    public static final String REF_TYPE_MILESTONE = "MILESTONE";
    public static final String REF_TYPE_DELIVERABLE = "DELIVERABLE";
    public static final String REF_TYPE_STATE = "STATE";
    public static final String REF_TYPE_PROCESS = "PROCESS";
    public static final String REF_TYPE_APPROVAL = "APPROVAL";

    private String signingMethod;
    private String projectCategory;
    private String implementationMethod;
    private String majorProjectLevel;

    private String processDefinitionKey;
    /** Historical compatibility field; V2 keeps it null. */
    private String processDefinitionVersion;

    private ClosurePolicy closurePolicy;

    @Getter
    public static final class ClosurePolicy {
        public static final String PROCESS_DEFINITION_KEY = "PMS_MINIMAL_NORMAL_CLOSURE";
        public static final String REVIEWER_PERMISSION = "pms:acc-project-closure:audit";
        private static final java.util.Set<String> FIELDS = java.util.Set.of(
                "closureType", "ruleRevision", "requireTerminalStage", "requireAllTasksDone",
                "revalidateBusinessFacts", "processDefinitionKey", "reviewerUserId");
        private final String closureType;
        private final Integer ruleRevision;
        private final Boolean requireTerminalStage;
        private final Boolean requireAllTasksDone;
        private final Boolean revalidateBusinessFacts;
        private final String processDefinitionKey;
        private final Long reviewerUserId;
        @Getter(lombok.AccessLevel.NONE)
        private final JsonNode source;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public ClosurePolicy(JsonNode json) {
            if (json == null || !json.isObject() || !new java.util.HashSet<>(json.propertyNames()).equals(FIELDS)) {
                throw new IllegalArgumentException("closurePolicy必须且只能包含批准的七个字段");
            }
            if (!json.get("closureType").isTextual() || !"NORMAL".equals(json.get("closureType").asText())
                    || !json.get("ruleRevision").isIntegralNumber()
                    || !json.get("ruleRevision").canConvertToInt() || json.get("ruleRevision").intValue() != 1
                    || !json.get("processDefinitionKey").isTextual()
                    || !PROCESS_DEFINITION_KEY.equals(json.get("processDefinitionKey").asText())) {
                throw new IllegalArgumentException("closurePolicy仅支持专用NORMAL闭环规则版本1及固定人工审批流程");
            }
            for (String field : java.util.List.of("requireTerminalStage", "requireAllTasksDone", "revalidateBusinessFacts")) {
                if (!json.get(field).isBoolean() || !json.get(field).booleanValue()) {
                    throw new IllegalArgumentException("closurePolicy." + field + "必须为布尔true，不能手工豁免条件");
                }
            }
            JsonNode reviewer = json.get("reviewerUserId");
            if (!(reviewer.isTextual() || reviewer.isIntegralNumber())) {
                throw new IllegalArgumentException("closurePolicy.reviewerUserId必须为正Long或十进制整数字符串");
            }
            String reviewerText = reviewer.asText();
            if (!reviewerText.matches("[0-9]+")) {
                throw new IllegalArgumentException("closurePolicy.reviewerUserId必须为正Long或十进制整数字符串");
            }
            try {
                reviewerUserId = Long.valueOf(reviewerText);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("closurePolicy.reviewerUserId超出Long范围", ex);
            }
            if (reviewerUserId <= 0) {
                throw new IllegalArgumentException("closurePolicy.reviewerUserId必须为正Long");
            }
            closureType = "NORMAL";
            ruleRevision = 1;
            requireTerminalStage = true;
            requireAllTasksDone = true;
            revalidateBusinessFacts = true;
            processDefinitionKey = PROCESS_DEFINITION_KEY;
            source = json.deepCopy();
        }

        @JsonValue
        public JsonNode toJson() {
            return source.deepCopy();
        }
    }

    private List<StageDef> stages = new ArrayList<>();
    private List<TaskDef> tasks = new ArrayList<>();
    private List<MilestoneDef> milestones = new ArrayList<>();
    private List<DeliverableDef> deliverables = new ArrayList<>();
    private List<GateDef> gates = new ArrayList<>();
    private List<TransitionDef> transitions = new ArrayList<>();

    /** Legacy server-generated exact definition closure. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private JsonNode definitionSnapshot;

    /** V2 server-generated immutable execution snapshot carried into existing initialization code. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private JsonNode executionSnapshot;

    @Data
    public static class TransitionDef {
        private Long id;
        private String transitionCode;
        private String fromStageCode;
        private String toStageCode;
        private Long conditionRuleRevisionId;
        private Integer priority;
        @JsonProperty("default")
        private Boolean defaultBranch;
        private Long revisionNo;
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private String sourceTransitionKey;
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private JsonNode conditionRuleSnapshot;
    }

    @Data
    public static class StageDef {
        @JsonAlias("stageDefinitionRevisionId")
        private Long definitionRevisionId;
        private Boolean start;
        private Boolean terminal;
        private Long workBindingRevisionId;
        private Long permissionPolicyRevisionId;
        private Long completionRuleRevisionId;
        private String stageCode;
        private String name;
        private Integer sortOrder;
        private String entryCriteria;
        private String exitCriteria;
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private String sourceNodeKey;
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private JsonNode bindingSnapshot;
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private JsonNode permissionSnapshot;
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private JsonNode completionRuleSnapshot;
    }

    @Data
    public static class TaskDef {
        private Long definitionRevisionId;
        private Long workBindingRevisionId;
        private Long permissionPolicyRevisionId;
        private Long completionRuleRevisionId;
        private Long id;
        private String taskCode;
        private String name;
        private String parentTaskCode;
        private String stageCode;
        private Integer priority;
        private Integer sortOrder;
        private BigDecimal estimatedHours;
        private String satisfactionTiming;
        private String description;
        private String workBindingTypeCode;
        private String targetContextCode;
        private String targetObjectType;
        private String targetObjectKey;
        private String componentKey;
        private Long dynamicFormRevisionId;
        private String approvalDefinitionKey;
        private String bindingConfig;
        private String permissionPolicyRef;
        private String completionRuleTypeCode;
        private String completionRuleConfig;
        private String gateRef;
        private Integer definitionVersion;
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private String sourceNodeKey;
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private JsonNode bindingViewSnapshot;
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private JsonNode permissionSnapshot;
    }

    @Data
    public static class MilestoneDef {
        private Long definitionRevisionId;
        private String milestoneCode;
        private String name;
        private String stageCode;
        private String timing;
        private String criteria;
    }

    @Data
    public static class DeliverableDef {
        private Long definitionRevisionId;
        private Long id;
        private String deliverableCode;
        private String name;
        private String stageCode;
        private String taskCode;
        private Boolean required;
    }

    @Data
    public static class GateDef {
        private Long definitionRevisionId;
        private String gateCode;
        private String name;
        private String gateType;
        private String stageCode;
        private String description;
        private List<GateRef> references = new ArrayList<>();
    }

    @Data
    public static class GateRef {
        private String refType;
        private String refCode;
        private String refVersion;
    }
}
