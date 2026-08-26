package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

public record FileArchiveRecordQuery(
        Long tenantId, String archiveBatchId, Long artifactId, Integer versionNo) {
}
