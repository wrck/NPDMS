package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

public record PreparationInputInvalidationUpdate(Long tenantId, Long preparationId,
                                                 Integer expectedVersion, Integer expectedInputVersion,
                                                 Integer expectedReadinessVersion, String updater) {
}
