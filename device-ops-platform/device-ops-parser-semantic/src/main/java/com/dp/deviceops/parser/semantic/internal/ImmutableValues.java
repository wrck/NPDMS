package com.dp.deviceops.parser.semantic.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

public final class ImmutableValues {

    private ImmutableValues() {
    }

    public static Map<String, Object> deepImmutableMap(Map<String, ?> source) {
        Objects.requireNonNull(source, "source");
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(Objects.requireNonNull(key, "map key"), deepImmutable(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object deepImmutable(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> copy.put(String.valueOf(key), deepImmutable(nested)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ImmutableValues::deepImmutable).toList();
        }
        return value;
    }
}
