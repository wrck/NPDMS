package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

public record RequirementAnalysisEffectiveClearUpdate(
        Long tenantId, Long preparationId, Integer expectedVersion, String updater) {
}
