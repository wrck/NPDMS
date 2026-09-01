package cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.migration.query;

public record LegacyCutoverPlanTargetQuery(Long tenantId, Long targetTaskId,
                                           Long legacyPlanId) {

    public static LegacyCutoverPlanTargetQuery target(Long tenantId, Long targetTaskId) {
        return new LegacyCutoverPlanTargetQuery(tenantId, targetTaskId, null);
    }

    public static LegacyCutoverPlanTargetQuery conflict(Long tenantId, Long targetTaskId, Long legacyPlanId) {
        return new LegacyCutoverPlanTargetQuery(tenantId, targetTaskId, legacyPlanId);
    }
}
