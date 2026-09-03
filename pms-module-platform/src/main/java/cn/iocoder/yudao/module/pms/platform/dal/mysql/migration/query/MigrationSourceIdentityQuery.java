package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query;

public record MigrationSourceIdentityQuery(Long tenantId, Long batchId, String sourceSystem,
                                           String sourceTable, String sourceRecordKey) {
}
