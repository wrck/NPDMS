package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

import java.util.List;

public record DynamicFormFieldDescriptor(String fieldKey, String componentType, boolean controlledFile,
                                         boolean required, String valueType, Integer minLength,
                                         Integer maxLength, String pattern, List<String> allowedValues) {
    public DynamicFormFieldDescriptor {
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }
}
