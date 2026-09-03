package cn.iocoder.yudao.module.pms.service.domain.inspectionrule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionRuleRegexValidatorTest {

    private final InspectionRuleRegexValidator validator = new InspectionRuleRegexValidator();

    @Test
    void shouldAcceptRestrictedJdkPatternsWithoutExecutingMatch() {
        for (String expression : List.of(
                "^(OK|WARN)-[0-9]+$",
                "(?ims)^(?:ok){1,1000}$",
                "^(ab)+$",
                "a+ +",
                "a{1,2} +")) {
            assertTrue(validator.validate(expression).isEmpty(), expression);
        }
    }

    @Test
    void shouldAcceptApprovedRegexBudgetBoundaries() {
        for (String expression : List.of(
                "a".repeat(1024),
                "(a)".repeat(32),
                "(".repeat(8) + "a" + ")".repeat(8),
                "a|".repeat(31) + "a",
                "a?".repeat(64),
                "a{1,1000}")) {
            assertTrue(validator.validate(expression).isEmpty(), expression);
        }
    }

    @Test
    void shouldRejectMissingTooLongAndInvalidJdkPattern() {
        assertEquals("REQUIRED", code(" "));
        assertEquals("REGEX_TOO_LONG", code("a".repeat(1025)));
        assertEquals("INVALID_SYNTAX", code("([a-z]"));
    }

    @Test
    void shouldRejectUnsupportedRegexStructures() {
        for (String expression : List.of(
                "^(a)\\1$",
                "^(?<value>a)\\k<value>$",
                "a(?=b)",
                "a(?!b)",
                "(?<=a)b",
                "(?<!a)b",
                "(?<value>a)",
                "(?>a)",
                "a(?i:b)",
                "(?i)(?m)a",
                "(a+)+",
                "((a+))+",
                "(?:((a+)))+",
                "(a|b)+",
                "((a|b))+",
                "(?:((a|b)))+",
                "a{1,}",
                "a{1,1001}",
                "(*SKIP)")) {
            assertEquals("REGEX_UNSUPPORTED_FEATURE", code(expression), expression);
        }
    }

    @Test
    void shouldRejectRegexComplexityAboveApprovedBudgets() {
        assertEquals("REGEX_COMPLEXITY_EXCEEDED", code("(a)".repeat(33)));
        assertEquals(
                "REGEX_COMPLEXITY_EXCEEDED",
                code("(".repeat(9) + "a" + ")".repeat(9)));
        assertEquals("REGEX_COMPLEXITY_EXCEEDED", code("a|".repeat(32) + "a"));
        assertEquals("REGEX_COMPLEXITY_EXCEEDED", code("a?".repeat(65)));
    }

    private String code(String expression) {
        return validator.validate(expression).getFirst().code();
    }
}
