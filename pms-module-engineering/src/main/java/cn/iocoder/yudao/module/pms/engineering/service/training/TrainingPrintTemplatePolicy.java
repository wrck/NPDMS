package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import org.springframework.stereotype.Component;

@Component
public class TrainingPrintTemplatePolicy implements DynamicFormBusinessObjectPolicyProvider {
    public static final DynamicFormProviderKey KEY = new DynamicFormProviderKey("IMP", "TRAINING_PRINT");
    public static final String USAGE = "TRAINING_PRINT";
    @Override public DynamicFormProviderKey providerKey() { return KEY; }
    @Override public DynamicFormPolicyFact inspectRevisionCompatibility(DynamicFormRevisionPolicyQuery query) {
        var fields = query.fields().stream().map(DynamicFormFieldDescriptor::fieldKey)
                .collect(java.util.stream.Collectors.toSet());
        boolean allowed = USAGE.equals(query.requiredUsage()) && (fields.contains("trainingPrintLayout")
                || fields.containsAll(java.util.Set.of("name", "content", "trainingTime", "signatureImageDataUrl")));
        return new DynamicFormPolicyFact(query.action(), allowed, allowed ? null : "TRAINING_PRINT_LAYOUT_REQUIRED",
                query.revisionFactVersion().longValue(), USAGE);
    }
    @Override public DynamicFormPolicyFact inspectInstanceOwnerPolicy(DynamicFormInstancePolicyQuery query) {
        return new DynamicFormPolicyFact(query.action(), false, "TRAINING_PRINT_HAS_NO_FORM_INSTANCE", null, USAGE);
    }
    @Override public DynamicFormPolicyFact lockAndRevalidateInstanceOwnerPolicy(DynamicFormPolicyRevalidationQuery query) {
        return new DynamicFormPolicyFact(query.expectedFact().action(), false, "TRAINING_PRINT_HAS_NO_FORM_INSTANCE", null, USAGE);
    }
}
