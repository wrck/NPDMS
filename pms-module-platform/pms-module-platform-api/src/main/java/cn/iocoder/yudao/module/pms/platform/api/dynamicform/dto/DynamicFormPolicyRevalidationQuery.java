package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

public record DynamicFormPolicyRevalidationQuery(Long tenantId, Long actorUserId,
                                                 DynamicFormProviderKey providerKey,
                                                 DynamicFormOwnerKey ownerKey, Long instanceId,
                                                 DynamicFormPolicyFact expectedFact) {
}
