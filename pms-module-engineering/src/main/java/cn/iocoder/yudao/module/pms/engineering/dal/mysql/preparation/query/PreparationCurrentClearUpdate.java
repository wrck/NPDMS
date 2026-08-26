package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

public record PreparationCurrentClearUpdate(Long tenantId, Long preparationId,
                                            Integer expectedVersion, String updater) {}
