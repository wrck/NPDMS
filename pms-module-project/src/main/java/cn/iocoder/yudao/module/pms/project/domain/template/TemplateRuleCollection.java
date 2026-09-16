package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Version-local references. This is model normalization, not a rule store or an execution engine. */
public final class TemplateRuleCollection {
    private TemplateRuleCollection() { }

    public static Map<String, VersionRule> index(List<VersionRule> rules) {
        if (rules == null) throw new IllegalArgumentException("版本内规则集合不能为空引用");
        Map<String, VersionRule> result = new LinkedHashMap<>();
        for (VersionRule rule : rules) {
            if (rule == null || !DeliveryDefinitionPayloadValidator.code(rule.key()) || rule.kind() == null)
                throw new IllegalArgumentException("规则需要有效的局部key和结果类型");
            if (result.putIfAbsent(rule.key(), rule) != null) throw new IllegalArgumentException("规则key重复: " + rule.key());
            boolean condition = rule.kind() == VersionRule.Kind.CONDITION;
            if (condition ? rule.expression() == null || rule.decision() != null
                    : rule.decision() == null || rule.expression() != null)
                throw new IllegalArgumentException("规则必须只有一种可编辑来源: " + rule.key());
        }
        return result;
    }

    /** Sidebar/asset-copy input may contain a fresh inline rule; persist only its generated local reference. */
    public static TemplateDesignerDocument forEditing(TemplateDesignerDocument submitted) {
        var result = JsonUtils.parseObject(JsonUtils.toJsonString(submitted), TemplateDesignerDocument.class);
        var rules = index(result.getRules());
        if (result.getStages() != null) for (var stage : result.getStages()) {
            if (stage == null) continue;
            stage.setCompletionRuleKey(capture(rules, stage.getCompletionRuleKey(), stage.getCompletionRule(),
                    stage.getName() + "·完成"));
            stage.setCompletionRule(null);
        }
        if (result.getTasks() != null) for (var task : result.getTasks()) {
            if (task == null) continue;
            task.setCompletionRuleKey(capture(rules, task.getCompletionRuleKey(), task.getCompletionRule(),
                    task.getName() + "·完成"));
            task.setCompletionRule(null);
        }
        if (result.getTransitions() != null) for (var edge : result.getTransitions()) {
            if (edge == null) continue;
            edge.setConditionRuleKey(capture(rules, edge.getConditionRuleKey(), edge.getCondition(),
                    edge.getCode() + "·条件"));
            edge.setCondition(null);
        }
        result.setRules(new ArrayList<>(rules.values()));
        validateReferences(result, rules);
        return result;
    }

    /** Frozen/compiled nodes may carry resolved JSON; it is derived from, never editable beside, the collection. */
    public static TemplateDesignerDocument forCompilation(TemplateDesignerDocument source) {
        var result = forEditing(source);
        var rules = index(result.getRules());
        if (result.getStages() != null) for (var stage : result.getStages())
            if (stage != null) stage.setCompletionRule(resolved(rules, stage.getCompletionRuleKey()));
        if (result.getTasks() != null) for (var task : result.getTasks())
            if (task != null) task.setCompletionRule(resolved(rules, task.getCompletionRuleKey()));
        if (result.getTransitions() != null) for (var edge : result.getTransitions())
            if (edge != null) edge.setCondition(resolved(rules, edge.getConditionRuleKey()));
        return result;
    }

    public static JsonNode condition(Map<String, VersionRule> rules, String key) {
        VersionRule rule = require(rules, key);
        if (rule.kind() != VersionRule.Kind.CONDITION)
            throw new IllegalArgumentException("策略结果必须显式选择输出比较条件，不能直接作为准入/完成判断: " + key);
        return expand(rule.expression(), rules);
    }

    private static JsonNode expand(JsonNode expression, Map<String, VersionRule> rules) {
        JsonNode copy = expression.deepCopy();
        if (copy.has("operator")) {
            var children = JsonUtils.parseObject("[]", tools.jackson.databind.node.ArrayNode.class);
            for (JsonNode child : copy.path("rules")) children.add(expand(child, rules));
            ((ObjectNode) copy).set("rules", children);
        } else if ("DECISION".equals(copy.path("predicate").asText()) && copy.path("parameters").has("ruleKey")) {
            String key = copy.path("parameters").path("ruleKey").asText();
            VersionRule target = require(rules, key);
            if (target.kind() != VersionRule.Kind.DECISION || copy.path("parameters").has("table"))
                throw new IllegalArgumentException("决策引用必须指向本版本策略规则，且不能另存一份可编辑表: " + key);
            ((ObjectNode) copy.path("parameters")).set("table",
                    JsonUtils.parseObject(JsonUtils.toJsonString(target.decision()), JsonNode.class));
            ((ObjectNode) copy.path("parameters")).remove("ruleKey");
        }
        return copy;
    }

