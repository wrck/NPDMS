package cn.iocoder.yudao.module.pms.bindings.backend;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition.ProcessDefinitionSnapshot;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.RuleVerdict;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.BusinessFactPort;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;

/**
 * 规则语义分发：按定义冻结的 ruleCode 选择判定实现；
 * 未声明的语义显式拒绝，不翻译成近似条件。
 */
public final class RuleSemanticEvaluator {

    private RuleSemanticEvaluator() {
    }

    public static RuleVerdict evaluate(ProcessDefinitionSnapshot snapshot, EntityRef ref,
                                       BusinessFactPort factPort, String sceneCode) {
        return switch (snapshot.ruleCode()) {
            case FieldConditionEvaluator.SEMANTIC_FIELD_CONDITION -> {
                var facts = ConditionFacts.collect(factPort, snapshot, ref, sceneCode);
                yield FieldConditionEvaluator.evaluate(snapshot.conditions(), facts, snapshot.ruleVersion());
            }
            case MemberConditionEvaluator.SEMANTIC_MEMBER_CONDITION ->
                    MemberConditionEvaluator.evaluate(snapshot, ref, factPort, sceneCode);
            default -> throw new IllegalArgumentException(
                    "RULE_SEMANTIC_UNSUPPORTED: " + snapshot.ruleCode());
        };
    }
}
