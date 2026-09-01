package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query;

import java.time.LocalDateTime;

public record ApprovalNotificationClaimQuery(Long tenantId, LocalDateTime dueAt, Integer batchSize) {
}
