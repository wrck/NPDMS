package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

public record RequirementAnalysisContentUpdate(
        Long tenantId, Long preparationId, Integer expectedVersion,
        Integer expectedContentVersion, String updater) {
}
