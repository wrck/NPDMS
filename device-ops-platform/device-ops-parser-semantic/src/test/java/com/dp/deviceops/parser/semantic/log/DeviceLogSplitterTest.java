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
    @Test void splitsSameHostnameByCompleteCommandOutputSerialAndKeepsRepeatedOutputs() {
        var result = splitter.split("<SAME-HOST>show ver\n<SAME-HOST>show version\nSerial Number: SN001\nfirst-version\n"
                + "<SAME-HOST>show tech\n<SAME-HOST>show tech-support\nSerial Number: SN001\nfirst-tech\n"
                + "<SAME-HOST>\n<SAME-HOST>show version\nSerial Number: SN002\nsecond-version\n"
                + "<SAME-HOST>show tech-support\nSerial Number: SN002\nsecond-tech\n");
        assertEquals(2, result.devices().size());
        var first = result.devices().getFirst();
        var second = result.devices().getLast();
        assertEquals("SN001", first.serialNumber());
        assertEquals("SN002", second.serialNumber());
        assertTrue(first.content().contains("first-version"));
        assertTrue(first.content().contains("first-tech"));
        assertFalse(first.content().contains("second-version"));
        assertTrue(second.content().contains("second-version"));
        assertTrue(second.content().contains("second-tech"));
        assertFalse(second.content().contains("first-tech"));
    }
    @Test void rejectsMultipleSerialsInsideOneCommandAndUnknownOutputBetweenDifferentDevices() {
        assertEquals("DEVICE_SERIAL_AMBIGUOUS", assertThrows(DeviceLogSplitter.SplitException.class,
                () -> splitter.split("<SAME>show tech\nSN: ONE1\nSN: TWO2\n")).code());
        assertEquals("DEVICE_SERIAL_AMBIGUOUS", assertThrows(DeviceLogSplitter.SplitException.class,
                () -> splitter.split("<SAME>show version\nSN: ONE1\n<SAME>show run\nunknown-output\n"
                        + "<SAME>show version\nSN: TWO2\n")).code());
    }
    @Test void retainsUnidentifiedCommandOnlyWhenSurroundingSerialsAgree() {
        var result = splitter.split("<SAME>show version\nSN: ONE1\n<SAME>show run\nfirst-config\n"
                + "<SAME>show tech\nSN: ONE1\n<SAME>show tech\nSN: TWO2\nsecond-config\n");
        assertTrue(result.devices().getFirst().content().contains("first-config"));
        assertFalse(result.devices().getLast().content().contains("first-config"));
    }
}
