package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;

import java.util.List;
import java.util.Map;

public record DynamicFormInstanceFact(Long tenantId, DynamicFormProviderKey providerKey,
                                      DynamicFormOwnerKey ownerKey, Long instanceId, Long templateId,
                                      Long templateRevisionId, Integer templateRevisionNo,
                                      Integer revisionFactVersion, String engineCode, String designerVersion,
                                      String rendererVersion, String formConfJson, String formRulesJson,
                                      List<DynamicFormFieldDescriptor> fields, Map<String, Object> ordinaryValues,
                                      DynamicFormValidationFact validationFact,
                                      List<FileReferenceSetFact> controlledFileFacts, Integer instanceVersion,
                                      DynamicFormBusinessAction action, DynamicFormPolicyFact policyFact) {
    public DynamicFormInstanceFact {
        fields = fields == null ? List.of() : List.copyOf(fields);
        ordinaryValues = ordinaryValues == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(ordinaryValues));
        controlledFileFacts = controlledFileFacts == null ? List.of() : List.copyOf(controlledFileFacts);
    }
}
