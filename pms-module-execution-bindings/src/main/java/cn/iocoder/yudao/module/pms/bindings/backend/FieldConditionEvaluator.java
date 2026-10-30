package cn.iocoder.yudao.module.pms.bindings.backend;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.RuleSemanticRequest;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.RuleVerdict;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.FactObservation;

import java.util.ArrayList;
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
        List<String> diagnostics = new ArrayList<>();
        boolean complete = true;
        for (BusinessFieldFilter condition : conditions) {
            FactObservation observation = facts.get(condition.fieldCode());
            if (observation == null || observation.availability() == FactObservation.Availability.UNKNOWN
                    || observation.availability() == FactObservation.Availability.UNAVAILABLE) {
                complete = false;
                diagnostics.add("字段事实未知或不可用: " + condition.fieldCode());
                continue;
            }
            Boolean matched = matches(observation, condition);
            if (matched == null) {
                complete = false;
                diagnostics.add("条件不可比较: " + condition.fieldCode());
            } else if (!matched) {
                // 明确不满足即可整体否定，无需继续。
                return new RuleVerdict(false, true, ruleVersion, diagnostics);
            }
        }
        if (!complete) {
            return new RuleVerdict(null, false, ruleVersion, diagnostics);
        }
        return new RuleVerdict(true, true, ruleVersion, diagnostics);
    }

    /** 单条件比较；无法比较返回 null（未知），不猜测。 */
    private static Boolean matches(FactObservation observation, BusinessFieldFilter condition) {
        boolean isEmpty = observation.availability() == FactObservation.Availability.EMPTY;
        List<Object> values = condition.values() == null ? List.of() : condition.values();
        return switch (condition.operator()) {
            case IS_NULL -> isEmpty;
            case NOT_NULL -> !isEmpty;
            case EQ -> isEmpty ? values.isEmpty() : compare(observation.value(), values, values::contains);
            case NE -> isEmpty ? !values.isEmpty()
                    : compare(observation.value(), values, value -> !values.contains(value));
            case IN -> isEmpty ? Boolean.FALSE : compare(observation.value(), values, values::contains);
            case LIKE -> isEmpty ? Boolean.FALSE : compareText(observation.value(), values,
                    (text, pattern) -> text.contains(String.valueOf(pattern)));
            case GT, GTE, LT, LTE -> isEmpty ? Boolean.FALSE
                    : compareOrdered(observation.value(), values, condition.operator());
        };
    }

    private static Boolean compare(Object actual, List<Object> expected, java.util.function.Predicate<Object> hit) {
        if (expected.isEmpty()) {
            return null;
        }
        return hit.test(actual);
    }

    private static Boolean compareText(Object actual, List<Object> expected,
                                       java.util.function.BiFunction<String, Object, Boolean> matcher) {
        if (expected.isEmpty() || !(actual instanceof String text)) {
            return null;
        }
        for (Object pattern : expected) {
            if (matcher.apply(text, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static Boolean compareOrdered(Object actual, List<Object> expected, BusinessFieldFilter.Operator op) {
        if (expected.isEmpty() || !(actual instanceof Comparable<?> comparable)) {
            return null;
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        int cmp = ((Comparable) comparable).compareTo(expected.get(0));
        return switch (op) {
            case GT -> cmp > 0;
            case GTE -> cmp >= 0;
            case LT -> cmp < 0;
            case LTE -> cmp <= 0;
            default -> null;
        };
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
