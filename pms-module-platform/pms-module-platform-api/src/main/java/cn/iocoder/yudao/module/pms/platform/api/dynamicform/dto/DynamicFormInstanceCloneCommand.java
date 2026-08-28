package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

public record DynamicFormInstanceCloneCommand(Long tenantId, Long actorUserId,
                                              DynamicFormProviderKey providerKey,
                                              DynamicFormInstanceFact sourceFact,
                                              DynamicFormOwnerKey targetOwnerKey,
                                              Long preallocatedTargetInstanceId,
                                              DynamicFormPolicyFact targetPolicyFact,
                                              String operationId) {
}
