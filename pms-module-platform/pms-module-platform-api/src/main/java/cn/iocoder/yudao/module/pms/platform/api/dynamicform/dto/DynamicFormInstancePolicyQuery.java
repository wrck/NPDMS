package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;

public record DynamicFormInstancePolicyQuery(Long tenantId, Long actorUserId, DynamicFormProviderKey providerKey,
                                             DynamicFormOwnerKey ownerKey, Long instanceId,
                                             DynamicFormBusinessAction action,
                                             tools.jackson.databind.JsonNode ownerExecutionContext) {
    public DynamicFormInstancePolicyQuery(Long tenantId, Long actorUserId, DynamicFormProviderKey providerKey,
                                          DynamicFormOwnerKey ownerKey, Long instanceId, DynamicFormBusinessAction action) {
        this(tenantId, actorUserId, providerKey, ownerKey, instanceId, action, null);
    }
    public DynamicFormInstancePolicyQuery {
        ownerExecutionContext = ownerExecutionContext == null ? null : ownerExecutionContext.deepCopy();
    }
    @Override public tools.jackson.databind.JsonNode ownerExecutionContext() {
        return ownerExecutionContext == null ? null : ownerExecutionContext.deepCopy();
    }
}
