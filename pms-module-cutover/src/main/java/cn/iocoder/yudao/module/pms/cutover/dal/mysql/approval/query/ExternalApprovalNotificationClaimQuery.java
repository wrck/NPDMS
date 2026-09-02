package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query;

import java.time.LocalDateTime;

public record ExternalApprovalNotificationClaimQuery(Long tenantId, LocalDateTime dueAt, Integer batchSize) {
}
