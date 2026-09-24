package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleResult;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.rule.DecisionRuleEvaluation;
import com.yomahub.liteflow.core.FlowExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
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
        var context = new ProjectRuleInvocation(program, chainReplayingResolver(program, resolver));
        try {
            // Native EL caching avoids a second mutable rule registry; the caller pins the immutable program.
            var response = executor.execute2RespWithEL(program.el(), null, null, (Object) context);
            if (!response.isSuccess()) {
                context.outcome = RuleEvaluation.Outcome.UNKNOWN;
                if (context.reasonCode == null) context.reasonCode = "RULE_EXECUTION_FAILED";
                log.warn("rule[{}] execution failed: {}", ruleVersionRef,
                        response.getCause() == null ? "unknown" : response.getCause().getMessage(), response.getCause());
            }
            List<String> steps = response.getExecuteStepQueue().stream().map(step -> step.getNodeId()).toList();
            return result(ruleVersionRef, context, steps);
        } catch (RuntimeException failure) {
            context.outcome = RuleEvaluation.Outcome.UNKNOWN;
            context.decisionValues = null;
            context.reasonCode = "RULE_EXECUTION_FAILED";
            log.warn("rule[{}] compilation/execution threw: {}", ruleVersionRef, failure.getMessage(), failure);
            return result(ruleVersionRef, context, List.of());
        }
    }

    /**
     * 事实解析器可能嵌套评估另一条规则（如 DELIVERABLE 引用会评估交付件确认规则）。LiteFlow 按归一化 EL 复用
     * Chain 对象，链内同线程重入会破坏其节点引用栈并吞掉真实异常。因此在链外预解析全部提供者叶子，
     * 链内只回放结果；CONSTANT 不经解析器、DECISION 由 pmsRuleDecisions 在链内处理，均保持原语义。
     */
    private RuleFact.Resolver chainReplayingResolver(RuleProgram program, RuleFact.Resolver resolver) {
        Map<String, RuleFact> resolved = new LinkedHashMap<>();
        for (RuleProgram.Leaf leaf : program.leaves()) {
            if (leaf.predicate().equals("CONSTANT") || leaf.predicate().equals("DECISION")) continue;
            RuleFact fact;
            try {
                fact = resolver.resolve(leaf);
            } catch (RuntimeException failure) {
                fact = RuleFact.unknown("FACT_UNAVAILABLE");
            }
            resolved.put(leaf.key(), fact);
        }
        return leaf -> {
            RuleFact fact = resolved.get(leaf.key());
            return fact != null ? fact : resolver.resolve(leaf);
        };
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
