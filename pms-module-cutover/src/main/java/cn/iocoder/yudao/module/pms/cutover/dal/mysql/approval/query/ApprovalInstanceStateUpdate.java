package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query;

import java.time.LocalDateTime;

public record ApprovalInstanceStateUpdate(Long tenantId, Long approvalInstanceId, Long expectedVersion,
        String statusCode, Integer currentNodeNo, LocalDateTime decisionAt, String rejectionReason,
        Long replacementApprovalInstanceId, String holdReasonCode, String updater, LocalDateTime updateTime) {
}
