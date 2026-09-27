package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

public record DeliveryEvidenceArchivedUpdate(
        Long tenantId,
        Long evidenceId,
        Integer currentRevision,
        Long expectedVersion,
        String archiveRecordId,
        String eventId) {
}
