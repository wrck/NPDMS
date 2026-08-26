package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

public record PreparationSnapshotPageQuery(Long tenantId, Long preparationId,
                                           Integer cursorSnapshotNo, Long cursorId, Integer limit) {}
