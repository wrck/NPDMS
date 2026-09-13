package cn.iocoder.yudao.module.pms.project.domain.rule;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DecisionRuleEvaluation(String ruleVersionRef, Status status, List<Map<String, Object>> values,
                                     String reasonCode, List<RuleEvaluation.Condition> conditions,
                                     List<String> steps, List<RuleDiagnostic> diagnostics) implements RuleResult {
    public enum Status { AVAILABLE, UNKNOWN }

    /** Internal typed carrier for the native decision component, separate from scalar condition facts. */
    public record Values(List<Map<String, Object>> rows) { }

    public DecisionRuleEvaluation {
        values = values.stream().map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList();
        conditions = List.copyOf(conditions);
        steps = List.copyOf(steps);
        diagnostics = List.copyOf(diagnostics);
    }
}
