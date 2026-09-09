package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import org.springframework.stereotype.Component;
import java.util.Set;

/** Original site-survey entity owns all values; this provider supports schema use only. */
@Component
public class SiteSurveyFormPolicyProvider implements DynamicFormBusinessObjectPolicyProvider {
    public static final DynamicFormProviderKey KEY = new DynamicFormProviderKey("SOL", "SITE_SURVEY");
    public static final Set<String> FIELDS = Set.of("powerSupply", "cabinet", "networkPort", "fiber",
            "module", "cable", "ground", "constructionResource", "conclusion", "remark");

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
