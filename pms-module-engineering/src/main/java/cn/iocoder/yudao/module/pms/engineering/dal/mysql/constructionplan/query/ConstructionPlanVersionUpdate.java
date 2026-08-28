package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query;

/** 施工计划根完整状态的版本CAS更新。 */
public record ConstructionPlanVersionUpdate(
        Long tenantId,
        Long planId,
        Integer expectedVersion,
        Long currentDurationRevisionId,
        Long pendingChangeId,
        String planRecalculationStatusCode,
        Long planRecalculationSourceRevisionId,
        String updater) {
}
