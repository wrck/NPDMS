package cn.iocoder.yudao.module.pms.platform.domain.businessview;

import tools.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** PM-03 / F-PLT-003: local value checks, never executable lookup or authorization. */
final class BusinessViewRules {

    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");
    private static final Pattern RELEASE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.+\\-]*");

    private BusinessViewRules() {
    }

    static String key(String value, String field) {
        int maxLength = switch (field.substring(field.lastIndexOf('.') + 1)) {
            case "entityType" -> 64;
            case "ownerContext" -> 32;
            default -> 128;
        };
        require(value != null && value.length() <= maxLength && KEY.matcher(value).matches(),
                field + ": invalid stable key (maximum " + maxLength + ")");
        return value;
    }

    static String componentVersion(String value) {
        require(value != null && value.length() <= 64 && RELEASE.matcher(value).matches(),
                "componentVersion: invalid release label (maximum 64)");
        return value;
    }

    static JsonNode schema(JsonNode value) {
        require(value != null && value.isObject(), "contextSchema: OBJECT required");
        return value.deepCopy();
    }

    static JsonNode actions(JsonNode value, String field) {
        require(value != null && value.isArray() && !value.isEmpty(), field + ": nonempty array required");
        Set<String> keys = new LinkedHashSet<>();
        for (JsonNode action : value) {
            require(action.isTextual(), field + ": stable string keys required");
            String key = key(action.textValue(), field);
            require(keys.add(key), field + ": duplicate action " + key);
        }
        return value.deepCopy();
    }

    static Set<String> actionKeys(JsonNode actions) {
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode action : actions) {
            result.add(action.textValue());
        }
        return Set.copyOf(result);
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