    private static TemplateDesignerDocument.RuleSpec resolved(Map<String, VersionRule> rules, String key) {
        if (key == null || key.isBlank()) return null;
        var result = new TemplateDesignerDocument.RuleSpec();
        result.setExpression(condition(rules, key));
        return result;
    }

    private static String capture(Map<String, VersionRule> rules, String key, TemplateDesignerDocument.RuleSpec inline,
                                  String name) {
        if (inline == null || inline.getExpression() == null) return key;
        if (key != null && !key.isBlank()) {
            if (!condition(rules, key).equals(inline.getExpression()))
                throw new IllegalArgumentException("同一规则不能同时修改局部引用和节点内表达式: " + key);
            return key;
        }
        int sequence = rules.size();
        do { key = "rule_" + sequence++; } while (rules.containsKey(key));
        rules.put(key, new VersionRule(key, name, VersionRule.Kind.CONDITION, false, inline.getExpression(), null));
        return key;
    }

    private static void validateReferences(TemplateDesignerDocument document, Map<String, VersionRule> rules) {
        Map<String, Set<String>> owners = new LinkedHashMap<>();
        use(rules, owners, document.getMatchRuleKey(), "template");
        use(rules, owners, document.getClosureRuleKey(), "template");
        if (document.getStages() != null) for (var node : document.getStages()) {
            if (node == null) continue;
            for (String key : new String[]{node.getAdmissionRuleKey(), node.getCompletionRuleKey(), node.getExitRuleKey()})
                use(rules, owners, key, "stage:" + node.getNodeKey());
            operationUses(node.getWorkBinding(), rules, owners, "stage:" + node.getNodeKey());
        }
        if (document.getTasks() != null) for (var node : document.getTasks()) {
            if (node == null) continue;
            for (String key : new String[]{node.getAdmissionRuleKey(), node.getCompletionRuleKey(), node.getExitRuleKey()})
                use(rules, owners, key, "task:" + node.getNodeKey());
            operationUses(node.getWorkBinding(), rules, owners, "task:" + node.getNodeKey());
        }
        if (document.getTransitions() != null) for (var edge : document.getTransitions())
            if (edge != null) use(rules, owners, edge.getConditionRuleKey(), "edge:" + edge.getEdgeKey());
        owners.forEach((key, users) -> {
            if (users.size() > 1 && !rules.get(key).shared())
                throw new IllegalArgumentException("多个节点使用同一规则必须显式共享: " + key + " " + users);
        });
    }

    private static void operationUses(TemplateDesignerDocument.WorkBindingSpec binding,
            Map<String, VersionRule> rules, Map<String, Set<String>> owners, String owner) {
        if (binding == null || binding.getOperationContract() == null) return;
        var contract = cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContractJson
                .readAuthoring(binding.getOperationContract());
        for (var operation : contract.operations()) {
            if ("RULE".equals(operation.pre().mode())) use(rules, owners, operation.pre().ruleKey(), owner);
            if ("RULE".equals(operation.post().mode())) use(rules, owners, operation.post().ruleKey(), owner);
        }
    }

    private static void use(Map<String, VersionRule> rules, Map<String, Set<String>> owners, String key, String owner) {
        if (key == null || key.isBlank()) return;
        VersionRule rule = require(rules, key);
        owners.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(owner);
        if (rule.kind() == VersionRule.Kind.CONDITION) decisionUses(rule.expression(), rules, owners, owner);
    }

    private static void decisionUses(JsonNode node, Map<String, VersionRule> rules, Map<String, Set<String>> owners, String owner) {
        if (node.has("operator")) {
            for (JsonNode child : node.path("rules")) decisionUses(child, rules, owners, owner);
        } else if ("DECISION".equals(node.path("predicate").asText()) && node.path("parameters").has("ruleKey")) {
            String key = node.path("parameters").path("ruleKey").asText();
            if (require(rules, key).kind() != VersionRule.Kind.DECISION)
                throw new IllegalArgumentException("决策引用类型不匹配: " + key);
            owners.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(owner);
        }
    }

    private static VersionRule require(Map<String, VersionRule> rules, String key) {
        VersionRule rule = rules.get(key);
        if (rule == null) throw new IllegalArgumentException("当前版本不存在规则: " + key);
        return rule;
    }
}
