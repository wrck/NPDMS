package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

import java.util.Map;

/** Validate Owner-held values without creating or writing a PLT instance. */
public record DynamicFormRevisionValuesQuery(DynamicFormRevisionUsageQuery revision,
                                             Map<String, Object> values) {
}
