package cn.iocoder.yudao.module.pms.commerce.api;

import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.*;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** COM-01 / amendment 014: source namespace, quantity precision and legacy item identity. */
class CommerceErpSourceContractTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 9, 7, 0, 0);
    @Test void rejectsOtherSystemsAsConfirmedErpAuthority() {
        var order = new CommerceSalesOrderFact("O1", null, "1", "ACME", "O1", "NORMAL", null, null,
                null, null, CommerceSourceLifecycleStatus.ACTIVE, TIME);
        for (String source : List.of("CRM", "SMS", "MANUAL", "SEED")) {
            assertThrows(CommerceAuthorityIngestException.class, () -> new CommerceAuthorityBatchCommand(1L,
                    "event", "batch", source, "watermark", List.of(), List.of(order), List.of(), List.of(), TIME, "correlation"));
        }
    }
    @Test void doesNotInventProductCodeFromLegacyItemCode() {
        var line = new CommerceOrderLineFact("L1", null, "1", "O1", "1", "ITEM", "MODEL",
                BigDecimal.ONE, "SET", CommerceSourceLifecycleStatus.ACTIVE, TIME);
        assertEquals("ITEM", line.itemCode());
        assertNull(line.productCode());
    }
    @Test void rejectsQuantityBeyondDeclaredUnitScale() {
        assertThrows(CommerceAuthorityIngestException.class, () -> new CommerceOrderLineFact("L1", null, "1", "O1",
                "1", "ITEM", "Description", "PRODUCT", "MODEL", new BigDecimal("1.5"), null, null,
                "SET", 0, "CONFIRMED", CommerceSourceLifecycleStatus.ACTIVE, TIME));
    }
}
