package cn.iocoder.yudao.module.pms.service.domain.inspection;

import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionResultEvaluator.CommandOutput;
import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionResultEvaluator.Evaluation;
import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionResultEvaluator.Input;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InspectionResultEvaluatorTest {
    private final InspectionResultEvaluator evaluator = new InspectionResultEvaluator();

    @Test
    void requiresBothRegexAndThresholdAndDoesNotRequireASerialNumber() {
        Evaluation passing = evaluator.evaluate(input("健康", List.of(command("设备健康", List.of("10ms")))));
        assertEquals("PASS", passing.outcome());
        assertEquals(new BigDecimal("10"), passing.measuredValue());
        assertEquals("ms", passing.measuredUnit());
        assertEquals(Boolean.TRUE, passing.regexMatched());
        Evaluation thresholdFailure = evaluator.evaluate(input("健康", List.of(command("设备健康", List.of("11ms")))));
        assertEquals("ABNORMAL", thresholdFailure.outcome());
        assertEquals("THRESHOLD_NOT_MET", thresholdFailure.detailCode());
    }

    @Test
    void preservesCommandOrderAndUsesFindRatherThanWholeTextMatch() {
        List<CommandOutput> commands = List.of(command("prefix first", List.of("10ms")), command("second suffix", List.of("10.0ms")));
        assertEquals("PASS", evaluator.evaluate(input("first\\nsecond", commands)).outcome());
        assertEquals("ABNORMAL", evaluator.evaluate(input("second\\nfirst", commands)).outcome());
    }

    @Test
    void explicitRegexFailureIsAbnormalEvenWhenNoNumericValueIsAvailable() {
        Evaluation result = evaluator.evaluate(input("healthy", List.of(command("failed condition", List.of()))));
        assertEquals("ABNORMAL", result.outcome());
        assertEquals(Boolean.FALSE, result.regexMatched());
        assertEquals("REGEX_NOT_MATCHED", result.detailCode());
        assertNull(result.measuredValue());
    }

    @Test
    void missingEmptyAndLimitedOutputsRemainUnjudgedBeforeMatching() {
        assertEquals("UNJUDGED", evaluator.evaluate(input("unmatched", List.of())).outcome());
        for (CommandOutput command : List.of(
                new CommandOutput("show status", "SUCCEEDED", "STRUCTURED", "OK", List.of("10ms"), false),
                new CommandOutput("show status", "SUCCEEDED", "STRUCTURED", " \r\n", List.of("10ms"), true),
                new CommandOutput("show status", "SUCCEEDED", "EMPTY", "OK", List.of("10ms"), true),
                new CommandOutput("show status", "SUCCEEDED", "LIMITED", "OK", List.of("10ms"), true))) {
            Evaluation result = evaluator.evaluate(input("unmatched", List.of(command)));
            assertEquals("UNJUDGED", result.outcome());
            assertNull(result.regexMatched());
        }
    }

    @Test
    void failureAndTimeoutHavePriorityOverMissingOutputs() {
        CommandOutput missing = new CommandOutput("show missing", "SUCCEEDED", "EMPTY", null, List.of(), false);
        for (String status : List.of("FAILED", "TIMED_OUT")) {
            CommandOutput failed = new CommandOutput("show failed", status, "EMPTY", null, List.of(), true);
            Evaluation result = evaluator.evaluate(input("OK", List.of(missing, failed)));
            assertEquals("ERROR", result.outcome());
            assertNull(result.regexMatched());
        }
    }

    @Test
    void unknownOrNonFinalStatusesCannotBecomePass() {
        for (String status : List.of("RUNNING", "CANCELLED", "UNKNOWN", "SUCCESS")) {
            var command = new CommandOutput("show status", status, "STRUCTURED", "OK", List.of("10ms"), true);
            Evaluation result = evaluator.evaluate(input("OK", List.of(command)));
            assertEquals("UNJUDGED", result.outcome());
            assertEquals("COMMAND_NOT_SUCCEEDED", result.detailCode());
        }
    }

    @Test
    void acceptsSignedDecimalsAndScientificNotationWithExactUnits() {
        for (String value : List.of("+1e1ms", "1.0E+1 ms", "10.0 ms", "+10.ms")) {
            Evaluation result = evaluator.evaluate(input("OK", List.of(command("OK", List.of(value)))));
            assertEquals("PASS", result.outcome(), value);
            assertEquals(0, new BigDecimal("10").compareTo(result.measuredValue()));
        }
        var negative = Input.builder().expectedRegex("OK").thresholdOperator("<").thresholdValue(BigDecimal.ZERO)
                .thresholdUnit("ms").commands(List.of(command("OK", List.of("-.5 ms")))).build();
        assertEquals("PASS", evaluator.evaluate(negative).outcome());
    }

    @Test
    void supportsAllSixApprovedComparisonOperatorsAtBoundaries() {
        for (Comparison comparison : List.of(new Comparison(">", "9", "PASS"), new Comparison(">", "10", "ABNORMAL"),
                new Comparison("<", "11", "PASS"), new Comparison("<", "10", "ABNORMAL"),
                new Comparison("≥", "10", "PASS"), new Comparison("≥", "11", "ABNORMAL"),
                new Comparison("≤", "10", "PASS"), new Comparison("≤", "9", "ABNORMAL"),
                new Comparison("=", "10.00", "PASS"), new Comparison("=", "11", "ABNORMAL"),
                new Comparison("≠", "11", "PASS"), new Comparison("≠", "10", "ABNORMAL"))) {
            var input = Input.builder().expectedRegex("OK").thresholdOperator(comparison.operator())
                    .thresholdValue(new BigDecimal(comparison.threshold())).thresholdUnit("ms")
                    .commands(List.of(command("OK", List.of("10ms")))).build();
            assertEquals(comparison.outcome(), evaluator.evaluate(input).outcome(), comparison.toString());
        }
    }

    @Test
    void repeatsEquivalentMeasurementsButRejectsDifferentNumbersOrUnits() {
        assertEquals("PASS", evaluator.evaluate(input("OK", List.of(command("OK", List.of("10ms", "10.00ms", "1e1ms"))))).outcome());
        for (List<String> values : List.of(List.of("10ms", "11ms"), List.of("10ms", "10s"))) {
            Evaluation result = evaluator.evaluate(input("OK", List.of(command("OK", values))));
            assertEquals("UNJUDGED", result.outcome());
            assertEquals("VALUE_AMBIGUOUS", result.detailCode());
            assertNull(result.measuredValue());
        }
    }

    @Test
    void requiresAnExplicitUnitAndNeverConvertsUnits() {
        assertEquals("UNIT_MISSING", evaluator.evaluate(input("OK", List.of(command("OK", List.of("10"))))).detailCode());
        for (String value : List.of("0.01s", "10MS")) {
            Evaluation result = evaluator.evaluate(input("OK", List.of(command("OK", List.of(value)))));
            assertEquals("UNJUDGED", result.outcome());
            assertEquals("UNIT_MISMATCH", result.detailCode());
        }
        var dimensionless = Input.builder().expectedRegex("10").thresholdOperator("=").thresholdValue(BigDecimal.TEN)
                .thresholdUnit("1").commands(List.of(command("10", List.of("1e1", "10.0 1")))).build();
        assertEquals("PASS", evaluator.evaluate(dimensionless).outcome());
        assertEquals("1", evaluator.evaluate(dimensionless).measuredUnit());
    }

    @Test
    void onlyReadsCompleteParsedValuesOrCompleteSingleNumericOutputs() {
        assertEquals("PASS", evaluator.evaluate(input("10", List.of(command("10ms", List.of())))).outcome());
        Evaluation missing = evaluator.evaluate(input("latency=([0-9]+)", List.of(command("latency=10ms; status=OK", List.of()))));
        assertEquals("UNJUDGED", missing.outcome());
        assertEquals("VALUE_MISSING", missing.detailCode());
        Evaluation conflicts = evaluator.evaluate(input("10", List.of(command("10ms", List.of("11ms")))));
        assertEquals("UNJUDGED", conflicts.outcome());
        assertEquals("VALUE_AMBIGUOUS", conflicts.detailCode());
    }

    @Test
    void longOutputsAreNotTruncatedIntoPassingInputs() {
        Evaluation result = evaluator.evaluate(input("OK", List.of(command("OK " + "x".repeat(262_144), List.of("10ms")))));
        assertEquals("UNJUDGED", result.outcome());
        assertEquals("OUTPUT_LIMIT", result.detailCode());
        assertNull(result.regexMatched());
        Evaluation tooManyValues = evaluator.evaluate(input("OK", List.of(command("OK", Collections.nCopies(1025, "10ms")))));
        assertEquals("OUTPUT_LIMIT", tooManyValues.detailCode());
        Evaluation hugeNumber = evaluator.evaluate(input("OK", List.of(command("OK", List.of("9".repeat(4097) + "ms")))));
        assertEquals("UNJUDGED", hugeNumber.outcome());
        assertEquals("OUTPUT_LIMIT", hugeNumber.detailCode());
    }

    @Test
    void restrictedRegexValidationRejectsUnsupportedNestedQuantifiers() {
        Evaluation result = evaluator.evaluate(input("(a+)+$", List.of(command("aaaa!", List.of("10ms")))));
        assertEquals("ERROR", result.outcome());
        assertEquals("REGEX_INVALID", result.detailCode());
    }

    @Test
    void acceptedButExpensiveBacktrackingStopsInTheCallingThread() {
        // Adjacent repetitions pass the existing restricted validator yet can still require excessive backtracking.
        Evaluation result = assertTimeout(Duration.ofSeconds(3), () -> evaluator.evaluate(
                input("^a+a+a+a+a+b$", List.of(command("a".repeat(512) + "!", List.of("10ms"))))));
        assertEquals("ERROR", result.outcome());
        assertEquals("REGEX_LIMIT", result.detailCode());
        assertNull(result.regexMatched());
    }

    private record Comparison(String operator, String threshold, String outcome) { }

    private Input input(String regex, List<CommandOutput> commands) {
        return Input.builder().expectedRegex(regex).thresholdOperator("=").thresholdValue(BigDecimal.TEN)
                .thresholdUnit("ms").commands(commands).build();
    }

    private CommandOutput command(String output, List<String> values) {
        return new CommandOutput("show status", "SUCCEEDED", "STRUCTURED", output, values, true);
    }
}
