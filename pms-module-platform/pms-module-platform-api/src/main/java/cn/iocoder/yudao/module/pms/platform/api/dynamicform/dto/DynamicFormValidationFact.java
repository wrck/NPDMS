package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

import java.util.List;

public record DynamicFormValidationFact(String result, List<String> blockerCodes) {
    public DynamicFormValidationFact {
        blockerCodes = blockerCodes == null ? List.of() : List.copyOf(blockerCodes);
    }
}
