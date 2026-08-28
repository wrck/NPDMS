package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;

import java.util.Map;

public record DynamicFormInstancePatchCommand(Long tenantId, Long actorUserId,
                                              DynamicFormProviderKey providerKey,
                                              DynamicFormBusinessAction action, DynamicFormOwnerKey ownerKey,
                                              Long instanceId, Integer expectedInstanceVersion,
                                              Map<String, Object> partialValues) {
    public DynamicFormInstancePatchCommand {
        partialValues = partialValues == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(partialValues));
    }
}
