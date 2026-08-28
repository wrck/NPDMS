package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;

import java.util.List;

public record DynamicFormRevisionPolicyQuery(Long tenantId, Long actorUserId, DynamicFormProviderKey providerKey,
                                             Long templateId, Long templateRevisionId, Integer revisionNo,
                                             Integer revisionFactVersion, String requiredUsage,
                                             DynamicFormBusinessAction action, List<DynamicFormFieldDescriptor> fields) {
    public DynamicFormRevisionPolicyQuery {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
