package cn.iocoder.yudao.module.pms.cutover.service.approval.command;

import java.util.List;

public record RejectCutoverApprovalCommand(Long tenantId, Long taskId, Integer expectedTaskVersion,
                                           Integer expectedApprovalVersion,
                                           List<ReviewItemInput> reviewItems,
                                           AssessmentReviewInput assessmentReview, String feedback,
                                           String idempotencyKey, String correlationId) {
}
