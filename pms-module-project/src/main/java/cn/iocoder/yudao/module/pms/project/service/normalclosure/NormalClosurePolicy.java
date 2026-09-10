package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import tools.jackson.databind.JsonNode;

/** Reuses the exact published template schema; a terminal graph alone never authorizes closure. */
public record NormalClosurePolicy(String closureType, int ruleRevision, boolean requireTerminalStage,
                                  boolean requireAllTasksDone, boolean revalidateBusinessFacts,
                                  String processDefinitionKey, Long reviewerUserId) {
    public static final String PROCESS_KEY = TemplateDefinitionContent.ClosurePolicy.PROCESS_DEFINITION_KEY;

    public static NormalClosurePolicy parseFrozen(String snapshot) {
        if (snapshot == null || snapshot.isBlank() || "null".equals(snapshot.trim()))
            throw NormalClosureErrors.failure("CLOSURE_POLICY_NOT_FROZEN");
        try {
            var policy = new TemplateDefinitionContent.ClosurePolicy(JsonUtils.parseObject(snapshot, JsonNode.class));
            return new NormalClosurePolicy(policy.getClosureType(), policy.getRuleRevision(), policy.getRequireTerminalStage(),
                    policy.getRequireAllTasksDone(), policy.getRevalidateBusinessFacts(), policy.getProcessDefinitionKey(), policy.getReviewerUserId());
        } catch (RuntimeException ex) {
            throw NormalClosureErrors.failure("CLOSURE_POLICY_UNSUPPORTED");
        }
    }
}
