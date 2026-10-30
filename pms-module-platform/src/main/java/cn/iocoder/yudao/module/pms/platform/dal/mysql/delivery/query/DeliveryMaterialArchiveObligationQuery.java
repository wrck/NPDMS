package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;

/** Promote a source material's archive obligation without changing its evidence identity. */
public record DeliveryMaterialArchiveObligationQuery(Long tenantId, Long materialId) {}
