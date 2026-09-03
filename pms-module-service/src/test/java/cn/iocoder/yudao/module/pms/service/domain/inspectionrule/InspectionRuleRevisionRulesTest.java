package cn.iocoder.yudao.module.pms.service.domain.inspectionrule;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionRuleRevisionRulesTest {

    private static final List<String> CATEGORY_CODES = List.of(
            "BASIC", "OPERATING_STATUS", "LOG", "BUSINESS_STATUS", "REDUNDANCY",
            "ROUTING", "SECURITY", "FORWARDING_CHANNEL", "LOAD_BALANCING", "TRAFFIC_CLEANING");
    private static final List<String> SEVERITY_CODES = List.of("GENERAL", "SEVERE", "FATAL");

    private final InspectionRuleRevisionRules rules = new InspectionRuleRevisionRules();

    @Test
    void shouldAcceptCompleteDraftWithContinuousCommandsAndThreshold() {
        List<InspectionRuleRevisionRules.ValidationError> errors = rules.validate(validRevision());

        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldAcceptApprovedCategoryAndSeverityCodes() {
        for (String categoryCode : CATEGORY_CODES) {
            assertTrue(rules.validate(validRevision(categoryCode, "GENERAL")).isEmpty());
        }
        for (String severityCode : SEVERITY_CODES) {
            assertTrue(rules.validate(validRevision("BASIC", severityCode)).isEmpty());
        }
    }

    @Test
    void shouldRejectUnsupportedCategoryAndSeverityCodes() {
        assertEquals(
                List.of(
                        "categoryCode:UNSUPPORTED_VALUE",
                        "severityCode:UNSUPPORTED_VALUE"),
                locationsAndCodes(rules.validate(validRevision("基础检测", "SEVERITY"))));
    }

    @Test
    void shouldAggregateSecretDetectionByStableFieldLocation() {
        InspectionRuleRevisionRules.RevisionDefinition revision = validRevision(
                "BASIC",
                "GENERAL",
                "Authorization: Bearer secret-value");

        assertEquals(
                List.of("commands[0].content:SECRET_DETECTED"),
                locationsAndCodes(rules.validate(revision)));
    }

    @Test
    void shouldRejectNonDraftAndMissingRequiredFields() {
        InspectionRuleRevisionRules.RevisionDefinition revision =
                new InspectionRuleRevisionRules.RevisionDefinition(
                        "PUBLISHED", " ", " ", " ", " ", " ", " ", null,
                        List.of(), " ", null, List.of());

        List<InspectionRuleRevisionRules.ValidationError> errors = rules.validate(revision);

        assertEquals(
                List.of(
                        "status:NOT_EDITABLE",
                        "detectionId:REQUIRED",
                        "ruleName:REQUIRED",
                        "inspectionItem:REQUIRED",
                        "description:REQUIRED",
                        "categoryCode:REQUIRED",
                        "severityCode:REQUIRED",
                        "sortOrder:REQUIRED",
                        "commands:REQUIRED",
                        "expectedResultRegex:REQUIRED",
                        "threshold:REQUIRED",
                        "productTypes:REQUIRED"),
                locationsAndCodes(errors));
    }

    @Test
    void shouldRejectNullCommandByStableFieldLocation() {
        InspectionRuleRevisionRules.RevisionDefinition revision =
                new InspectionRuleRevisionRules.RevisionDefinition(
                        "DRAFT", "DET-1", "Rule", "Item", "Description", "BASIC", "GENERAL", 1,
                        java.util.Arrays.asList((InspectionRuleRevisionRules.CommandDefinition) null),
                        "OK",
                        new InspectionRuleRevisionRules.ThresholdDefinition(
                                "NUMBER", "≤", new BigDecimal("80"), "%"),
                        List.of("TYPE-A"));

        assertEquals(
                List.of("commands[0]:REQUIRED"),
                locationsAndCodes(rules.validate(revision)));
    }

    @Test
    void shouldRejectInvalidCommandAndThresholdStructure() {
        InspectionRuleRevisionRules.RevisionDefinition revision =
                new InspectionRuleRevisionRules.RevisionDefinition(
                        "DRAFT", "DET-1", "Rule", "Item", "Description", "BASIC", "GENERAL", 1,
                        List.of(
                                new InspectionRuleRevisionRules.CommandDefinition("CMD-1", "", 1, 0, true),
                                new InspectionRuleRevisionRules.CommandDefinition("CMD-1", "show", 3, 31, false)),
                        "OK",
                        new InspectionRuleRevisionRules.ThresholdDefinition("", "<>", null, ""),
                        List.of("TYPE-A", " "));

        List<InspectionRuleRevisionRules.ValidationError> errors = rules.validate(revision);

        assertEquals(
                List.of(
                        "commands[0].content:REQUIRED",
                        "commands[0].timeoutSeconds:OUT_OF_RANGE",
                        "commands[1].stableCommandKey:DUPLICATE",
                        "commands[1].executionOrder:NOT_CONTINUOUS",
                        "commands[1].timeoutSeconds:OUT_OF_RANGE",
                        "threshold.dataType:REQUIRED",
                        "threshold.operator:INVALID",
                        "threshold.value:REQUIRED",
                        "threshold.unit:REQUIRED",
                        "productTypes[1]:REQUIRED"),
                locationsAndCodes(errors));
    }

    @Test
    void shouldRejectThresholdDataTypeOtherThanNumber() {
        InspectionRuleRevisionRules.RevisionDefinition revision =
                new InspectionRuleRevisionRules.RevisionDefinition(
                        "DRAFT", "DET-1", "Rule", "Item", "Description", "BASIC", "GENERAL", 1,
                        List.of(new InspectionRuleRevisionRules.CommandDefinition("CMD-1", "show", 1, 30, false)),
                        "OK",
                        new InspectionRuleRevisionRules.ThresholdDefinition(
                                "STRING", "=", new BigDecimal("1"), "value"),
                        List.of("TYPE-A"));

        assertEquals(
                List.of("threshold.dataType:UNSUPPORTED_VALUE"),
                locationsAndCodes(rules.validate(revision)));
    }

    @Test
    void shouldRejectMissingStableFieldsThresholdAndInvalidRegex() {
        InspectionRuleRevisionRules.RevisionDefinition revision =
                new InspectionRuleRevisionRules.RevisionDefinition(
                        "DRAFT", "DET-1", "Rule", " ", "Description", "BASIC", "GENERAL", null,
                        List.of(new InspectionRuleRevisionRules.CommandDefinition("CMD-1", "show", 1, 30, false)),
                        "([a-z]", null, List.of("TYPE-A"));

        assertEquals(
                List.of(
                        "inspectionItem:REQUIRED",
                        "sortOrder:REQUIRED",
                        "expectedResultRegex:INVALID_SYNTAX",
                        "threshold:REQUIRED"),
                locationsAndCodes(rules.validate(revision)));
    }

    @Test
    void shouldLocateOutOfOrderCommandsAndRejectDuplicateProductTypes() {
        InspectionRuleRevisionRules.RevisionDefinition revision =
                new InspectionRuleRevisionRules.RevisionDefinition(
                        "DRAFT", "DET-1", "Rule", "Item", "Description", "BASIC", "GENERAL", 1,
                        List.of(
                                new InspectionRuleRevisionRules.CommandDefinition("CMD-1", "show one", 3, 30, false),
                                new InspectionRuleRevisionRules.CommandDefinition("CMD-2", "show two", 1, 30, false)),
                        "OK",
                        new InspectionRuleRevisionRules.ThresholdDefinition(
                                "NUMBER", "≤", new BigDecimal("80"), "%"),
                        List.of("TYPE-A", "TYPE-A"));

        assertEquals(
                List.of(
                        "commands[0].executionOrder:NOT_CONTINUOUS",
                        "productTypes[1]:DUPLICATE"),
                locationsAndCodes(rules.validate(revision)));
    }

    private static InspectionRuleRevisionRules.RevisionDefinition validRevision() {
        return validRevision("BASIC", "GENERAL");
    }

    private static InspectionRuleRevisionRules.RevisionDefinition validRevision(
            String categoryCode,
            String severityCode) {
        return validRevision(categoryCode, severityCode, "show status");
    }

    private static InspectionRuleRevisionRules.RevisionDefinition validRevision(
            String categoryCode,
            String severityCode,
            String firstCommand) {
        return new InspectionRuleRevisionRules.RevisionDefinition(
                "DRAFT", "DET-1", "Rule", "Item", "Description", categoryCode, severityCode, 1,
                List.of(
                        new InspectionRuleRevisionRules.CommandDefinition("CMD-1", firstCommand, 1, 1, true),
                        new InspectionRuleRevisionRules.CommandDefinition("CMD-2", "show alarm", 2, 30, false)),
                "^(OK|WARN)$",
                new InspectionRuleRevisionRules.ThresholdDefinition(
                        "NUMBER", "≤", new BigDecimal("80"), "%"),
                List.of("TYPE-A"));
    }

    private static List<String> locationsAndCodes(
            List<InspectionRuleRevisionRules.ValidationError> errors) {
        return errors.stream()
                .map(error -> error.location() + ":" + error.code())
                .toList();
    }
}
