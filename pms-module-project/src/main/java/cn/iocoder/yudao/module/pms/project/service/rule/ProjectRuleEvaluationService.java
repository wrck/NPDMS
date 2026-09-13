package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleResult;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.rule.DecisionRuleEvaluation;
import com.yomahub.liteflow.core.FlowExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectRuleEvaluationService {
    private final FlowExecutor executor;

    public RuleEvaluation evaluate(String ruleVersionRef, RuleProgram program, RuleFact.Resolver resolver) {
        if (program.kind() != VersionRule.Kind.CONDITION)
            throw new IllegalArgumentException("策略结果需要显式选择输出条件，不能直接当作布尔值");
        return (RuleEvaluation) evaluateRule(ruleVersionRef, program, resolver);
    }

    public RuleResult evaluateRule(String ruleVersionRef, RuleProgram program, RuleFact.Resolver resolver) {
        Objects.requireNonNull(ruleVersionRef, "ruleVersionRef");
        var context = new ProjectRuleInvocation(program, resolver);
        try {
            // Native EL caching avoids a second mutable rule registry; the caller pins the immutable program.
            var response = executor.execute2RespWithEL(program.el(), null, null, (Object) context);
            if (!response.isSuccess()) {
                context.outcome = RuleEvaluation.Outcome.UNKNOWN;
                if (context.reasonCode == null) context.reasonCode = "RULE_EXECUTION_FAILED";
            }
            List<String> steps = response.getExecuteStepQueue().stream().map(step -> step.getNodeId()).toList();
            return result(ruleVersionRef, context, steps);
        } catch (RuntimeException failure) {
            context.outcome = RuleEvaluation.Outcome.UNKNOWN;
            context.decisionValues = null;
            context.reasonCode = "RULE_EXECUTION_FAILED";
            return result(ruleVersionRef, context, List.of());
        }
    }

    private static RuleResult result(String reference, ProjectRuleInvocation context, List<String> steps) {
        if (context.program.kind() == VersionRule.Kind.DECISION) {
            boolean available = context.decisionValues != null && context.reasonCode == null;
            return new DecisionRuleEvaluation(reference, available ? DecisionRuleEvaluation.Status.AVAILABLE
                    : DecisionRuleEvaluation.Status.UNKNOWN, available ? context.decisionValues.rows() : List.of(),
                    context.reasonCode, context.conditions, steps, context.diagnostics);
        }
        return new RuleEvaluation(reference, context.outcome, context.reasonCode, context.conditions, steps, context.diagnostics);
    }
}
