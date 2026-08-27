package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

public record RequirementAnalysisHistoryQuery(
        Long tenantId, Long projectId, Integer cursorBusinessVersion, Long cursorId, int limit) {
}
