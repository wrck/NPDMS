package cn.iocoder.yudao.module.pms.acceptance.domain.satisfaction;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies the approved seed with the production parser and scorer, without duplicating its algorithm. */
class CompletionOriginalQuestionnaireTest {
    private SatisfactionQuestionnaireDefinition original() throws Exception {
        var migration = Files.readString(Path.of("..", "sql", "migrations", "V430__completion_satisfaction_original_questionnaire.sql"));
        var matcher = Pattern.compile("'(\\{\"schemaVersion\":1.*?\\})',80\\.00", Pattern.DOTALL).matcher(migration);
        assertTrue(matcher.find(), "The original questionnaire seed must have an explicit approved scoring definition");
        return SatisfactionQuestionnaireDefinition.parse(matcher.group(1));
    }
    private String answers(int count, boolean lowerOne) {
        var ratings = IntStream.rangeClosed(1, count).mapToObj(index ->
                "{\"questionCode\":\"rating_" + index + "\",\"value\":\"" + (lowerOne && index == 1 ? "average" : "satisfied") + "\"}").toList();
        return "{\"answers\":[" + String.join(",", ratings) + ",{\"questionCode\":\"repurchase\",\"value\":\"其他：仍在评估\"},"
                + "{\"questionCode\":\"product_opinion\",\"value\":\"产品意见\"},{\"questionCode\":\"service_opinion\",\"value\":\"服务意见\"}]}";
    }
    @Test void tenRatingsAverageToEightyAndUnscoredOpinionsDoNotChangeTheScore() throws Exception {
        var evaluation = original().evaluate(answers(10, false), true);
        assertEquals(new BigDecimal("80.00"), evaluation.score());
        assertEquals(new BigDecimal("80.00"), evaluation.threshold());
        assertTrue(evaluation.passed());
    }
    @Test void oneLowerRatingMakesTheAverageFailAtSeventyEight() throws Exception {
        var evaluation = original().evaluate(answers(10, true), true);
        assertEquals(new BigDecimal("78.00"), evaluation.score());
        assertFalse(evaluation.passed());
    }
    @Test void missingRequiredRatingOrMissingSignatureDoesNotPass() throws Exception {
        assertFalse(original().evaluate(answers(9, false), true).requiredComplete());
        assertFalse(original().evaluate(answers(10, false), false).passed());
    }
}
