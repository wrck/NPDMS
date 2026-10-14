package cn.iocoder.yudao.server.sync;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InitialMigrationPerformanceBudgetTest {
    final InitialMigrationPerformanceBudget budget = InitialMigrationPerformanceBudget.historicalV7();

    @Test void slowFirstSourceStopsBeforeLaterSources() {
        var failure = budget.source(0, 229.111742476);
        assertEquals("PERFORMANCE_REGRESSION", failure.status());
        assertEquals(74.702411047, failure.baselineSeconds());
        assertTrue(failure.stopRemainingAcceptance());
    }
    @Test void equalAndFasterMeasurementsPass() {
        assertNull(budget.source(0, 74.702411047));
        assertNull(budget.source(3, 100));
        assertNull(budget.total(InitialMigrationPerformanceBudget.TOTAL_SECONDS));
    }
    @Test void totalBudgetAlsoStopsDuringLastSource() {
        assertNull(budget.source(4, 4000));
        assertNotNull(budget.total(4300));
    }
    @Test void unknownSourceFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> budget.source(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> budget.source(5, 0));
    }
    @Test void invalidDurationsDoNotPass() {
        for(double seconds : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> budget.total(seconds));
            assertThrows(IllegalArgumentException.class, () -> budget.source(0, seconds));
        }
    }
}
