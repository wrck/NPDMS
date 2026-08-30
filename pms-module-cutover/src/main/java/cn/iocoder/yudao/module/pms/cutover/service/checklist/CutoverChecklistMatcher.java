package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.FROZEN_CONFIGURATION_INVALID;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.INVALID_REQUEST;

public final class CutoverChecklistMatcher {

    public MatchResult match(CutoverFrozenConfiguration configuration, MatchInput input) {
        if (configuration == null || input == null || input.dimensions() == null) {
            throw new CutoverChecklistException(INVALID_REQUEST, "清单匹配输入不完整");
        }
        Map<String, Set<String>> dimensions = normalizeDimensions(input.dimensions());
        Map<Long, CutoverFrozenConfiguration.ItemDefinition> itemsById = configuration.items().stream()
                .collect(java.util.stream.Collectors.toMap(CutoverFrozenConfiguration.ItemDefinition::id,
                        item -> item));
        Map<String, List<RuleMatch>> matchesByKey = new LinkedHashMap<>();
        configuration.rules().stream()
                .sorted(Comparator.comparing(CutoverFrozenConfiguration.BindingRule::priority,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CutoverFrozenConfiguration.BindingRule::id))
                .forEach(rule -> {
                    CutoverFrozenConfiguration.ItemDefinition item = itemsById.get(rule.itemDefinitionId());
                    if (item == null || !item.itemDefinitionVersion().equals(rule.itemDefinitionVersion())) {
                        throw new CutoverChecklistException(FROZEN_CONFIGURATION_INVALID,
                                "冻结配置规则引用的采集项定义不完整");
                    }
                    if (matches(rule.dimensionConditionSnapshot(), dimensions)) {
                        matchesByKey.computeIfAbsent(item.stableItemKey(), ignored -> new ArrayList<>())
                                .add(new RuleMatch(item, rule));
                    }
                });
        List<MatchedItem> ready = new ArrayList<>();
        List<Conflict> conflicts = new ArrayList<>();
        matchesByKey.forEach((stableItemKey, matches) -> {
            Map<String, List<RuleMatch>> byDefinition = matches.stream().collect(java.util.stream.Collectors.groupingBy(
                    match -> match.item().id() + ":" + match.item().itemDefinitionVersion(),
                    LinkedHashMap::new, java.util.stream.Collectors.toList()));
            if (byDefinition.size() > 1) {
                conflicts.add(new Conflict(stableItemKey, byDefinition.values().stream()
                        .map(values -> candidate(values.getFirst().item(), values)).toList()));
                return;
            }
            List<RuleMatch> compatible = byDefinition.values().iterator().next();
            CutoverFrozenConfiguration.ItemDefinition item = compatible.getFirst().item();
            ready.add(new MatchedItem(item, item.required()
                    || compatible.stream().anyMatch(value -> value.rule().requiredResult()),
                    compatible.stream().map(value -> value.rule().id()).sorted().toList()));
        });
        ready.sort(Comparator.comparing((MatchedItem value) -> value.item().sortOrder(),
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(value -> value.item().stableItemKey()));
        conflicts.sort(Comparator.comparing(Conflict::stableItemKey));
        return new MatchResult(List.copyOf(ready), List.copyOf(conflicts), ready.isEmpty() && conflicts.isEmpty());
    }

    private Candidate candidate(CutoverFrozenConfiguration.ItemDefinition item, List<RuleMatch> matches) {
        return new Candidate(item.id(), item.itemDefinitionVersion(),
                matches.stream().map(value -> value.rule().id()).sorted().toList());
    }

    private boolean matches(String snapshot, Map<String, Set<String>> dimensions) {
        if (snapshot == null || snapshot.isBlank()) {
            throw new CutoverChecklistException(FROZEN_CONFIGURATION_INVALID, "冻结配置规则条件为空");
        }
        Object decoded;
        try {
            decoded = JSONUtil.parse(snapshot);
        } catch (RuntimeException exception) {
            throw new CutoverChecklistException(FROZEN_CONFIGURATION_INVALID, "冻结配置规则条件不是合法JSON");
        }
        if (!(decoded instanceof Map<?, ?> conditions) || conditions.isEmpty()) {
            throw new CutoverChecklistException(FROZEN_CONFIGURATION_INVALID, "冻结配置规则条件必须是非空对象");
        }
        for (Map.Entry<?, ?> entry : conditions.entrySet()) {
            String dimension = comparisonKey(String.valueOf(entry.getKey()));
            Set<String> expected = normalizeValues(entry.getValue());
            Set<String> actual = dimensions.getOrDefault(dimension, Set.of());
            if (expected.isEmpty() || actual.isEmpty() || expected.stream().noneMatch(actual::contains)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Set<String>> normalizeDimensions(Map<String, ? extends Collection<String>> values) {
        Map<String, Set<String>> normalized = new LinkedHashMap<>();
        values.forEach((key, current) -> {
            String dimension = comparisonKey(key);
            if (dimension.isEmpty() || normalized.putIfAbsent(dimension, normalizeValues(current)) != null) {
                throw new CutoverChecklistException(INVALID_REQUEST, "清单匹配维度非法或重复");
            }
        });
        return normalized;
    }

    private Set<String> normalizeValues(Object raw) {
        if (!(raw instanceof Collection<?> values)) {
            throw new CutoverChecklistException(FROZEN_CONFIGURATION_INVALID, "冻结配置规则维度值必须是数组");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (Object value : values) {
            String current = comparisonKey(value == null ? null : String.valueOf(value));
            if (current.isEmpty()) {
                throw new CutoverChecklistException(FROZEN_CONFIGURATION_INVALID, "冻结配置规则维度值不能为空");
            }
            normalized.add(current);
        }
        return Set.copyOf(normalized);
    }

    private String comparisonKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record MatchInput(Map<String, ? extends Collection<String>> dimensions) {
    }

    public record MatchResult(List<MatchedItem> readyItems, List<Conflict> conflicts, boolean gap) {
    }

    public record MatchedItem(CutoverFrozenConfiguration.ItemDefinition item,
                              boolean required,
                              List<Long> matchedRuleIds) {
    }

    public record Conflict(String stableItemKey, List<Candidate> candidates) {
    }

    public record Candidate(Long itemDefinitionId, Integer itemDefinitionVersion, List<Long> matchedRuleIds) {
    }

    private record RuleMatch(CutoverFrozenConfiguration.ItemDefinition item,
                             CutoverFrozenConfiguration.BindingRule rule) {
    }
}
