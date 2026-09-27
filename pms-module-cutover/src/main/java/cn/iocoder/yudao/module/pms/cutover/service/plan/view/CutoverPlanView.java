package cn.iocoder.yudao.module.pms.cutover.service.plan.view;

import tools.jackson.databind.JsonNode;

import java.util.List;

/** 与F-CUT-004 API PlanView精确同构的只读投影。 */
public record CutoverPlanView(Long taskId, String taskStage, Long taskVersion,
                              Long planRevisionId, Integer revisionNo, Long planVersion,
                              String originCode, String status, Long legacyPlanId, Integer legacyStatusRaw,
                              Long sourcePlanRevisionId, String revisionReason, JsonNode sourceSnapshot,
                              JsonNode content, ApprovalFactView approvalFact, List<String> allowedActions) {

    public record ApprovalFactView(Long approvalInstanceId, Long approvalVersion, String status,
                                   Long decisionAt, String rejectionReason) {}
}
