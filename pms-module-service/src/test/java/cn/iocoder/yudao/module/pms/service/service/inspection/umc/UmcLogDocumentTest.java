package cn.iocoder.yudao.module.pms.service.service.inspection.umc;

import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceLogParsingApi;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UmcLogDocumentTest {
    @Test void sourceAddressComesOnlyFromAnExactUmcFilename() {
        assertEquals("192.0.2.10", UmcLogDocument.sourceHostFromFileName("192.0.2.10"));
        assertEquals("192.0.2.10", UmcLogDocument.sourceHostFromFileName("设备甲(192.0.2.10)"));
        assertEquals("192.0.2.10", UmcLogDocument.sourceHostFromFileName("设备甲（192.0.2.10）"));
        assertNull(UmcLogDocument.sourceHostFromFileName(null));
        for (String name : List.of("capture.log", "192.0.2.10.txt", "设备甲(192.0.2.10).log", "设备甲(192.0.2.999)",
                "192.0.2.01", " 192.0.2.10", "192.0.2.10 ", "../192.0.2.10", "folder/设备甲(192.0.2.10)",
                "设备甲\r\n(192.0.2.10)", "name((192.0.2.10))", "name(192.0.2.10)(192.0.2.11)", "设备甲(2001:db8::1)"))
            assertNull(UmcLogDocument.sourceHostFromFileName(name), name);
    }
    @Test void rendersRedactedPromptTextWithCrLfAndRemovesOnlyDuplicateCommandEcho() {
        var block = new DeviceLogParsingApi.LogBlock(0, "show version", "show version\nSN: TEST-SN\n", "", "OK", false, 0);
        var result = new String(UmcLogDocument.render(new UmcLogDocument.Input("test-device", List.of(block))), StandardCharsets.UTF_8);
        assertEquals("<test-device>show version\r\nSN: TEST-SN\r\n\r\n", result);
    }
    @Test void rejectsTruncatedOrMissingBlocksAndInvalidSourceIp() {
        var truncated = new DeviceLogParsingApi.LogBlock(0, "show version", "partial", "", "OK", true, 0);
        assertThrows(InspectionException.class, () -> UmcLogDocument.render(new UmcLogDocument.Input("test", List.of(truncated))));
        assertThrows(InspectionException.class, () -> UmcLogDocument.render(new UmcLogDocument.Input("test", List.of())));
        for (String value : List.of("192.0.2.999", "192.0.2.1.txt", "192.0.2.01", "localhost"))
            assertThrows(InspectionException.class, () -> UmcLogDocument.requireIpv4(value));
        assertDoesNotThrow(() -> UmcLogDocument.requireIpv4("192.0.2.1"));
    }
}
