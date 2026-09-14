package cn.iocoder.yudao.module.pms.project.domain.rule;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.OptBoolean;
import java.util.List;

/** No engine-specific types or raw business values cross the application boundary. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind", requireTypeIdForSubtypes = OptBoolean.FALSE)
public record RuleEvaluation(String ruleVersionRef, Outcome outcome, String reasonCode,
                             List<Condition> conditions, List<String> steps, List<RuleDiagnostic> diagnostics) implements RuleResult {
    public enum Outcome { MATCHED, NOT_MATCHED, UNKNOWN }

    public record Condition(String key, String path, String component, Outcome outcome, String reasonCode) { }

    public RuleEvaluation {
        conditions = List.copyOf(conditions);
        steps = List.copyOf(steps);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean matched() {
        return outcome == Outcome.MATCHED;
    }
}
