package cn.iocoder.yudao.module.pms.project.domain.satisfaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisfactionQuestionnaireDefinitionTest {

    @Test
    void scoresSumWithFinalRounding() {
        var definition = SatisfactionQuestionnaireDefinition.parse(config(
                single("Q1", true, "3.25", "0.00") + "," + single("Q2", false, "2.25", "0.00"),
                "SUM_V1", "5.50", "4.00", 1, "HALF_UP"));

        var result = definition.evaluate(answers(answer("Q1", "YES") + "," + answer("Q2", "YES")), true);

        assertEquals(new BigDecimal("5.5"), result.score());
        assertTrue(result.passed());
    }

    @Test
    void scoresWeightedAverageAndMissingOptionalAsZero() {
        String first = weightedSingle("Q1", true, "10.00", "0.00", "1.00");
        String second = weightedSingle("Q2", false, "10.00", "0.00", "3.00");
        var definition = SatisfactionQuestionnaireDefinition.parse(
                config(first + "," + second, "WEIGHTED_AVERAGE_V1", "10.00", "2.50", 2, "HALF_EVEN"));

        var result = definition.evaluate(answers(answer("Q1", "YES")), true);

        assertEquals(new BigDecimal("2.50"), result.score());
        assertTrue(result.passed());
    }

    @Test
    void rejectsUnreachableMultipleChoiceScoreMax() {
        String multi = "{\"code\":\"Q1\",\"title\":\"多选\",\"type\":\"MULTIPLE_CHOICE\","
                + "\"required\":true,\"minSelections\":2,\"maxSelections\":2,\"options\":["
                + option("A", "100.00") + "," + option("B", "0.00") + "]}";

        assertThrows(IllegalArgumentException.class,
                () -> SatisfactionQuestionnaireDefinition.parse(
                        config(multi, "SUM_V1", "100.00", "80.00", 0, "HALF_UP")));
    }

    @Test
    void rejectsInvalidAnswerBeforeScoring() {
        var definition = SatisfactionQuestionnaireDefinition.parse(
                config(single("Q1", true, "5.00", "0.00"), "SUM_V1", "5.00", "4.00", 0, "HALF_UP"));

        assertThrows(IllegalArgumentException.class,
                () -> definition.evaluate(answers(answer("Q1", "UNKNOWN")), true));
    }

    @Test
    void missingRequiredProducesFailedEvaluation() {
        var definition = SatisfactionQuestionnaireDefinition.parse(
                config(single("Q1", true, "5.00", "0.00"), "SUM_V1", "5.00", "4.00", 0, "HALF_UP"));

        var result = definition.evaluate(answers(""), true);

        assertEquals(BigDecimal.ZERO.setScale(0), result.score());
        assertFalse(result.requiredComplete());
        assertFalse(result.passed());
    }

    private String config(String questions, String strategy, String scoreMax,
                          String threshold, int precision, String rounding) {
        return "{\"schemaVersion\":1,\"questions\":[" + questions + "],\"scoring\":{"
                + "\"ruleVersion\":\"RULE-1\",\"strategy\":\"" + strategy + "\","
                + "\"scoreMin\":\"0.00\",\"scoreMax\":\"" + scoreMax + "\","
                + "\"precision\":" + precision + ",\"roundingMode\":\"" + rounding + "\","
                + "\"threshold\":\"" + threshold + "\"}}";
    }

    private String single(String code, boolean required, String yes, String no) {
        return "{\"code\":\"" + code + "\",\"title\":\"题目\",\"type\":\"SINGLE_CHOICE\","
                + "\"required\":" + required + ",\"options\":[" + option("YES", yes) + ","
                + option("NO", no) + "]}";
    }

    private String weightedSingle(String code, boolean required, String yes, String no, String weight) {
        String plain = single(code, required, yes, no);
        return plain.substring(0, plain.length() - 1) + ",\"weight\":\"" + weight + "\"}";
    }

    private String option(String code, String score) {
        return "{\"code\":\"" + code + "\",\"label\":\"" + code + "\",\"score\":\"" + score + "\"}";
    }

    private String answers(String items) {
        return "{\"answers\":[" + items + "]}";
    }

    private String answer(String code, String value) {
        return "{\"questionCode\":\"" + code + "\",\"value\":\"" + value + "\"}";
    }
}
