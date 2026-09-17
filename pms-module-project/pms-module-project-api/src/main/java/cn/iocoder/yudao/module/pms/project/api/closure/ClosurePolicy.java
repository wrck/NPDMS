package cn.iocoder.yudao.module.pms.project.api.closure;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 模板闭环策略契约（PROJ 模板域与 ACC 闭环域共享的冻结语义）。
 * 仅支持 NORMAL 闭环规则版本 1 与固定人工审批流程；七字段缺一不可。
 */
public final class ClosurePolicy {
    public static final String PROCESS_DEFINITION_KEY = "PMS_MINIMAL_NORMAL_CLOSURE";
    public static final String REVIEWER_PERMISSION = "pms:acc-project-closure:audit";
    private static final Set<String> FIELDS = Set.of(
            "closureType", "ruleRevision", "requireTerminalStage", "requireAllTasksDone",
            "revalidateBusinessFacts", "processDefinitionKey", "reviewerUserId");
    private final String closureType;
    private final Integer ruleRevision;
    private final Boolean requireTerminalStage;
    private final Boolean requireAllTasksDone;
    private final Boolean revalidateBusinessFacts;
    private final String processDefinitionKey;
    private final Long reviewerUserId;
    private final JsonNode source;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public ClosurePolicy(JsonNode json) {
        if (json == null || !json.isObject() || !new HashSet<>(json.propertyNames()).equals(FIELDS)) {
            throw new IllegalArgumentException("closurePolicy必须且只能包含批准的七个字段");
        }
        if (!json.get("closureType").isTextual() || !"NORMAL".equals(json.get("closureType").asText())
                || !json.get("ruleRevision").isIntegralNumber()
                || !json.get("ruleRevision").canConvertToInt() || json.get("ruleRevision").intValue() != 1
                || !json.get("processDefinitionKey").isTextual()
                || !PROCESS_DEFINITION_KEY.equals(json.get("processDefinitionKey").asText())) {
            throw new IllegalArgumentException("closurePolicy仅支持专用NORMAL闭环规则版本1及固定人工审批流程");
        }
        for (String field : List.of("requireTerminalStage", "requireAllTasksDone", "revalidateBusinessFacts")) {
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

    public String getClosureType() { return closureType; }
    public Integer getRuleRevision() { return ruleRevision; }
    public Boolean getRequireTerminalStage() { return requireTerminalStage; }
    public Boolean getRequireAllTasksDone() { return requireAllTasksDone; }
    public Boolean getRevalidateBusinessFacts() { return revalidateBusinessFacts; }
    public String getProcessDefinitionKey() { return processDefinitionKey; }
    public Long getReviewerUserId() { return reviewerUserId; }

    @JsonValue
    public JsonNode toJson() {
        return source.deepCopy();
    }
}
