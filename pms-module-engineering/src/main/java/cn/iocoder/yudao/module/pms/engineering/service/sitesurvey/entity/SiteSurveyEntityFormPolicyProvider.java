package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import org.springframework.stereotype.Component;
import java.util.Set;

/** The independent survey Owner retains the original published-schema compatibility rules. */
@Component
public class SiteSurveyEntityFormPolicyProvider implements DynamicFormBusinessObjectPolicyProvider {
    public static final DynamicFormProviderKey KEY = new DynamicFormProviderKey("SOL", "SITE_SURVEY");
    private static final Set<String> FIELDS = SiteSurveyEntityProvider.FIELDS.fields().stream()
            .filter(field -> field.type() == cn.iocoder.yudao.module.pms.platform.api.entity.EntityField.Type.TEXT)
            .map(cn.iocoder.yudao.module.pms.platform.api.entity.EntityField::code)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    @Override public DynamicFormProviderKey providerKey() { return KEY; }

    @Override public DynamicFormPolicyFact inspectRevisionCompatibility(DynamicFormRevisionPolicyQuery query) {
        Set<String> present = new java.util.HashSet<>();
        boolean compatible = "SITE_SURVEY".equals(query.requiredUsage()) && query.fields().stream().allMatch(field -> {
            present.add(field.fieldKey());
            return !field.controlledFile() && (FIELDS.contains(field.fieldKey())
                    || field.fieldKey().startsWith("extra_"));
        }) && present.containsAll(FIELDS);
        return new DynamicFormPolicyFact(query.action(), compatible,
                compatible ? null : "SITE_SURVEY_SCHEMA_INCOMPATIBLE", query.revisionFactVersion().longValue(),
                "SITE_SURVEY_ENTITY_FIELDS");
    }

    @Override public DynamicFormPolicyFact inspectInstanceOwnerPolicy(DynamicFormInstancePolicyQuery query) {
        return new DynamicFormPolicyFact(query.action(), false, "SITE_SURVEY_HAS_NO_PLT_INSTANCE", null, "ENTITY_ONLY");
    }
    @Override public DynamicFormPolicyFact lockAndRevalidateInstanceOwnerPolicy(DynamicFormPolicyRevalidationQuery query) {
        return new DynamicFormPolicyFact(query.expectedFact().action(), false,
                "SITE_SURVEY_HAS_NO_PLT_INSTANCE", null, "ENTITY_ONLY");
    }
}
