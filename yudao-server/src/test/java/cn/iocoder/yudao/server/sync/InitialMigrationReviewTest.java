package cn.iocoder.yudao.server.sync;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InitialMigrationReviewTest {
    @Test void explicitEquivalentWorkReviewAllowsVerification() {
        assertEquals(InitialMigrationReview.Decision.CONTINUE,
                InitialMigrationReview.decision("CONTINUE_INITIAL_VERIFICATION\n"));
    }
    @Test void confirmedRegressionStopsRemainingVerification() {
        assertEquals(InitialMigrationReview.Decision.STOP,
                InitialMigrationReview.decision("STOP_PERFORMANCE"));
    }
    @Test void ambiguousOrMissingReviewNeverAllowsVerification() {
        for(String text : new String[]{null,"","PASS","1.5","CONTINUE","STOP_PERFORMANCE\nCONTINUE_INITIAL_VERIFICATION"})
            assertThrows(IllegalArgumentException.class,()->InitialMigrationReview.decision(text));
    }
}
