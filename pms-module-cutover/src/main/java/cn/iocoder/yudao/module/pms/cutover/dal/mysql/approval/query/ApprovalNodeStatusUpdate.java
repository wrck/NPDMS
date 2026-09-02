package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query;

import java.time.LocalDateTime;

public record ApprovalNodeStatusUpdate(Long tenantId, Long approvalNodeId, Integer expectedVersion,
                                       String expectedStatusCode, String newStatusCode,
                                       Long currentApproverUserId, String candidateFactSnapshot,
                                       Long projectScopeVersion,
                                       String assessmentReviewDecisionCode, String assessmentReviewReason,
                                       String feedback, LocalDateTime decisionAt, String updater,
                                       LocalDateTime updateTime) {
}
