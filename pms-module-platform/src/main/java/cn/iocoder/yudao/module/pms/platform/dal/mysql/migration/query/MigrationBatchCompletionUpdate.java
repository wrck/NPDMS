package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query;

public record MigrationBatchCompletionUpdate(Long tenantId, Long batchId, int expectedVersion,
                                             long sourceCount, long mappedCount, long issueCount,
                                             long retainedCount, String ruleVersion) {
}
