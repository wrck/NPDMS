package cn.iocoder.yudao.module.pms.bindings.backend;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition.ProcessDefinitionSnapshot;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.RuleVerdict;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.BusinessFactPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.FactObservation;

import java.util.ArrayList;
import java.util.List;

/**
 * MEMBER_CONDITION 语义 v1：条件字段编码是关系身份，条件值是成员计数阈值；
 * 计数去重单位为成员对象身份（由事实端口漏页穷举保证）。
 * 空集合计数为已知 0（不是未知），可明确不满足阈值条件；
 * 关系不可读或成员不可读一律未知（satisfied=null 且 collectionComplete=false），取反不得通过。
 */
public final class MemberConditionEvaluator {

    public static final String SEMANTIC_MEMBER_CONDITION = "MEMBER_CONDITION";

    private MemberConditionEvaluator() {
    }

    public static RuleVerdict evaluate(ProcessDefinitionSnapshot snapshot,
                                       cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef ref,
                                       BusinessFactPort factPort, String sceneCode) {
        List<String> diagnostics = new ArrayList<>();
        boolean complete = true;
        for (var condition : snapshot.conditions()) {
            FactObservation observation = factPort.observeMembers(snapshot.ownerModule(),
                    snapshot.entityType(), ref.entityId(), condition.fieldCode(), List.of(), sceneCode);
            Long count = memberCount(observation);
            if (count == null) {
                complete = false;
                diagnostics.add("关系成员事实未知或不可用: " + condition.fieldCode());
                continue;
            }
            Boolean matched = compareCount(count, condition);
            if (matched == null) {
                complete = false;
                diagnostics.add("成员计数条件不可比较: " + condition.fieldCode());
            } else if (!matched) {
                // 明确不满足即可整体否定，无需继续。
                return new RuleVerdict(false, true, snapshot.ruleVersion(), diagnostics);
            }
        }
        if (!complete) {
            return new RuleVerdict(null, false, snapshot.ruleVersion(), diagnostics);
        }
        return new RuleVerdict(true, true, snapshot.ruleVersion(), diagnostics);
    }

    /** EMPTY 是已知空集合（计数 0），不是未知；UNKNOWN/UNAVAILABLE 一律未知。 */
    private static Long memberCount(FactObservation observation) {
        return switch (observation.availability()) {
            case EMPTY -> 0L;
            case VALUE -> observation.value() instanceof Long count ? count : null;
            case UNKNOWN, UNAVAILABLE -> null;
        };
    }

    private static Boolean compareCount(long count,
                                        cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter condition) {
        List<Object> values = condition.values() == null ? List.of() : condition.values();
        if (values.isEmpty() || !(values.get(0) instanceof Number threshold)) {
            return null;
        }
        long expected = threshold.longValue();
        return switch (condition.operator()) {
            case EQ -> count == expected;
            case GTE -> count >= expected;
            case LTE -> count <= expected;
            default -> null;
        };
    }
}
