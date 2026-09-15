package cn.iocoder.yudao.module.pms.project.domain.rule;

import tools.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Version-owned child closure policy; aggregation supplies one fact to the native LiteFlow chain. */
public record ChildProjectWaitCondition(Scope scope, Set<String> acceptedClosureTypes,
                                        Quantifier quantifier, boolean emptyResult) {
    public enum Scope { DIRECT, DESCENDANTS }
    public enum Quantifier { ALL, ANY }

    public static ChildProjectWaitCondition parse(JsonNode parameters) {
        if (!parameters.isObject() || !Set.copyOf(parameters.propertyNames()).equals(
                Set.of("scope", "acceptedClosureTypes", "quantifier", "emptyResult")))
            throw new IllegalArgumentException("子项目等待条件需要显式配置范围、关闭类型、满足方式和无子项目结果");
        var scope = Scope.valueOf(parameters.path("scope").asText());
        var quantifier = Quantifier.valueOf(parameters.path("quantifier").asText());
        var types = parameters.path("acceptedClosureTypes");
        if (!types.isArray() || types.isEmpty() || !parameters.path("emptyResult").isBoolean())
            throw new IllegalArgumentException("请选择认可的关闭类型和无子项目时的结果");
        Set<String> accepted = new HashSet<>();
        for (var type : types) {
            if (!type.isTextual() || !Set.of("NORMAL_CLOSED", "EXCEPTION_CLOSED").contains(type.asText())
                    || !accepted.add(type.asText())) throw new IllegalArgumentException("认可关闭类型无效或重复");
        }
        return new ChildProjectWaitCondition(scope, Set.copyOf(accepted), quantifier,
                parameters.path("emptyResult").booleanValue());
    }

    public RuleFact evaluate(List<String> lifecycleStatuses) {
        if (lifecycleStatuses == null || lifecycleStatuses.stream().anyMatch(status -> status == null
                || !Set.of("ACTIVE", "NORMAL_CLOSED", "EXCEPTION_CLOSED").contains(status)))
            return RuleFact.unknown("CHILD_CLOSURE_FACT_UNAVAILABLE");
        if (lifecycleStatuses.isEmpty()) return RuleFact.known(emptyResult);
        return RuleFact.known(quantifier == Quantifier.ALL
                ? lifecycleStatuses.stream().allMatch(acceptedClosureTypes::contains)
                : lifecycleStatuses.stream().anyMatch(acceptedClosureTypes::contains));
    }
}
