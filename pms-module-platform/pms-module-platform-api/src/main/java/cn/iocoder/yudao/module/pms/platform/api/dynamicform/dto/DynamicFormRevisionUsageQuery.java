package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;

public record DynamicFormRevisionUsageQuery(Long tenantId, Long actorUserId, DynamicFormProviderKey providerKey,
                                            Long templateRevisionId, String requiredUsage,
                                            DynamicFormBusinessAction action, Integer expectedRevisionFactVersion) {
}
