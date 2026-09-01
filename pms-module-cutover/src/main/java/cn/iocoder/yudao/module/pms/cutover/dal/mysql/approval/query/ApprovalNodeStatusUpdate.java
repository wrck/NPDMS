package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query;

import java.time.LocalDateTime;

public record ApprovalNodeStatusUpdate(Long tenantId, Long approvalNodeId, Integer expectedVersion,
                                       String expectedStatusCode, String newStatusCode,
                                       Long currentApproverUserId, Long projectScopeVersion,
                                       String assessmentReviewDecisionCode, String assessmentReviewReason,
                                       String feedback, LocalDateTime decisionAt, Long updater,
                                       LocalDateTime updateTime) {
}
