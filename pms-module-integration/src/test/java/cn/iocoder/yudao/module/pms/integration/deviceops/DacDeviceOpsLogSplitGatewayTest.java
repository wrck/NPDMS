package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsLogSplitApi;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DacDeviceOpsLogSplitGatewayTest {
    DacDeviceOpsLogSplitGateway gateway;
    MockRestServiceServer server;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var properties = new DacDeviceOpsGatewayProperties();
        properties.setBaseUrl("https://dac.test"); properties.setBearerToken("synthetic-test-token");
        gateway = new DacDeviceOpsLogSplitGateway(properties);
        server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(gateway, "http")).build();
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void offlineDependencyReturnsActionableUnavailableWithoutLeakingTransportDetails() {
        server.expect(anything()).andRespond(withException(new IOException("private transport details")));
        var failure = assertThrows(ServiceException.class, () -> gateway.split(command()));
        assertEquals(503, failure.getCode());
        assertTrue(failure.getMessage().contains("启动"));
        assertFalse(failure.getMessage().contains("private"));
    }
    @Test void authorizationFailureIsNotMisreportedAsInvalidLogOrEchoed() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN).body("private response"));
        var failure = assertThrows(ServiceException.class, () -> gateway.split(command()));
        assertEquals(503, failure.getCode());
        assertTrue(failure.getMessage().contains("授权"));
        assertFalse(failure.getMessage().contains("private"));
    }
    @Test void malformedLogRemainsAnExplicitRejection() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("{\"code\":\"DEVICE_SERIAL_MISSING\"}"));
        var failure = assertThrows(DeviceOpsLogSplitApi.Rejected.class, () -> gateway.split(command()));
        assertEquals("日志块缺少设备序列号", failure.getMessage());
    }
    private DeviceOpsLogSplitApi.Command command() {
        return new DeviceOpsLogSplitApi.Command(1L, 10L, "TERMINAL_TEXT", "SYNTHETIC");
    }
}
