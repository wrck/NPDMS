package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One invocation only; never stored in a component singleton or shared across projects. */
final class ProjectRuleInvocation {
    final RuleProgram program;
    final RuleFact.Resolver resolver;
    final Map<String, RuleFact> facts = new LinkedHashMap<>();
    final Map<String, RuleProgram.Leaf> leaves = new LinkedHashMap<>();
    final List<RuleEvaluation.Condition> conditions = new ArrayList<>();
    final List<cn.iocoder.yudao.module.pms.project.domain.rule.RuleDiagnostic> diagnostics = new ArrayList<>();
    RuleEvaluation.Outcome outcome = RuleEvaluation.Outcome.UNKNOWN;
    String reasonCode;
    cn.iocoder.yudao.module.pms.project.domain.rule.DecisionRuleEvaluation.Values decisionValues;

    ProjectRuleInvocation(RuleProgram program, RuleFact.Resolver resolver) {
        this.program = program;
        this.resolver = resolver;
        program.leaves().forEach(leaf -> leaves.put(leaf.key(), leaf));
    }

    boolean record(String key, String component, boolean matched) {
        RuleProgram.Leaf leaf = leaves.get(key);
        conditions.add(new RuleEvaluation.Condition(key, leaf.path(), component,
                matched ? RuleEvaluation.Outcome.MATCHED : RuleEvaluation.Outcome.NOT_MATCHED, null));
        return matched;
    }
}
