package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;

import java.util.Map;

public record DynamicFormInstanceCreateCommand(Long tenantId, Long actorUserId,
                                               DynamicFormProviderKey providerKey,
                                               DynamicFormBusinessAction action, Long preallocatedInstanceId,
                                               DynamicFormOwnerKey ownerKey, Long templateRevisionId,
                                               Integer expectedRevisionFactVersion,
                                               Map<String, Object> initialValues) {
    public DynamicFormInstanceCreateCommand {
        initialValues = initialValues == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(initialValues));
    }
}
