package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.JsonNode;

/**
 * PM-03 typed TASK_NATIVE completion policy.
 *
 * <p>The template/compiler may select this stable policy, but runtime does not interpret arbitrary
 * JSON as business logic. The frozen snapshot is only the strict parameter payload for the named
 * policy. Historical TASK_NATIVE contracts already use the same payload shape.</p>
 */
public final class TaskNativeCompletionPolicy {

    public static final String WORK_BINDING_TYPE = "TASK_NATIVE";
    public static final String RULE_TYPE = "TASK_NATIVE_STATUS";
    public static final String REQUIRED_STATUS = "DONE";

    private TaskNativeCompletionPolicy() {
    }

    public static void validate(String workBindingTypeCode, String completionRuleTypeCode,
                                String completionRuleSnapshot) {
        if (!WORK_BINDING_TYPE.equals(workBindingTypeCode)) {
            throw new IllegalArgumentException("TASK_NATIVE completion policy requires TASK_NATIVE binding");
        }
        validateRule(completionRuleTypeCode, completionRuleSnapshot);
    }

    public static void validateRule(String completionRuleTypeCode, String completionRuleSnapshot) {
        if (!RULE_TYPE.equals(completionRuleTypeCode)) {
            throw new IllegalArgumentException("TASK_NATIVE completion rule type must be TASK_NATIVE_STATUS");
        }
        JsonNode parameters;
        try {
            parameters = JsonUtils.parseObject(completionRuleSnapshot, JsonNode.class);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("TASK_NATIVE completion snapshot is invalid", ex);
        }
        if (parameters == null || !parameters.isObject() || parameters.size() != 1
                || !parameters.path("requiredStatus").isTextual()
                || !REQUIRED_STATUS.equals(parameters.path("requiredStatus").asText())) {
            throw new IllegalArgumentException("TASK_NATIVE completion snapshot must require DONE");
        }
    }
}
