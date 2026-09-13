package cn.iocoder.yudao.module.pms.project.domain.rule;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/** Shared version/diagnostics contract; decision values are not truthy/falsy conditions. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RuleEvaluation.class, name = "CONDITION"),
        @JsonSubTypes.Type(value = DecisionRuleEvaluation.class, name = "DECISION")
})
public sealed interface RuleResult permits RuleEvaluation, DecisionRuleEvaluation {
    String ruleVersionRef();
    String reasonCode();
    List<RuleEvaluation.Condition> conditions();
    List<String> steps();
    List<RuleDiagnostic> diagnostics();
}
