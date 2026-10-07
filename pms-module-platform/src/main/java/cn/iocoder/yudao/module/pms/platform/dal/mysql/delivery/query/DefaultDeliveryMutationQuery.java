package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;

public record DefaultDeliveryMutationQuery(Long tenantId, Long materialId, Long expectedVersion,
                                          String title, boolean delete, String updater) { }
