package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

import java.time.LocalDateTime;

public record DynamicFormFreezeUpdate(Long tenantId, Long preparationId, Long itemId,
                                      Long formInstanceId, Integer expectedVersion,
                                      LocalDateTime frozenAt, Long frozenBy, String updater) {}
