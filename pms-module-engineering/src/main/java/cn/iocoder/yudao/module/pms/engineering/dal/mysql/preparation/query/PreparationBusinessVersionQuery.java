package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

public record PreparationBusinessVersionQuery(Long tenantId, Long projectId,
                                              String preparationTypeCode, Integer businessVersion) {
}
