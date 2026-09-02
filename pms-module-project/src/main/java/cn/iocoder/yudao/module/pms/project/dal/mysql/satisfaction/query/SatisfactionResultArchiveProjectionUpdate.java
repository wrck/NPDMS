package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query;

public record SatisfactionResultArchiveProjectionUpdate(
        Long tenantId, Long resultId, Integer expectedFactVersion, Long deliverableSourceVersionId,
        String archiveStatus, String archiveFailureCode, Integer archiveRetryCount, String updater) {
}
