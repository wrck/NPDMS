package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContract.Check;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 节点独立配置的严格业务边界；不查询目录、不授权、不执行命令。 */
public final class TemplateExecutionConfiguration {
    private TemplateExecutionConfiguration() { }

    public record Definition(List<Operation> operations, List<Subscription> subscriptions, Presentation presentation) {
        public Definition { operations = List.copyOf(operations); subscriptions = List.copyOf(subscriptions); }
    }
    public record Operation(String ownerContext, String entityType, String permissionCode, String operationCode,
                            Check pre, Check post) { }
    public record Scope(String mode, List<String> objectIds) {
        public Scope { objectIds = List.copyOf(objectIds); }
    }
    public record Policy(String acquisition, String validity, String selection, String pinnedResultId) { }
    public record Subscription(String key, String ownerContext, String entityType, String resultType,
                               Scope scope, Policy policy) { }
    public record Presentation(String pageUrl, Map<String, String> query) {
        public Presentation { query = Collections.unmodifiableMap(new LinkedHashMap<>(query)); }
    }
    public record Node(String kind, String nodeKey, String path, JsonNode value,
                       TemplateDesignerDocument.WorkBindingSpec binding) { }

    public static List<Node> nodes(TemplateDesignerDocument source) {
        List<Node> nodes = new ArrayList<>();
        if (source == null) return nodes;
        if (source.getStages() != null) for (int i = 0; i < source.getStages().size(); i++) {
            var node = source.getStages().get(i);
            if (node != null && node.getExecution() != null)
                nodes.add(new Node("STAGE", node.getNodeKey(), "stages[" + i + "].execution", node.getExecution(), node.getWorkBinding()));
        }
        if (source.getTasks() != null) for (int i = 0; i < source.getTasks().size(); i++) {
            var node = source.getTasks().get(i);
            if (node != null && node.getExecution() != null)
                nodes.add(new Node("TASK", node.getNodeKey(), "tasks[" + i + "].execution", node.getExecution(), node.getWorkBinding()));
        }
        return nodes;
    }

    public static Definition read(JsonNode value) {
        object(value, "execution", Set.of("operations", "subscriptions", "presentation"));
        List<Operation> operations = new ArrayList<>();
        for (JsonNode row : array(value, "operations")) {
            object(row, "operations", Set.of("ownerContext", "entityType", "permissionCode", "operationCode", "pre", "post"));
            operations.add(new Operation(text(row, "ownerContext"), text(row, "entityType"), text(row, "permissionCode"),
                    optionalText(row, "operationCode"), check(row.get("pre"), "pre"), check(row.get("post"), "post")));
        }
        List<Subscription> subscriptions = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (JsonNode row : array(value, "subscriptions")) {
            object(row, "subscriptions", Set.of("key", "ownerContext", "entityType", "resultType", "scope", "policy"));
            String key = text(row, "key");
            require(DeliveryDefinitionPayloadValidator.code(key) && keys.add(key), "subscriptions.key");
            Scope scope = scope(row.get("scope"));
            Policy policy = policy(row.get("policy"));
            require(!"ALL_EXPECTED".equals(policy.selection()) || "OBJECTS".equals(scope.mode()), "policy.ALL_EXPECTED.scope");
            subscriptions.add(new Subscription(key, text(row, "ownerContext"), text(row, "entityType"),
                    text(row, "resultType"), scope, policy));
        }
        Presentation presentation = null;
        if (value.has("presentation")) {
            var row = value.get("presentation");
            object(row, "presentation", Set.of("pageUrl", "query"));
            Map<String, String> query = new LinkedHashMap<>();
            if (row.has("query")) {
                JsonNode params = row.get("query");
                require(params.isObject(), "presentation.query");
                for (var entry : params.properties()) {
                    require(!entry.getKey().isBlank() && entry.getValue().isTextual(), "presentation.query." + entry.getKey());
                    query.put(entry.getKey(), entry.getValue().textValue());
                }
            }
            presentation = new Presentation(text(row, "pageUrl"), query);
        }
        return new Definition(operations, subscriptions, presentation);
    }

    private static Scope scope(JsonNode row) {
        object(row, "scope", Set.of("mode", "objectIds"));
        String mode = choice(row, "mode", Set.of("PROJECT", "OBJECTS"));
        List<String> ids = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (JsonNode id : array(row, "objectIds")) {
            require(id.isTextual() && !id.textValue().isBlank() && unique.add(id.textValue()), "scope.objectIds");
            ids.add(id.textValue());
        }
        require("OBJECTS".equals(mode) ? !ids.isEmpty() : !row.has("objectIds"), "scope.objectIds");
        return new Scope(mode, ids);
    }

    private static Policy policy(JsonNode row) {
        object(row, "policy", Set.of("acquisition", "validity", "selection", "pinnedResultId"));
        String acquisition = choice(row, "acquisition", Set.of("REUSE_EXISTING", "NEW_RESULT", "PINNED_RESULT"));
        String validity = choice(row, "validity", Set.of("HISTORICAL_FACT", "CURRENT_VALID"));
        String selection = choice(row, "selection", Set.of("EXACT_ONE", "ANY_MATCHING", "ALL_EXPECTED"));
        String pinned = optionalText(row, "pinnedResultId");
        require("PINNED_RESULT".equals(acquisition) == (pinned != null), "policy.pinnedResultId");
        return new Policy(acquisition, validity, selection, pinned);
    }

    private static Check check(JsonNode row, String path) {
        object(row, path, Set.of("mode", "ruleKey"));
        String mode = choice(row, "mode", Set.of("NONE", "RULE"));
        String key = optionalText(row, "ruleKey");
        require("RULE".equals(mode) == (key != null), path + ".ruleKey");
        return new Check(mode, key);
    }

    private static Iterable<JsonNode> array(JsonNode value, String field) {
        if (!value.has(field)) return List.of();
        require(value.get(field).isArray(), field);
        return value.get(field);
    }
    private static void object(JsonNode value, String path, Set<String> allowed) {
        require(value != null && value.isObject(), path);
        for (var entry : value.properties()) require(allowed.contains(entry.getKey()), path + "." + entry.getKey());
    }
    private static String text(JsonNode value, String field) {
        JsonNode node = value.get(field);
        require(node != null && node.isTextual() && !node.textValue().isBlank(), field);
        return node.textValue();
    }
    private static String optionalText(JsonNode value, String field) { return value.has(field) ? text(value, field) : null; }
    private static String choice(JsonNode value, String field, Set<String> choices) {
        String selected = text(value, field);
        require(choices.contains(selected), field);
        return selected;
    }
    private static void require(boolean valid, String path) {
        if (!valid) throw new IllegalArgumentException("EXECUTION_CONFIGURATION_INVALID: " + path);
    }
}
