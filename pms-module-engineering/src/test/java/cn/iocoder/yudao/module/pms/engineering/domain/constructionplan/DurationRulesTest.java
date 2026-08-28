package cn.iocoder.yudao.module.pms.engineering.domain.constructionplan;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationRulesTest {

    @Test
    void dateRangeUsesInclusiveNaturalDaysAndAcceptsMatchingDerivedValue() {
        var result = DurationRules.resolve(DurationRules.DATE_RANGE,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 3);

        assertEquals(LocalDate.of(2026, 8, 1), result.startDate());
        assertEquals(LocalDate.of(2026, 8, 3), result.endDate());
        assertEquals(3, result.durationDays());
    }

    @Test
    void durationFromStartDerivesInclusiveEndAndAcceptsMatchingDerivedValue() {
        var result = DurationRules.resolve(DurationRules.DURATION_FROM_START,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 3);

        assertEquals(LocalDate.of(2026, 8, 3), result.endDate());
        assertEquals(3, result.durationDays());
    }

    @Test
    void rejectsInvertedRangeAndNonPositiveDuration() {
        assertThrows(IllegalArgumentException.class, () -> DurationRules.resolve(
                DurationRules.DATE_RANGE,
                LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1), null));
        assertThrows(IllegalArgumentException.class, () -> DurationRules.resolve(
                DurationRules.DURATION_FROM_START,
                LocalDate.of(2026, 8, 1), null, 0));
    }

    @Test
    void rejectsConflictingDerivedValuesAndUnknownBasis() {
        assertThrows(IllegalArgumentException.class, () -> DurationRules.resolve(
                DurationRules.DATE_RANGE,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 4));
        assertThrows(IllegalArgumentException.class, () -> DurationRules.resolve(
                DurationRules.DURATION_FROM_START,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 4), 3));
        assertThrows(IllegalArgumentException.class, () -> DurationRules.resolve(
                "UNKNOWN", LocalDate.of(2026, 8, 1), null, 3));
    }

    @Test
    void rejectsDateAndDurationOverflow() {
        assertThrows(IllegalArgumentException.class, () -> DurationRules.resolve(
                DurationRules.DATE_RANGE, LocalDate.MIN, LocalDate.MAX, null));
        assertThrows(IllegalArgumentException.class, () -> DurationRules.resolve(
                DurationRules.DURATION_FROM_START, LocalDate.MAX, null, 2));
    }

}
