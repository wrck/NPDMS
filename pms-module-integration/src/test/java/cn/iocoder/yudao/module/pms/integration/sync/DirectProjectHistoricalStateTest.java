package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DirectProjectHistoricalState.*;

class DirectProjectHistoricalStateTest {
    @Test void noTrackingUsesDistinctTerminalStateAndKeepsHistoricalEvidence() {
        var closedAt = LocalDateTime.of(2024, 12, 23, 9, 46, 31);
        var active = new State("ACTIVE", "S4", null);
        var expected = new State("NO_TRACKING_CLOSED", "S4", closedAt);
        assertEquals(expected, resolve("20", closedAt, active, false));
        assertEquals(expected, resolve("20", closedAt, expected, true));
        assertEquals(new State("NO_TRACKING_CLOSED", "S4", null), resolve("20", null, active, false));
        assertThrows(IllegalArgumentException.class, () -> resolve("20", closedAt,
                new State("EXCEPTION_CLOSED", "S4", closedAt), true));
        assertThrows(IllegalArgumentException.class, () -> resolve("afss", "20", closedAt, active, false));
    }
}
