package cn.iocoder.yudao.module.pms.asset.domain.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeviceIdentityRulesTest {

    @Test
    void shouldRejectBlankSn() {
        assertThrows(IllegalArgumentException.class, () -> DeviceIdentityRules.requireSn(" "));
    }

    @Test
    void shouldRejectIdOrSnMutation() {
        assertThrows(IllegalStateException.class,
                () -> DeviceIdentityRules.requireImmutable(1L, "SN-1", 2L, "SN-1"));
        assertThrows(IllegalStateException.class,
                () -> DeviceIdentityRules.requireImmutable(1L, "SN-1", 1L, "SN-2"));
        assertDoesNotThrow(() -> DeviceIdentityRules.requireImmutable(1L, "SN-1", 1L, "SN-1"));
    }

    @Test
    void shouldRequireManualReasonAndEvidence() {
        assertThrows(IllegalArgumentException.class,
                () -> DeviceIdentityRules.requireManualEvidence("", "evidence"));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceIdentityRules.requireManualEvidence("reason", ""));
        assertDoesNotThrow(() -> DeviceIdentityRules.requireManualEvidence("reason", "evidence"));
    }
}
