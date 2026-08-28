package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;

public record DynamicFormPolicyFact(DynamicFormBusinessAction action, boolean allowed, String stableErrorCode,
                                    Long scopeVersion, String ownerStateSummary) {
}
