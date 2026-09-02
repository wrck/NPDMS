package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.migration.query;

public record LegacyCutoverReconciliationQuery(Long tenantId, Long legacyTaskId,
                                                Long projectId, String taskNo) {

    public static LegacyCutoverReconciliationQuery source(Long tenantId, Long legacyTaskId) {
        return new LegacyCutoverReconciliationQuery(tenantId, legacyTaskId, null, null);
    }

    public static LegacyCutoverReconciliationQuery target(Long tenantId, Long legacyTaskId,
                                                           Long projectId, String taskNo) {
        return new LegacyCutoverReconciliationQuery(tenantId, legacyTaskId, projectId, taskNo);
    }
}
