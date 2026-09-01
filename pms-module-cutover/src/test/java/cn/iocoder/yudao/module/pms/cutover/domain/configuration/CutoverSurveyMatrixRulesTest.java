package cn.iocoder.yudao.module.pms.cutover.domain.configuration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CutoverSurveyMatrixRulesTest {

    @Test
    void acceptsCompleteSurveyMatrix() {
        var errors = CutoverSurveyMatrixRules.validate(CutoverMatrixFixtures.completeSurveyItems(),
                CutoverMatrixFixtures.surveyRules(), CutoverMatrixFixtures.context());

        assertTrue(errors.isEmpty());
    }

    @Test
    void rejectsMissingCoreCategoryAndInvalidBackgroundDependency() {
        var items = CutoverMatrixFixtures.completeSurveyItemsWithout("BUSINESS_SUMMARY");
        items.getFirst().interfaceSchema().put("visibleWhenField", "UNKNOWN_FIELD");

        var errors = CutoverSurveyMatrixRules.validate(items, CutoverMatrixFixtures.surveyRules(),
                CutoverMatrixFixtures.context());

        assertTrue(errors.stream().anyMatch(error -> error.message().contains("BUSINESS_SUMMARY")));
        assertTrue(errors.stream().anyMatch(error -> error.message().contains("条件字段不存在")));
    }

    @Test
    void rejectsBindingWithoutRequiredResult() {
        var errors = CutoverSurveyMatrixRules.validate(
                CutoverMatrixFixtures.completeSurveyItems(), CutoverMatrixFixtures.surveyRule(null),
                CutoverMatrixFixtures.context());

        assertTrue(errors.stream().anyMatch(error -> error.location().endsWith("requiredResult")));
    }

    @Test
    void rejectsEnabledSurveyItemWithoutBinding() {
        var rules = new ArrayList<>(CutoverMatrixFixtures.surveyRules());
        rules.removeIf(rule -> "SURVEY_BUSINESS_SUMMARY".equals(rule.stableItemKey()));

        var errors = CutoverSurveyMatrixRules.validate(
                CutoverMatrixFixtures.completeSurveyItems(), rules, CutoverMatrixFixtures.context());

        assertTrue(errors.stream().anyMatch(error -> error.location().contains("SURVEY_BUSINESS_SUMMARY")
                && error.message().contains("启用绑定")));
    }

    @Test
    void rejectsMissingBackgroundFieldAndWrongVisibleWhen() {
        var items = CutoverMatrixFixtures.completeSurveyItems();
        @SuppressWarnings("unchecked")
        var fields = (List<Map<String, Object>>) items.getFirst().interfaceSchema().get("fields");
        fields.removeIf(field -> "backgroundDescription".equals(field.get("code")));
        fields.stream().filter(field -> "issueTicketNo".equals(field.get("code")))
                .findFirst().orElseThrow()
                .put("visibleWhen", Map.of("field", "repeatCutover", "equals", true));

        var errors = CutoverSurveyMatrixRules.validate(items, CutoverMatrixFixtures.surveyRules(),
                CutoverMatrixFixtures.context());

        assertTrue(errors.stream().anyMatch(error -> error.message().contains("backgroundDescription")));
        assertTrue(errors.stream().anyMatch(error -> error.message().contains("issueTicketNo")
                && error.message().contains("solvesOnlineIssue")));
    }

    @Test
    void rejectsSameConditionAndPriorityWithDifferentRequiredResults() {
        var rules = new ArrayList<>(CutoverMatrixFixtures.surveyRule(true));
        rules.add(new CutoverConfigurationRules.BindingRule("RULE_SURVEY_BACKGROUND_OPTIONAL",
                "SURVEY_CUTOVER_BACKGROUND", "{\"CUTOVER_LEVEL\":[\"A\",\"B\",\"C\"]}",
                10, false, true));

        var errors = CutoverSurveyMatrixRules.validate(CutoverMatrixFixtures.completeSurveyItems(), rules,
                CutoverMatrixFixtures.context());

        assertTrue(errors.stream().anyMatch(error -> error.message().contains("必填结果冲突")));
    }

    @Test
    void specificCombinationMustHaveHigherPriorityThanWildcard() {
        var rules = List.of(
                new CutoverConfigurationRules.BindingRule("RULE_SURVEY_GENERAL", "SURVEY_BUSINESS_SUMMARY",
                        "{\"CUTOVER_LEVEL\":[\"A\",\"B\",\"C\"]}", 20, false, true),
                new CutoverConfigurationRules.BindingRule("RULE_SURVEY_ADX", "SURVEY_BUSINESS_SUMMARY",
                        "{\"CUTOVER_LEVEL\":[\"A\",\"B\",\"C\"],\"DEVICE_TYPE\":[\"ADX\"]}",
                        10, true, true));

        var errors = CutoverSurveyMatrixRules.validate(CutoverMatrixFixtures.completeSurveyItems(), rules,
                CutoverMatrixFixtures.context());

        assertTrue(errors.stream().anyMatch(error -> error.message().contains("具体组合优先级")));
    }

    @Test
    void rejectsDuplicateSurveyBindingEvenWhenPriorityDiffers() {
        var rules = new ArrayList<>(CutoverMatrixFixtures.surveyRules());
        var source = rules.getFirst();
        rules.add(new CutoverConfigurationRules.BindingRule("RULE_SURVEY_DUPLICATE",
                source.stableItemKey(), source.dimensionConditionSnapshot(), source.priority() + 1,
                source.requiredResult(), true));

        var errors = CutoverSurveyMatrixRules.validate(CutoverMatrixFixtures.completeSurveyItems(), rules,
                CutoverMatrixFixtures.context());

        assertTrue(errors.stream().anyMatch(error -> error.message().contains("维度组合重复")));
    }
}
