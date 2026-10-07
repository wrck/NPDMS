package com.dp.deviceops.parser.semantic.log;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeviceLogSplitterTest {
    private final DeviceLogSplitter splitter = new DeviceLogSplitter();
    @Test void keepsCompleteCommandsAndMergesRepeatedDeviceSessions() {
        var result = splitter.split("R1#show run\nSN: SN001\ninterface Gi1\n end\nR1#show tech\nuptime 10\n"
                + "R2#show tech\nSystem Serial Number: SN002\ninterface Gi2\nend\n"
                + "R1#show tech\nProcessor board ID SN001\nlast line\n");
        assertEquals(2, result.devices().size());
        assertEquals("SN001", result.devices().getFirst().serialNumber());
        assertTrue(result.devices().getFirst().content().contains("interface Gi1"));
        assertTrue(result.devices().getFirst().content().contains("uptime 10"));
        assertTrue(result.devices().getFirst().content().contains("last line"));
        assertFalse(result.devices().getFirst().content().contains("interface Gi2"));
    }
    @Test void unknownAndAmbiguousBlocksFailWithoutGuessing() {
        assertEquals("DEVICE_SERIAL_MISSING", assertThrows(DeviceLogSplitter.SplitException.class,
                () -> splitter.split("R1#show tech\nuptime 10\n")).code());
        assertEquals("DEVICE_SERIAL_AMBIGUOUS", assertThrows(DeviceLogSplitter.SplitException.class,
                () -> splitter.split("SN: ONE1\nSN: TWO2\n")).code());
        assertThrows(DeviceLogSplitter.SplitException.class, () -> splitter.split("R1#show run\nSN: ONE1\nR2#show run\nno serial\n"));
    }
    @Test void secretsAreRemovedFromParentAndDeviceFiles() {
        var result = splitter.split("<R1>display diagnostic-information\nESN: HW001\npassword simple test-secret\n"
                + "-----BEGIN PRIVATE KEY-----\nprivate-key-content\n-----END PRIVATE KEY-----\ninterface GE1\n"
                + "api-key sample-api-key\nsnmp-server user x group v3 auth sha auth-value priv aes 128 privacy-value\n");
        assertFalse(result.sanitizedSource().contains("test-secret"));
        assertFalse(result.devices().getFirst().content().contains("private-key-content"));
        assertFalse(result.sanitizedSource().contains("sample-api-key"));
        assertFalse(result.sanitizedSource().contains("auth-value"));
        assertFalse(result.sanitizedSource().contains("privacy-value"));
        assertTrue(result.devices().getFirst().content().contains("interface GE1"));
        assertTrue(result.redactedValueCount() > 0);
    }
}
