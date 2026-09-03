package cn.iocoder.yudao.module.pms.cutover.domain.configuration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutoverRiskMatrixRulesTest {

    @Test
    void acceptsCompleteRiskMatrix() {
        var errors = CutoverRiskMatrixRules.validate(CutoverMatrixFixtures.completeRiskItems(),
                CutoverMatrixFixtures.completeRiskRules(), CutoverMatrixFixtures.context());

        assertTrue(errors.isEmpty());
    }

    @Test
    void rejectsWrongDualMachineCountsAndCrossModeBinding() {
        var errors = CutoverRiskMatrixRules.validate(
                CutoverMatrixFixtures.riskItems(Map.of("VSM", 16, "SILENT_DUAL", 25,
                        "DRP_DUAL", 23, "NORMAL_DUAL", 24, "CLUSTER", 8)),
                List.of(CutoverMatrixFixtures.rule("DUAL_VSM_001", "NORMAL_DUAL", true)),
                CutoverMatrixFixtures.context());

        assertTrue(errors.stream().anyMatch(error -> error.message().contains("VSM双机应为17项")));
        assertTrue(errors.stream().anyMatch(error -> error.message().contains("不得跨所属组网模式")));
    }

    @Test
    void rejectsTooManyDualMachineItemsAndMissingRiskCategory() {
        var items = CutoverMatrixFixtures.riskItems(Map.of("VSM", 18, "SILENT_DUAL", 25,
                "DRP_DUAL", 23, "NORMAL_DUAL", 24, "CLUSTER", 8));
        items.removeIf(item -> "SYSTEM_LOG".equals(item.businessCategoryCode()));

        var errors = CutoverRiskMatrixRules.validate(items, List.of(), CutoverMatrixFixtures.context());

        assertTrue(errors.stream().anyMatch(error -> error.message().contains("VSM双机应为17项")));
        assertTrue(errors.stream().anyMatch(error -> error.message().contains("SYSTEM_LOG")));
    }

    @Test
    void rejectsMissingAllSituationCoverage() {
        var errors = CutoverRiskMatrixRules.validate(
                CutoverMatrixFixtures.completeRiskItems(),
                CutoverMatrixFixtures.rulesMissingDevice("SYSTEM_LOG", "ADX"),
                CutoverMatrixFixtures.context());

        assertTrue(errors.stream().anyMatch(error -> error.location().contains("coverage")
                && error.message().contains("ADX")));
    }

    @Test
    void emptyConditionsDoNotCoverAllSituations() {
        var rules = new ArrayList<>(CutoverMatrixFixtures.completeRiskRules());
        rules.removeIf(rule -> "RISK_SYSTEM_LOG".equals(rule.stableItemKey()));
        rules.add(new CutoverConfigurationRules.BindingRule("RULE_SYSTEM_LOG_EMPTY", "RISK_SYSTEM_LOG",
                "{}", 10, true, true));

        var errors = CutoverRiskMatrixRules.validate(CutoverMatrixFixtures.completeRiskItems(), rules,
                CutoverMatrixFixtures.context());

        assertFalse(errors.stream().noneMatch(error -> error.location().contains("coverage")));
    }

    @Test
    void optionalRuleDoesNotCoverAllSituationRequiredItem() {
        var rules = new ArrayList<>(CutoverMatrixFixtures.completeRiskRules());
        rules.removeIf(rule -> "RISK_SYSTEM_LOG".equals(rule.stableItemKey()));
        rules.add(new CutoverConfigurationRules.BindingRule("RULE_SYSTEM_LOG_OPTIONAL", "RISK_SYSTEM_LOG",
                "{\"CUTOVER_TYPE\":[\"CONFIGURATION_CHANGE\",\"VERSION_UPGRADE\"],"
                        + "\"DEVICE_TYPE\":[\"ADX\",\"FW\",\"SW\"],"
                        + "\"CUTOVER_LEVEL\":[\"A\",\"B\",\"C\"]}",
                10, false, true));

        var errors = CutoverRiskMatrixRules.validate(CutoverMatrixFixtures.completeRiskItems(), rules,
                CutoverMatrixFixtures.context());

        assertTrue(errors.stream().anyMatch(error -> error.location().contains("coverage.SYSTEM_LOG")));
    }

    @Test
    void rejectsEnabledRiskItemWithoutBinding() {
        var rules = new ArrayList<>(CutoverMatrixFixtures.completeRiskRules());
        rules.removeIf(rule -> "RISK_F5_DEFAULT".equals(rule.stableItemKey()));

        var errors = CutoverRiskMatrixRules.validate(CutoverMatrixFixtures.completeRiskItems(), rules,
                CutoverMatrixFixtures.context());

        assertTrue(errors.stream().anyMatch(error -> error.location().contains("RISK_F5_DEFAULT")
                && error.message().contains("启用绑定")));
    }

    @Test
    void targetVersionBulletinOnlyAppliesToVersionUpgrade() {
        var rules = new ArrayList<>(CutoverMatrixFixtures.completeRiskRules());
        rules.removeIf(rule -> "RISK_TARGET_VERSION_BULLETIN".equals(rule.stableItemKey()));
        rules.add(new CutoverConfigurationRules.BindingRule("RULE_TARGET_INVALID",
                "RISK_TARGET_VERSION_BULLETIN",
                "{\"CUTOVER_TYPE\":[\"VERSION_UPGRADE\",\"CONFIGURATION_CHANGE\"]}",
                10, true, true));

        var errors = CutoverRiskMatrixRules.validate(CutoverMatrixFixtures.completeRiskItems(), rules,
                CutoverMatrixFixtures.context());

        assertTrue(errors.stream().anyMatch(error -> error.message().contains("仅适用于版本升级")));
    }

    @Test
    void rejectsDualItemWithoutSubtableInsteadOfFailingValidation() {
        var items = CutoverMatrixFixtures.completeRiskItems();
        var source = items.stream()
                .filter(item -> "DUAL_MACHINE_CHECK".equals(item.itemType()))
                .findFirst().orElseThrow();
        items.set(items.indexOf(source), new CutoverConfigurationRules.ItemDefinition(
                source.stableItemKey(), source.itemType(), source.businessCategoryCode(), source.itemName(),
                source.interfaceFormat(), source.interfaceSchema(), source.feedbackFormat(), source.required(),
                source.workMode(), source.externalSourceConfig(), null, source.enabled()));

        var errors = CutoverRiskMatrixRules.validate(items, List.of(), CutoverMatrixFixtures.context());

        assertTrue(errors.stream().anyMatch(error -> error.message().contains("所属组网模式")));
    }
}
