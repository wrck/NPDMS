package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutoverChecklistMatcherTest {

    private final CutoverChecklistMatcher matcher = new CutoverChecklistMatcher();

    @Test
    void matchesFrozenRulesForPositiveGradeAFlow() {
        CutoverFrozenConfiguration configuration = configuration(
                List.of(item(11L, "SURVEY_BACKGROUND", 10), item(12L, "DUAL_VSM_001", 20)),
                List.of(rule(21L, 11L, "{\"CUTOVER_LEVEL\":[\"A\",\"B\",\"C\"]}", true),
                        rule(22L, 12L, "{\"NETWORK_MODE\":[\"VSM\"]}", false)));

        CutoverChecklistMatcher.MatchResult result = matcher.match(configuration,
                new CutoverChecklistMatcher.MatchInput(Map.of(
                        "CUTOVER_LEVEL", Set.of(" a "),
                        "NETWORK_MODE", Set.of("vsm"))));

        assertFalse(result.gap());
        assertTrue(result.conflicts().isEmpty());
        assertEquals(List.of("SURVEY_BACKGROUND", "DUAL_VSM_001"), result.readyItems().stream()
                .map(value -> value.item().stableItemKey()).toList());
        assertTrue(result.readyItems().getFirst().required());
    }

    @Test
    void reportsGapWhenNoFrozenRuleApplies() {
        CutoverFrozenConfiguration configuration = configuration(List.of(item(11L, "SURVEY_BACKGROUND", 10)),
                List.of(rule(21L, 11L, "{\"CUTOVER_LEVEL\":[\"A\"]}", true)));

        CutoverChecklistMatcher.MatchResult result = matcher.match(configuration,
                new CutoverChecklistMatcher.MatchInput(Map.of("CUTOVER_LEVEL", Set.of("D"))));

        assertTrue(result.gap());
        assertTrue(result.readyItems().isEmpty());
        assertTrue(result.conflicts().isEmpty());
    }

    private CutoverFrozenConfiguration configuration(List<CutoverFrozenConfiguration.ItemDefinition> items,
                                                      List<CutoverFrozenConfiguration.BindingRule> rules) {
        return new CutoverFrozenConfiguration(1L, "CUTOVER_MAIN", 2, "DISABLED", "{}", "{}", items, rules);
    }

    private CutoverFrozenConfiguration.ItemDefinition item(Long id, String key, int sortOrder) {
        return new CutoverFrozenConfiguration.ItemDefinition(id, key, 1, "BUSINESS_SURVEY", key, null,
                "FORM", "{}", "MANUAL", false, sortOrder);
    }

    private CutoverFrozenConfiguration.BindingRule rule(Long id, Long itemId, String condition, boolean required) {
        return new CutoverFrozenConfiguration.BindingRule(id, "RULE_" + id, itemId, 1, condition, 10, required);
    }
}
