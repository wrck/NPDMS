package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query;

public record MigrationBatchIdentityQuery(Long tenantId, String ownerContextCode, String purposeCode,
                                          String releaseId, String sourceSystem, String sourceTable) {
}
