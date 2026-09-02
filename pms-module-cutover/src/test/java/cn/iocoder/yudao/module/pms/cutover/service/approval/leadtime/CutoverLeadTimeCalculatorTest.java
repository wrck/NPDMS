package cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutoverLeadTimeCalculatorTest {

    private static final Map<String, Integer> RULES = Map.ofEntries(
            Map.entry("DEVICE_REPLACE_WHOLE", 5),
            Map.entry("DEVICE_REPLACE_BOARD", 3),
            Map.entry("DEVICE_REPLACE_VENDOR", 7),
            Map.entry("DEVICE_ONBOARD", 7),
            Map.entry("VERSION_UPGRADE", 2),
            Map.entry("DISASTER_RECOVERY_DRILL", 2),
            Map.entry("CONFIGURATION_CHANGE", 2),
            Map.entry("NETWORK_TOPOLOGY_CHANGE", 3),
            Map.entry("VERSION_PATCH", 2),
            Map.entry("SIGNATURE_UPGRADE", 1));

    private final CutoverLeadTimeCalculator calculator = new CutoverLeadTimeCalculator();

    @Test
    void calculatesEveryFrozenRuleAtLateExactAndEarlyBoundaries() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 9, 1, 10, 30);

        RULES.forEach((cutoverType, requiredDays) -> {
            CutoverLeadTimeCompliance late = calculator.calculate("A", cutoverType,
                    submittedAt.plusDays(requiredDays - 1L), submittedAt);
            CutoverLeadTimeCompliance exact = calculator.calculate("A", cutoverType,
                    submittedAt.plusDays(requiredDays), submittedAt);
            CutoverLeadTimeCompliance early = calculator.calculate("B", cutoverType,
                    submittedAt.plusDays(requiredDays + 1L), submittedAt);

            assertEquals(requiredDays, late.requiredDays());
            assertEquals(requiredDays - 1, late.actualNaturalDays());
            assertTrue(late.lateSubmission());
            assertEquals(requiredDays, exact.actualNaturalDays());
            assertFalse(exact.lateSubmission());
            assertEquals(requiredDays + 1, early.actualNaturalDays());
            assertFalse(early.lateSubmission());
            assertEquals(CutoverLeadTimeCompliance.RULE_VERSION, exact.ruleVersion());
            assertEquals(CutoverLeadTimeCompliance.TIMEZONE_ID, exact.timezoneId());
        });
    }

    @Test
    void usesBusinessDatesInsteadOfElapsedTwentyFourHourWindows() {
        CutoverLeadTimeCompliance result = calculator.calculate("A", "SIGNATURE_UPGRADE",
                LocalDateTime.of(2026, 9, 2, 0, 1),
                LocalDateTime.of(2026, 9, 1, 23, 59));

        assertEquals(1, result.actualNaturalDays());
        assertFalse(result.lateSubmission());
    }

    @Test
    void rejectsGradesAndTypesOutsideTheFrozenRule() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 10, 0);

        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate("C", "VERSION_UPGRADE", now.plusDays(2), now));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate("A", "UNKNOWN", now.plusDays(2), now));
    }
}
