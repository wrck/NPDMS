package cn.iocoder.yudao.module.pms.project.domain.template.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Strict boundary for the authored sub-contract. Browser JSON never supplies compiled programs. */
public final class TemplateOperationContractJson {
    private TemplateOperationContractJson() { }

    public static TemplateOperationContract readAuthoring(JsonNode node) {
        if (node == null) return null;
        object(node, "operationContract", Set.of("version", "operations"));
        int version = integer(node.get("version"), "operationContract.version");
        JsonNode rows = node.get("operations");
        if (rows == null || !rows.isArray() || rows.isEmpty())
            throw invalid("operationContract.operations");
        List<TemplateOperationContract.Operation> operations = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String path = "operationContract.operations[" + i + "]";
            JsonNode row = rows.get(i);
            object(row, path, Set.of("operationCode", "operationVersion", "pre", "post"));
            operations.add(new TemplateOperationContract.Operation(text(row.get("operationCode"), path + ".operationCode"),
                    integer(row.get("operationVersion"), path + ".operationVersion"),
                    check(row.get("pre"), path + ".pre"), check(row.get("post"), path + ".post")));
        }
        return new TemplateOperationContract(version, operations);
    }

    public static JsonNode authoring(TemplateOperationContract contract) {
        Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("version", contract.version());
        value.put("operations", contract.operations().stream().map(operation -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("operationCode", operation.operationCode());
            row.put("operationVersion", operation.operationVersion());
            row.put("pre", checkValue(operation.pre()));
            row.put("post", checkValue(operation.post()));
            return row;
        }).toList());
        return JsonUtils.parseTree(JsonUtils.toJsonString(value));
    }

    private static Map<String, Object> checkValue(TemplateOperationContract.Check check) {
        return "NONE".equals(check.mode()) ? Map.of("mode", "NONE")
                : Map.of("mode", "RULE", "ruleKey", check.ruleKey());
    }

    private static TemplateOperationContract.Check check(JsonNode node, String path) {
        if (node == null || !node.isObject()) throw invalid(path);
        String mode = text(node.get("mode"), path + ".mode");
        if ("NONE".equals(mode)) {
            object(node, path, Set.of("mode"));
            return new TemplateOperationContract.Check(mode, null);
        }
        if (!"RULE".equals(mode)) throw invalid(path + ".mode");
        object(node, path, Set.of("mode", "ruleKey"));
        return new TemplateOperationContract.Check(mode, text(node.get("ruleKey"), path + ".ruleKey"));
    }

    private static void object(JsonNode node, String path, Set<String> allowed) {
        if (node == null || !node.isObject()) throw invalid(path);
        for (var property : node.properties())
            if (!allowed.contains(property.getKey())) throw invalid(path + "." + property.getKey());
    }
    private static String text(JsonNode node, String path) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) throw invalid(path);
        return node.asText();
    }
    private static int integer(JsonNode node, String path) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() < 1)
            throw invalid(path);
        return node.intValue();
    }
    private static IllegalArgumentException invalid(String path) {
        return new IllegalArgumentException("OPERATION_CONTRACT_INVALID: " + path);
    }

    /** Sorted object keys, preserved array order and scalar JSON types for semantic hashing. */
    public static Object semanticValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) {
            Map<String, Object> result = new TreeMap<>();
            for (var entry : node.properties()) result.put(entry.getKey(), semanticValue(entry.getValue()));
            return result;
        }
        if (node.isArray()) {
            List<Object> result = new ArrayList<>();
            for (JsonNode item : node) result.add(semanticValue(item));
            return result;
        }
        if (node.isTextual()) return node.asText();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) return node.bigIntegerValue();
        if (node.isNumber()) return node.decimalValue();
        throw invalid("nonJsonValue");
    }
}
