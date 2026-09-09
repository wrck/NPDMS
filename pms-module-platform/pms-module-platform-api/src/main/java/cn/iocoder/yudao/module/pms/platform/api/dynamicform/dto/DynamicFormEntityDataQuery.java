package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Owner-supplied entity values are projected and validated, never persisted by PLT. */
public record DynamicFormEntityDataQuery(DynamicFormInstanceQuery context,
                                         Map<String, Object> entityValues) {
    public DynamicFormEntityDataQuery {
        entityValues = entityValues == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(entityValues));
    }
}
