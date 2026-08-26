package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

public record PreparationWaiverPageQuery(Long tenantId, Long projectId, String itemCode,
                                         Integer cursorWaiverNo, Long cursorId, Integer limit) {}
