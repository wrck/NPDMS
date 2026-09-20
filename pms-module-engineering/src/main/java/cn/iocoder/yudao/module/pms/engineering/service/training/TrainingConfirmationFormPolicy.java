package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class TrainingConfirmationFormPolicy implements DynamicFormBusinessObjectPolicyProvider {
    public static final DynamicFormProviderKey KEY = new DynamicFormProviderKey("IMP", "TRAINING_CONFIRMATION");
    public static final String USAGE = "TRAINING_CUSTOMER_CONFIRMATION";
    private static final Set<String> REQUIRED = Set.of("skillRating", "effectRating", "satisfactionRating", "signConfirmerName", "signatureImageDataUrl");
    @Override public DynamicFormProviderKey providerKey() { return KEY; }
    @Override public DynamicFormPolicyFact inspectRevisionCompatibility(DynamicFormRevisionPolicyQuery query) {
        boolean allowed = query != null && USAGE.equals(query.requiredUsage()) && REQUIRED.stream().allMatch(
                key -> query.fields().stream().anyMatch(field -> key.equals(field.fieldKey()) && field.required()));
        return new DynamicFormPolicyFact(query == null ? null : query.action(), allowed,
                allowed ? null : "TRAINING_CONFIRMATION_FIELDS_REQUIRED", query == null ? null : query.revisionFactVersion().longValue(), "TRAINING_CONFIRMATION");
    }
    @Override public DynamicFormPolicyFact inspectInstanceOwnerPolicy(DynamicFormInstancePolicyQuery query) {
        return new DynamicFormPolicyFact(query.action(), false, "TRAINING_VALUES_OWNED_BY_IMP", null, "NOT_SUPPORTED");
    }
    @Override public DynamicFormPolicyFact lockAndRevalidateInstanceOwnerPolicy(DynamicFormPolicyRevalidationQuery query) {
        return new DynamicFormPolicyFact(query.expectedFact().action(), false, "TRAINING_VALUES_OWNED_BY_IMP", null, "NOT_SUPPORTED");
    }
}
