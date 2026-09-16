package com.dp.plat.lowcode.engine.rule.impl;

import com.dp.plat.lowcode.engine.apm.LowCodeApmService;
import com.dp.plat.lowcode.engine.rule.AviatorSandboxExecutor;
import com.dp.plat.lowcode.engine.rule.LiteFlowExecutor;
import com.dp.plat.lowcode.engine.rule.RuleEngineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则引擎服务实现。
 *
 * <p>决策表：解析 JSON，按行匹配条件后返回行动作。同时支持两种 definition 格式：
 * <ul>
 *   <li>新格式（结构化）：{hitPolicy, conditionColumns:[{field, operator}], actionColumns:[{field}],
 *       rows:[{conditions:[{value}], actions:[{value}]}]}</li>
 *   <li>旧格式（内联）：{rows:[{conditions:[{field, operator, value}], actions:{field, value}}]}</li>
 * </ul>
 * 加载时优先按新格式解析（含 conditionColumns / hitPolicy），否则回退旧格式，保证向后兼容。</p>
 *
 * <p>Hit Policy（仅新格式生效，旧格式默认返回全部命中）：
 * <ul>
 *   <li>FIRST：匹配首行即停止，返回该行 actions</li>
 *   <li>ALL / COLLECT：匹配全部行，返回所有命中 actions 列表</li>
 * </ul></p>
 *
 * <p>表达式：通过 Aviator 求值。LiteFlow：通过 {@link LiteFlowExecutor} 执行 EL 表达式。</p>
 *
 * <p><b>APM 指标</b>（批次5-T9）：决策表/表达式/LiteFlow 执行均通过 {@link LowCodeApmService} 记录
 * Micrometer 指标，APM 记录为 best-effort，服务未注入时 no-op。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineServiceImpl implements RuleEngineService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final LiteFlowExecutor liteFlowExecutor;

    /** Aviator 沙箱执行器（禁用 NewInstance/Module/InternalVars 与危险系统函数，防 RCE） */
    private final AviatorSandboxExecutor aviatorSandbox;

    /** APM 指标服务（可选注入，未注入时 no-op） */
    @Autowired(required = false)
    private LowCodeApmService apmService;

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> executeDecisionTable(String definition, Map<String, Object> facts) {
        long startMs = System.currentTimeMillis();
        boolean success = true;
        try {
            if (definition == null || definition.isBlank()) {
                return List.of();
            }
            Map<String, Object> table = objectMapper.readValue(definition, Map.class);
            // 新格式优先：包含 conditionColumns 或 hitPolicy
            if (table.containsKey("conditionColumns") || table.containsKey("hitPolicy")) {
                return executeNewFormat(table, facts);
            }
            // 旧格式回退：rows.conditions/actions 内联
            return executeLegacyFormat(table, facts);
        } catch (Exception e) {
            success = false;
            throw new RuntimeException("决策表执行失败", e);
        } finally {
            if (apmService != null) {
                apmService.recordRuleExecution("decision-table",
                        System.currentTimeMillis() - startMs, success);
            }
        }
    }

    /**
     * 新格式执行：列结构独立定义，行内仅存值。按 hitPolicy 控制匹配范围。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> executeNewFormat(Map<String, Object> table, Map<String, Object> facts) {
        String hitPolicy = (String) table.getOrDefault("hitPolicy", "FIRST");
        List<Map<String, Object>> conditionColumns = (List<Map<String, Object>>) table.get("conditionColumns");
        List<Map<String, Object>> actionColumns = (List<Map<String, Object>>) table.get("actionColumns");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) table.get("rows");
        List<Map<String, Object>> hits = new ArrayList<>();
        if (rows == null) return hits;
        boolean firstOnly = "FIRST".equalsIgnoreCase(hitPolicy);
        for (Map<String, Object> row : rows) {
            if (matchNewRow(conditionColumns, row, facts)) {
                hits.add(buildActions(actionColumns, row));
                if (firstOnly) break;
            }
        }
        return hits;
    }

    /**
     * 新格式行匹配：按 conditionColumns 顺序取 row.conditions[i].value 与 facts[field] 比较。
     */
    @SuppressWarnings("unchecked")
    private boolean matchNewRow(List<Map<String, Object>> conditionColumns, Map<String, Object> row,
                                Map<String, Object> facts) {
        if (conditionColumns == null) return true;
        List<?> conditions = (List<?>) row.get("conditions");
        for (int i = 0; i < conditionColumns.size(); i++) {
            Map<String, Object> col = conditionColumns.get(i);
            String field = (String) col.get("field");
            String op = (String) col.get("operator");
            Object condItem = conditions != null && i < conditions.size() ? conditions.get(i) : null;
            // 兼容两种格式：值数组 ["P1"] 和对象数组 [{"value":"P1"}]
            Object expected = condItem instanceof Map ? ((Map<String, Object>) condItem).get("value") : condItem;
            Object actual = facts.get(field);
            if (!matchOperator(actual, op, expected)) return false;
        }
        return true;
    }

    /**
     * 新格式行动作构建：按 actionColumns 顺序取 row.actions[i].value 组装 {field: value}。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildActions(List<Map<String, Object>> actionColumns, Map<String, Object> row) {
        Map<String, Object> actions = new LinkedHashMap<>();
        if (actionColumns == null) return actions;
        List<?> rowActions = (List<?>) row.get("actions");
        for (int i = 0; i < actionColumns.size(); i++) {
            String field = (String) actionColumns.get(i).get("field");
            Object actionItem = rowActions != null && i < rowActions.size() ? rowActions.get(i) : null;
            // 兼容两种格式：值数组 [1] 和对象数组 [{"value":1}]
            Object value = actionItem instanceof Map ? ((Map<String, Object>) actionItem).get("value") : actionItem;
            actions.put(field, value);
        }
        return actions;
    }

    /**
     * 旧格式执行：rows 内联 conditions/actions，行为与历史一致（返回全部命中）。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> executeLegacyFormat(Map<String, Object> table, Map<String, Object> facts) {
        List<Map<String, Object>> hitActions = new ArrayList<>();
        List<Map<String, Object>> rows = (List<Map<String, Object>>) table.get("rows");
        if (rows == null) return hitActions;
        for (Map<String, Object> row : rows) {
            if (matchRow((List<Map<String, Object>>) row.get("conditions"), facts)) {
                hitActions.add((Map<String, Object>) row.get("actions"));
            }
        }
        return hitActions;
    }

    @SuppressWarnings("unchecked")
    private boolean matchRow(List<Map<String, Object>> conditions, Map<String, Object> facts) {
        if (conditions == null) return true;
        for (Map<String, Object> cond : conditions) {
            String field = (String) cond.get("field");
            String op = (String) cond.get("operator");
            Object expected = cond.get("value");
            Object actual = facts.get(field);
            if (!matchOperator(actual, op, expected)) return false;
        }
        return true;
    }

    /**
     * 操作符匹配（含类型兼容）。
     *
     * <p>数值比较统一转 Double，避免 Integer/Long/Double 及数字字符串间的不等；
     * IN 支持 List 与逗号分隔字符串两种形式；EQ/NE 经类型兼容比较。</p>
     */
    private boolean matchOperator(Object actual, String op, Object expected) {
        if (actual == null) {
            if ("NE".equals(op)) return expected != null;
            return false;
        }
        return switch (op) {
            case "EQ" -> equalsWithCoercion(actual, expected);
            case "NE" -> !equalsWithCoercion(actual, expected);
            case "GT" -> compareWithCoercion(actual, expected) > 0;
            case "GE" -> compareWithCoercion(actual, expected) >= 0;
            case "LT" -> compareWithCoercion(actual, expected) < 0;
            case "LE" -> compareWithCoercion(actual, expected) <= 0;
            case "IN" -> containsWithCoercion(actual, expected);
            default -> false;
        };
    }

    /** 类型兼容的相等比较：数值统一转 Double，否则字符串比较 */
    private boolean equalsWithCoercion(Object actual, Object expected) {
        if (actual == null || expected == null) return actual == expected;
        Double a = toDoubleOrNull(actual);
        Double b = toDoubleOrNull(expected);
        if (a != null && b != null) return a.equals(b);
        return actual.toString().equals(expected.toString());
    }

    /** 类型兼容的大小比较：数值按 Double 比较，否则按字符串字典序 */
    private int compareWithCoercion(Object a, Object b) {
        Double da = toDoubleOrNull(a);
        Double db = toDoubleOrNull(b);
        if (da != null && db != null) return Double.compare(da, db);
        return a.toString().compareTo(b.toString());
    }

    /** IN 操作：expected 为 List 时逐项比较；否则按逗号分隔字符串处理（容错） */
    private boolean containsWithCoercion(Object actual, Object expected) {
        if (expected instanceof List<?> list) {
            for (Object e : list) {
                if (equalsWithCoercion(actual, e)) return true;
            }
            return false;
        }
        String s = expected == null ? "" : expected.toString();
        for (String part : s.split(",")) {
            if (equalsWithCoercion(actual, part.trim())) return true;
        }
        return false;
    }

    /** 转换为 Double，无法转换返回 null（布尔不视作数值） */
    private Double toDoubleOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof Boolean) return null;
        String s = o.toString().trim();
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Object executeExpression(String expression, Map<String, Object> context) {
        long startMs = System.currentTimeMillis();
        boolean success = true;
        try {
            return aviatorSandbox.execute(expression, context);
        } catch (Exception e) {
            success = false;
            throw e;
        } finally {
            if (apmService != null) {
                apmService.recordRuleExecution("expression",
                        System.currentTimeMillis() - startMs, success);
            }
        }
    }

    @Override
    public Object executeLiteFlow(String el, Map<String, Object> context) {
        long startMs = System.currentTimeMillis();
        boolean success = true;
        try {
            return liteFlowExecutor.execute(el, context);
        } catch (Exception e) {
            success = false;
            throw e;
        } finally {
            if (apmService != null) {
                apmService.recordRuleExecution("liteflow",
                        System.currentTimeMillis() - startMs, success);
            }
        }
    }
}
