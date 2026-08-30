package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

public record DeliveryEvidenceRetryStateUpdate(
        Long tenantId,
        Long evidenceId,
        Integer currentRevision,
        Integer expectedVersion,
        String expectedStatus,
        String targetStatus) {
}
