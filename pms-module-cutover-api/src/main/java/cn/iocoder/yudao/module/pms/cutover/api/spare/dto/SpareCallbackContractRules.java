package cn.iocoder.yudao.module.pms.cutover.api.spare.dto;

import cn.iocoder.yudao.module.pms.cutover.api.spare.CutoverSpareCallbackException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class SpareCallbackContractRules {
    private SpareCallbackContractRules() { }

    static long positive(Long value, String field) {
        if (value == null || value <= 0) throw invalid(field);
        return value;
    }

    static String text(String value, int max, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw invalid(field);
        }
        return value;
    }

    static Map<String, Object> jsonObject(Map<String, Object> value, String field) {
        if (value == null) throw invalid(field);
        String json;
        try {
            json = JsonUtils.toJsonString(value);
        } catch (RuntimeException exception) {
            throw invalid(field);
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > 16 * 1024) {
            throw invalid(field);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    static CutoverSpareCallbackException invalid(String field) {
        return new CutoverSpareCallbackException(CutoverSpareCallbackException.Code.INVALID_REQUEST,
                "invalid " + field);
    }
}
