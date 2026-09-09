package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

public record RequirementAnalysisEntityDataUpdate(Long tenantId, Long preparationId,
                                                  Integer expectedVersion, String entityValueJson,
                                                  String updater) {
}
