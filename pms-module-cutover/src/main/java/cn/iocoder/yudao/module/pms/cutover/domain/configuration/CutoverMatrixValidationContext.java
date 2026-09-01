package cn.iocoder.yudao.module.pms.cutover.domain.configuration;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CUT-09/CUT-10 发布校验使用的启用字典值快照。
 */
public record CutoverMatrixValidationContext(Set<String> cutoverTypeCodes,
                                             Set<String> networkModeCodes,
                                             Set<String> deviceTypeCodes,
                                             Set<String> levelCodes) {

    public CutoverMatrixValidationContext {
        cutoverTypeCodes = immutable(cutoverTypeCodes);
        networkModeCodes = immutable(networkModeCodes);
        deviceTypeCodes = immutable(deviceTypeCodes);
        levelCodes = immutable(levelCodes);
    }

    private static Set<String> immutable(Set<String> values) {
        return values == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}

final class CutoverMatrixRuleSupport {

    private static final Pattern CONDITION = Pattern.compile(
            "\\\"([^\\\"]+)\\\"\\s*:\\s*(\\\"([^\\\"]*)\\\"|\\[([^]]*)])");
    private static final Pattern ARRAY_VALUE = Pattern.compile("\\\"([^\\\"]*)\\\"");

    private CutoverMatrixRuleSupport() {
    }

    static Map<String, Set<String>> conditions(CutoverConfigurationRules.BindingRule rule) {
        if (rule == null || rule.dimensionConditionSnapshot() == null) {
            return Map.of();
        }
        Map<String, Set<String>> result = new LinkedHashMap<>();
        Matcher matcher = CONDITION.matcher(rule.dimensionConditionSnapshot());
        while (matcher.find()) {
            Set<String> values = new LinkedHashSet<>();
            if (matcher.group(3) != null && !matcher.group(3).isBlank()) {
                values.add(matcher.group(3));
            } else if (matcher.group(4) != null) {
                Matcher arrayMatcher = ARRAY_VALUE.matcher(matcher.group(4));
                while (arrayMatcher.find()) {
                    if (!arrayMatcher.group(1).isBlank()) {
                        values.add(arrayMatcher.group(1));
                    }
                }
            }
            result.put(matcher.group(1), Set.copyOf(values));
        }
        return Map.copyOf(result);
    }

    static boolean isMoreSpecific(Map<String, Set<String>> candidate,
                                  Map<String, Set<String>> broader) {
        if (candidate.isEmpty() || broader.isEmpty()) {
            return false;
        }
        boolean strictlyNarrower = candidate.size() > broader.size();
        for (Map.Entry<String, Set<String>> entry : broader.entrySet()) {
            Set<String> candidateValues = candidate.get(entry.getKey());
            if (candidateValues == null || candidateValues.isEmpty()
                    || !entry.getValue().containsAll(candidateValues)) {
                return false;
            }
            strictlyNarrower |= candidateValues.size() < entry.getValue().size();
        }
        return strictlyNarrower;
    }

    static void validateUniqueCombinations(List<CutoverConfigurationRules.BindingRule> rules,
                                           Set<String> itemKeys,
                                           List<CutoverConfigurationRules.ValidationError> errors,
                                           String matrixLabel) {
        Set<BindingCombination> combinations = new HashSet<>();
        for (int index = 0; index < rules.size(); index++) {
            CutoverConfigurationRules.BindingRule rule = rules.get(index);
            if (!rule.enabled() || !itemKeys.contains(rule.stableItemKey())) {
                continue;
            }
            BindingCombination combination = new BindingCombination(rule.stableItemKey(), conditions(rule));
            if (!combinations.add(combination)) {
                errors.add(new CutoverConfigurationRules.ValidationError(
                        "bindingRules[" + index + "].dimensionConditionSnapshot",
                        "同一" + matrixLabel + "项的维度组合重复"));
            }
        }
    }

    private record BindingCombination(String stableItemKey, Map<String, Set<String>> conditions) {
    }
}
