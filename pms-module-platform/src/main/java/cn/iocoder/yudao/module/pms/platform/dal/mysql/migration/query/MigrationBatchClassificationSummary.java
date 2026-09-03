package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query;

public record MigrationBatchClassificationSummary(long sourceCount, long mappedCount,
                                                  long issueCount, long retainedCount,
                                                  long unclassifiedCount, long conflictingCount) {
}
