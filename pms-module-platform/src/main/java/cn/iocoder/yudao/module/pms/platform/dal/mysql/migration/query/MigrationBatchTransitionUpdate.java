package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query;

public record MigrationBatchTransitionUpdate(Long tenantId, Long batchId, int expectedVersion,
                                             String expectedStatus, String targetStatus,
                                             long sourceCount, String failureCode) {
}
