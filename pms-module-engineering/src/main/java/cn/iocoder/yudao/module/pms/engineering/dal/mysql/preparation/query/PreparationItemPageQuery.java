package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

public record PreparationItemPageQuery(Long tenantId, Long preparationId,
                                       Integer cursorSortOrder, String cursorItemCode,
                                       Long cursorId, Integer limit) {}
