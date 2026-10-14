package cn.iocoder.yudao.server.sync;

/** Test-only accepted historical limits. An overrun never updates the baseline. */
final class InitialMigrationPerformanceBudget {
    static final double TOTAL_SECONDS = 4262.754054886;
    private static final double[] SOURCE_SECONDS = {
            74.702411047, 56.730396015, 59.773085646, 176.516666821};

    record Violation(String status, String dimension, int sourceIndex,
                     double observedSeconds, double baselineSeconds,
                     boolean stopRemainingAcceptance) {}

    static InitialMigrationPerformanceBudget historicalV7() {
        return new InitialMigrationPerformanceBudget();
    }

    Violation source(int index, double seconds) {
        duration(seconds);
        if (index < 0 || index > 4) throw new IllegalArgumentException("Unknown full-chain source");
        // The barcode source has no separately supplied budget; the task limit applies.
        return index == 4 ? null : compare("source", index, seconds, SOURCE_SECONDS[index]);
    }

    Violation total(double seconds) {
        duration(seconds);
        return compare("task", -1, seconds, TOTAL_SECONDS);
    }

    private static Violation compare(String dimension, int index, double seconds, double baseline) {
        return seconds > baseline ? new Violation("PERFORMANCE_REGRESSION", dimension, index,
                seconds, baseline, true) : null;
    }

    private static void duration(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0)
            throw new IllegalArgumentException("Finite nonnegative elapsed time required");
    }
}
