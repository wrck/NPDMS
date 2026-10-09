package cn.iocoder.yudao.module.pms.bindings.backend;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.RuleSemanticRequest;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.RuleVerdict;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.FactObservation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FIELD_CONDITION 语义的字段条件判定：所有条件满足才算满足；
 * 任一事实未知/不可用即整体未知，否定未知不得通过。
 * 两种架构的后端各自持有判定实现，但同一语义输入必须得出一致结论。
 */
public final class FieldConditionEvaluator {

    public static final String SEMANTIC_FIELD_CONDITION = "FIELD_CONDITION";

    private FieldConditionEvaluator() {
    }

    public static RuleVerdict evaluate(List<BusinessFieldFilter> conditions,
                                       Map<String, FactObservation> facts, String ruleVersion) {
        return cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.BusinessConditionEvaluator
                .evaluate(conditions, facts, ruleVersion);
    }

    /** 供两后端构造同构的语义请求。 */
    public static RuleSemanticRequest semanticRequest(String ruleCode, String ruleVersion,
                                                      Map<String, FactObservation> facts) {
        return new RuleSemanticRequest(ruleCode, ruleVersion, Map.copyOf(facts));
    }

    public static boolean consistentWith(RuleVerdict left, RuleVerdict right) {
        return Objects.equals(left.satisfied(), right.satisfied())
                && left.collectionComplete() == right.collectionComplete();
    }
}
