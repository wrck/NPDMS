package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import com.yomahub.liteflow.core.NodeBooleanComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.yomahub.liteflow.annotation.LiteflowComponent;

/** Components only read facts and calculate results. Domain commands execute outside the rule chain. */
public final class ProjectRuleComponents {
    private ProjectRuleComponents() { }

    private static RuleFact checked(RuleProgram.Leaf leaf, RuleFact fact) {
        if (fact == null) return RuleFact.unknown("FACT_UNAVAILABLE");
        if (!fact.available()) return fact;
        // Preflight every required value before native boolean short-circuiting can skip a broken branch.
        if (leaf.predicate().equals("FIELD") || leaf.predicate().equals("DECISION")) {
            try {
                RuleFieldComparison.validateValue(fact.value(), leaf.parameters());
            } catch (RuntimeException invalid) {
                return RuleFact.unknown("FIELD_VALUE_INVALID");
            }
        } else if (!(fact.value() instanceof Boolean)) {
            return RuleFact.unknown("BOOLEAN_FACT_REQUIRED");
        }
        return fact;
    }

    @LiteflowComponent("pmsRuleDecisionValue")
    public static class DecisionValue extends NodeComponent {
        @Override
        public void process() {
            var context = getContextBean(ProjectRuleInvocation.class);
            var leaf = context.program.leaves().getFirst();
            RuleFact result;
            try {
                result = context.resolver.resolve(leaf);
            } catch (RuntimeException unavailable) {
                context.reasonCode = "DECISION_EXECUTION_FAILED";
                context.diagnostics.add(new cn.iocoder.yudao.module.pms.project.domain.rule.RuleDiagnostic(
                        leaf.path(), "pmsRuleDecisionValue", context.reasonCode, null));
                throw new IllegalStateException(context.reasonCode);
            }
            if (result == null || !result.available()) {
                context.reasonCode = result == null ? "DECISION_UNAVAILABLE" : result.reasonCode();
                if (result != null) context.diagnostics.addAll(result.diagnostics());
                context.diagnostics.add(new cn.iocoder.yudao.module.pms.project.domain.rule.RuleDiagnostic(
                        leaf.path(), "pmsRuleDecisionValue", context.reasonCode, null));
                throw new IllegalStateException(context.reasonCode);
            }
            if (!(result.value() instanceof cn.iocoder.yudao.module.pms.project.domain.rule.DecisionRuleEvaluation.Values values)) {
                context.reasonCode = "DECISION_VALUES_REQUIRED";
                context.diagnostics.add(new cn.iocoder.yudao.module.pms.project.domain.rule.RuleDiagnostic(
                        leaf.path(), "pmsRuleDecisionValue", context.reasonCode, null));
                throw new IllegalArgumentException(context.reasonCode);
            }
            context.decisionValues = values;
        }
    }

    @LiteflowComponent("pmsRulePrepare")
    public static class Prepare extends NodeComponent {
        @Override
        public void process() {
            var context = getContextBean(ProjectRuleInvocation.class);
            boolean unavailable = false;
            for (RuleProgram.Leaf leaf : context.program.leaves()) {
                if (leaf.predicate().equals("DECISION")) continue;
                RuleFact fact;
                try {
                    fact = leaf.predicate().equals("CONSTANT")
                            ? RuleFact.known(leaf.parameters().path("value").asBoolean()) : context.resolver.resolve(leaf);
                    fact = checked(leaf, fact);
                } catch (RuntimeException failure) {
                    fact = RuleFact.unknown("FACT_UNAVAILABLE");
                }
                context.facts.put(leaf.key(), fact);
                if (!fact.available()) {
                    unavailable = true;
                    context.diagnostics.addAll(fact.diagnostics());
                    context.conditions.add(new RuleEvaluation.Condition(leaf.key(), leaf.path(), "pmsRulePrepare",
                            RuleEvaluation.Outcome.UNKNOWN, fact.reasonCode()));
                }
            }
            if (unavailable) {
                context.reasonCode = "FACT_UNAVAILABLE";
                throw new IllegalStateException("FACT_UNAVAILABLE");
            }
        }
    }

    @LiteflowComponent("pmsRuleDecisions")
    public static class Decisions extends NodeComponent {
        @Override
        public void process() {
            var context = getContextBean(ProjectRuleInvocation.class);
            for (var leaf : context.program.leaves()) {
                if (!leaf.predicate().equals("DECISION")) continue;
                RuleFact result;
                try {
                    result = checked(leaf, context.resolver.resolve(leaf));
                } catch (RuntimeException failed) {
                    result = RuleFact.unknown("DECISION_EXECUTION_FAILED");
                }
                if (result == null || !result.available()) {
                    context.reasonCode = result == null ? "DECISION_UNAVAILABLE" : result.reasonCode();
                    if (result != null) context.diagnostics.addAll(result.diagnostics());
                    context.conditions.add(new RuleEvaluation.Condition(leaf.key(), leaf.path(), "pmsRuleDecisions",
                            RuleEvaluation.Outcome.UNKNOWN, context.reasonCode));
                    throw new IllegalStateException(context.reasonCode);
                }
                context.facts.put(leaf.key(), result);
            }
        }
    }

    @LiteflowComponent("pmsRulePredicate")
    public static class Predicate extends NodeBooleanComponent {
        @Override
        public boolean processBoolean() {
            var context = getContextBean(ProjectRuleInvocation.class);
            Object value = context.facts.get(getTag()).value();
            if (!(value instanceof Boolean matched)) {
                context.reasonCode = "BOOLEAN_FACT_REQUIRED";
                var leaf = context.leaves.get(getTag());
                context.conditions.add(new RuleEvaluation.Condition(leaf.key(), leaf.path(), "pmsRulePredicate",
                        RuleEvaluation.Outcome.UNKNOWN, context.reasonCode));
                throw new IllegalArgumentException(context.reasonCode);
            }
            return context.record(getTag(), "pmsRulePredicate", matched);
        }
    }

    @LiteflowComponent("pmsRuleField")
    public static class Field extends NodeBooleanComponent {
        @Override
        public boolean processBoolean() {
            var context = getContextBean(ProjectRuleInvocation.class);
            var leaf = context.leaves.get(getTag());
            try {
                boolean matched = RuleFieldComparison.evaluate(context.facts.get(getTag()).value(), leaf.parameters());
                return context.record(getTag(), "pmsRuleField", matched);
            } catch (RuntimeException invalidValue) {
                context.reasonCode = "FIELD_VALUE_INVALID";
                context.conditions.add(new RuleEvaluation.Condition(leaf.key(), leaf.path(), "pmsRuleField",
                        RuleEvaluation.Outcome.UNKNOWN, context.reasonCode));
                throw new IllegalArgumentException(context.reasonCode);
            }
        }
    }

    @LiteflowComponent("pmsRuleMatched")
    public static class Matched extends NodeComponent {
        @Override
        public void process() {
            getContextBean(ProjectRuleInvocation.class).outcome = RuleEvaluation.Outcome.MATCHED;
        }
    }

    @LiteflowComponent("pmsRuleNotMatched")
    public static class NotMatched extends NodeComponent {
        @Override
        public void process() {
            getContextBean(ProjectRuleInvocation.class).outcome = RuleEvaluation.Outcome.NOT_MATCHED;
        }
    }
}
