package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;

/** Request-local Owner execution context is opaque to PLT and never changes the business object's identity. */
public record DynamicFormPolicyFact(DynamicFormBusinessAction action, boolean allowed, String stableErrorCode,
                                    Long scopeVersion, String ownerStateSummary,
                                    tools.jackson.databind.JsonNode ownerExecutionContext) {
    public DynamicFormPolicyFact(DynamicFormBusinessAction action, boolean allowed, String stableErrorCode,
                                 Long scopeVersion, String ownerStateSummary) {
        this(action, allowed, stableErrorCode, scopeVersion, ownerStateSummary, null);
    }
    public DynamicFormPolicyFact {
        ownerExecutionContext = ownerExecutionContext == null ? null : ownerExecutionContext.deepCopy();
    }
    @Override public tools.jackson.databind.JsonNode ownerExecutionContext() {
        return ownerExecutionContext == null ? null : ownerExecutionContext.deepCopy();
    }
}
