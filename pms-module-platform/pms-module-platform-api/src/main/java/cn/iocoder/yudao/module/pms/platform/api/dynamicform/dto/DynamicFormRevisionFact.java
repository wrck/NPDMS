package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;

import java.util.List;

public record DynamicFormRevisionFact(Long tenantId, DynamicFormProviderKey providerKey, Long templateId,
                                      Long templateRevisionId, Integer revisionNo, Integer revisionFactVersion,
                                      String requiredUsage, DynamicFormBusinessAction action, String engineCode,
                                      String designerVersion, String rendererVersion, String formConfJson,
                                      String formRulesJson, List<DynamicFormFieldDescriptor> fields,
                                      DynamicFormPolicyFact policyFact) {
    public DynamicFormRevisionFact {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
