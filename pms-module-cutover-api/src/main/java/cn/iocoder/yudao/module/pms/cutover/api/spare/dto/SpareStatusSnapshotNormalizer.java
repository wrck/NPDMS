package cn.iocoder.yudao.module.pms.cutover.api.spare.dto;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** CUT-08状态JSON的唯一规范化边界：对象键排序、数组保序并深度冻结。 */
public final class SpareStatusSnapshotNormalizer {

    private static final int MAX_BYTES = 16 * 1024;

    private SpareStatusSnapshotNormalizer() {
    }

    public static Map<String, Object> normalize(Map<String, Object> value) {
        if (value == null) throw invalid();
        Map<String, Object> normalized = normalizeObject(value);
        String json;
        try {
            json = JsonUtils.toJsonString(normalized);
        } catch (RuntimeException exception) {
            throw invalid(exception);
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw invalid();
        return normalized;
    }

    private static Map<String, Object> normalizeObject(Map<?, ?> value) {
        Map<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw invalid();
            sorted.put(key, normalizeValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Object normalizeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        if (value instanceof Map<?, ?> map) return normalizeObject(map);
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            list.forEach(item -> normalized.add(normalizeValue(item)));
            return Collections.unmodifiableList(normalized);
        }
        throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid statusSnapshot");
    }

    private static IllegalArgumentException invalid(Throwable cause) {
        return new IllegalArgumentException("invalid statusSnapshot", cause);
    }
}
