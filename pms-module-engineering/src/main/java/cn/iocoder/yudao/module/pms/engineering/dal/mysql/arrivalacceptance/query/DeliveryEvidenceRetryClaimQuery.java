package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

import java.time.LocalDateTime;

public record DeliveryEvidenceRetryClaimQuery(Long tenantId, LocalDateTime dueAt) {
}
