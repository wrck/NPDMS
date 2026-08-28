package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

public record PreparationPageQuery(Long tenantId, Long projectId, String preparationTypeCode,
                                   Integer cursorBusinessVersion, Long cursorId, Integer limit) {}
