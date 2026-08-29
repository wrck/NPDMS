package cn.iocoder.yudao.module.pms.cutover.domain.configuration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutoverConfigurationRulesTest {

    @Test
    void onlyDraftIsEditable() {
        assertTrue(CutoverConfigurationRules.isEditable("DRAFT"));
        assertFalse(CutoverConfigurationRules.isEditable("PUBLISHED"));
        assertFalse(CutoverConfigurationRules.isEditable("DISABLED"));
    }

    @Test
    void validConfigurationHasNoErrors() {
        assertTrue(CutoverConfigurationRules.validate(dimensions(), items(), rules(), sections()).isEmpty());
    }

    @Test
    void rejectsInvalidSubtableAndConflictingRule() {
        var invalidItems = List.of(
                new CutoverConfigurationRules.ItemDefinition("SURVEY-1", "BUSINESS_SURVEY", "割接背景",
                        "INPUT", "TEXT", true, "MANUAL", null, "VSM", true),
                new CutoverConfigurationRules.ItemDefinition("DUAL-1", "DUAL_MACHINE_CHECK", "双机检查",
                        "TABLE", "BOOLEAN_REMARK", true, "MANUAL", null, null, true));
        var duplicateRules = List.of(
                new CutoverConfigurationRules.BindingRule("RULE-1", "SURVEY-1", "{\"CUTOVER_LEVEL\":\"A\"}", 10, true),
                new CutoverConfigurationRules.BindingRule("RULE-2", "SURVEY-1", "{\"CUTOVER_LEVEL\":\"A\"}", 10, true));

        var errors = CutoverConfigurationRules.validate(dimensions(), invalidItems, duplicateRules, sections());

        assertEquals(3, errors.size());
        assertTrue(errors.stream().anyMatch(error -> error.location().endsWith("subtableCode")));
        assertTrue(errors.stream().anyMatch(error -> error.message().contains("冲突规则")));
    }

    @Test
    void rejectsMissingBaseDimensionAndExternalSourceDefinition() {
        var externalItem = new CutoverConfigurationRules.ItemDefinition("RISK-1", "RISK", "公告检查",
                "TABLE", "TABLE", true, "EXTERNAL", null, null, true);

        var errors = CutoverConfigurationRules.validate(dimensions().subList(0, 3),
                List.of(externalItem), List.of(), sections());

        assertTrue(errors.stream().anyMatch(error -> error.message().contains("CUTOVER_LEVEL")));
        assertTrue(errors.stream().anyMatch(error -> error.location().endsWith("externalSourceConfig")));
    }

    @Test
    void rejectsDisabledBaseDimensionAndEnabledRuleReferencingDisabledItem() {
        var disabledDimension = new CutoverConfigurationRules.DimensionDefinition(
                "CUTOVER_LEVEL", "割接等级", "STRING", "DICT:pms_risk_level",
                "CUT", "assessment.level", false);
        var dimensions = new java.util.ArrayList<>(dimensions());
        dimensions.set(3, disabledDimension);
        var disabledItem = new CutoverConfigurationRules.ItemDefinition(
                "SURVEY-1", "BUSINESS_SURVEY", "割接背景", "INPUT", "TEXT",
                true, "MANUAL", null, null, false);

        var errors = CutoverConfigurationRules.validate(dimensions, List.of(disabledItem), rules(), sections());

        assertTrue(errors.stream().anyMatch(error -> error.message().contains("未启用V1必需维度")));
        assertTrue(errors.stream().anyMatch(error -> error.location().endsWith("stableItemKey")
                && error.message().contains("不存在")));
    }

    private List<CutoverConfigurationRules.DimensionDefinition> dimensions() {
        return List.of(
                dimension("CUTOVER_TYPE", "割接类型", "STRING", "DICT:pms_cutover_type", "CUT", "task.cutoverType"),
                dimension("NETWORK_MODE", "组网模式", "STRING", "DICT:pms_cutover_network_mode", "CUT", "task.networkMode"),
                dimension("DEVICE_TYPE", "设备类型", "STRING", "DICT:pms_device_type", "SYSTEM", "task.deviceType"),
                dimension("CUTOVER_LEVEL", "割接等级", "STRING", "DICT:pms_cutover_level", "CUT", "assessment.level"));
    }

    private CutoverConfigurationRules.DimensionDefinition dimension(String code, String name, String type,
                                                                     String source, String owner, String path) {
        return new CutoverConfigurationRules.DimensionDefinition(code, name, type, source, owner, path, true);
    }

    private List<CutoverConfigurationRules.ItemDefinition> items() {
        return List.of(
                new CutoverConfigurationRules.ItemDefinition("SURVEY-1", "BUSINESS_SURVEY", "割接背景",
                        "INPUT", "TEXT", true, "MANUAL", null, null, true),
                new CutoverConfigurationRules.ItemDefinition("DUAL-1", "DUAL_MACHINE_CHECK", "双机检查",
                        "TABLE", "BOOLEAN_REMARK", true, "MANUAL", null, "VSM", true));
    }

    private List<CutoverConfigurationRules.BindingRule> rules() {
        return List.of(new CutoverConfigurationRules.BindingRule("RULE-1", "SURVEY-1",
                "{\"CUTOVER_LEVEL\":\"A\"}", 10, true));
    }

    private List<CutoverConfigurationRules.PlanTemplateSection> sections() {
        return List.of(new CutoverConfigurationRules.PlanTemplateSection("OVERVIEW", "割接概述", 10,
                List.of("VERSION_UPGRADE"), List.of("A", "B"), true));
    }
}
