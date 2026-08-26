package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

public record DynamicFormDraftUpdate(Long tenantId, Long preparationId, Long itemId,
                                     Long formInstanceId, Integer expectedVersion,
                                     String valueSnapshot, String updater) {}
