package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

public record DynamicFormCurrentRevisionQuery(Long tenantId, Long actorUserId,
        DynamicFormProviderKey providerKey, Long templateId, String requiredUsage) {
}
