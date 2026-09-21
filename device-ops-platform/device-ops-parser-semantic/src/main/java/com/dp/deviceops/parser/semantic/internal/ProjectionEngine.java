package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalog.ProjectionProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProjectionEngine {

    private static final Set<String> FORBIDDEN_SEGMENTS = Set.of("__proto__", "prototype", "constructor");

    public Map<String, Object> project(Map<String, Object> snapshot, List<ProjectionProfile> profiles) {
        Map<String, Object> results = new LinkedHashMap<>();
        for (ProjectionProfile profile : profiles) {
            Map<String, Object> projection = new LinkedHashMap<>();
            for (Map.Entry<String, String> field : profile.fields().entrySet()) {
                Object value = resolve(snapshot, field.getValue());
                if (value != MissingValue.INSTANCE) {
                    projection.put(field.getKey(), value);
                } else if ("NULL".equals(profile.missingValuePolicy())) {
                    projection.put(field.getKey(), null);
                }
            }
            results.put(profile.projectionId(), projection);
        }
        return results;
    }

    private Object resolve(Map<String, Object> snapshot, String semanticPath) {
        String[] segments = safeSegments(semanticPath);
        Object cursor = snapshot.get("entities");
        for (String segment : segments) {
            if (!(cursor instanceof Map<?, ?> map) || !map.containsKey(segment)) {
                return MissingValue.INSTANCE;
            }
            cursor = map.get(segment);
        }
        if (cursor instanceof Map<?, ?> fact && fact.containsKey("semanticKey")) {
            return fact.get("value");
        }
        if (cursor instanceof List<?> list) {
            List<Object> values = new ArrayList<>();
            for (Object item : list) {
                values.add(item instanceof Map<?, ?> fact && fact.containsKey("value")
                        ? fact.get("value") : item);
            }
            return values;
        }
        return cursor;
    }

    private static String[] safeSegments(String path) {
        String[] segments = path.split("\\.", -1);
        for (String segment : segments) {
            if (segment.isBlank() || FORBIDDEN_SEGMENTS.contains(segment)) {
                throw new SemanticParserError(SemanticParserError.INVALID_PROJECTIONS,
                        "projection path is unsafe");
            }
        }
        return segments;
    }

    private enum MissingValue {
        INSTANCE
    }
}
