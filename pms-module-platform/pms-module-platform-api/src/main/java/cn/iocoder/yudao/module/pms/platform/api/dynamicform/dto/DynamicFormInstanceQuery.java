package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;

public record DynamicFormInstanceQuery(Long tenantId, Long actorUserId, DynamicFormProviderKey providerKey,
                                       DynamicFormOwnerKey ownerKey, Long instanceId,
                                       DynamicFormBusinessAction action) {
}
