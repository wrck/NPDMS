package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

public record RequirementAnalysisDynamicContentUpdate(
        Long tenantId, Long preparationId, Integer expectedVersion, String updater) {
}
