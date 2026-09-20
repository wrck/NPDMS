package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchCommand;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.deviceops.DeviceOpsDispatchDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.deviceops.DeviceOpsDispatchMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@ExtendWith(MockitoExtension.class)
class DacDeviceOpsGatewayTest {

    private static final String BASE = "http://dac.test";

    @Mock
    DeviceOpsDispatchMapper dispatchMapper;

    private DacDeviceOpsGatewayProperties properties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private DacDeviceOpsGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new DacDeviceOpsGatewayProperties();
        properties.setBaseUrl(BASE);
        properties.setEnabled(true);
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        gateway = new DacDeviceOpsGateway(properties, dispatchMapper, restTemplate);
        TenantContextHolder.setTenantId(0L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void dispatchSubmitsDacContractAndRecordsMapping() {
        server.expect(requestTo(BASE + "/api/v1/collections"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "task-1"))
                .andExpect(jsonPath("$.namespace").value("npdms"))
                .andExpect(jsonPath("$.context.project.projectKey").value("project-1"))
                .andExpect(jsonPath("$.context.device.deviceKey").value("device-1"))
                .andExpect(jsonPath("$.connection.protocol").value("SSH2"))
                .andExpect(jsonPath("$.connection.executionMode").value("EXEC"))
                .andExpect(jsonPath("$.connection.authenticationType").value("PASSWORD"))
                .andExpect(jsonPath("$.connection.password").value("secret"))
                .andExpect(jsonPath("$.script.source").value("ADHOC_INLINE"))
                .andExpect(jsonPath("$.script.content").value("show version\ndisplay current"))
                .andExpect(jsonPath("$.script.sha256").value(sha256("show version\ndisplay current")))
                .andExpect(jsonPath("$.externalRequestId").value("task-1"))
                .andExpect(jsonPath("$.commandTimeoutSeconds").value(30))
                .andExpect(jsonPath("$.parseTimeoutSeconds").value(30))
                .andExpect(jsonPath("$.leaseGraceSeconds").value(0))
                .andExpect(jsonPath("$.connection.connectTimeoutSeconds").value(10))
                .andRespond(withSuccess("{\"collectionId\":\"dac-1\",\"existing\":false}",
                        MediaType.APPLICATION_JSON));
        when(dispatchMapper.selectByPlatformTaskId("task-1")).thenReturn(null);

        var result = gateway.dispatch(command("secret".toCharArray()));

        assertTrue(result.accepted());
        assertEquals(false, result.replayed());
        assertEquals("dac-1", result.externalTaskId());
        ArgumentCaptor<DeviceOpsDispatchDO> inserted = ArgumentCaptor.forClass(DeviceOpsDispatchDO.class);
        verify(dispatchMapper).insert(inserted.capture());
        assertEquals("dac-1", inserted.getValue().getCollectionId());
        assertEquals("npdms", inserted.getValue().getNamespace());
        server.verify();
    }

    @Test
    void dispatchReplayReportsExistingCollection() {
        server.expect(requestTo(BASE + "/api/v1/collections"))
                .andRespond(withSuccess("{\"collectionId\":\"dac-1\",\"existing\":true}",
                        MediaType.APPLICATION_JSON));
        when(dispatchMapper.selectByPlatformTaskId("task-1")).thenReturn(dispatchRow("dac-1"));

        var result = gateway.dispatch(command("secret".toCharArray()));

        assertTrue(result.replayed());
        verify(dispatchMapper).updateById(any(DeviceOpsDispatchDO.class));
        server.verify();
    }

    @Test
    void dispatchExplicitRejectionPropagatesWithoutMapping() {
        server.expect(requestTo(BASE + "/api/v1/collections"))
                .andRespond(withStatus(BAD_REQUEST));

        assertThrows(RuntimeException.class, () -> gateway.dispatch(command("secret".toCharArray())));

        verify(dispatchMapper, never()).insert(any(DeviceOpsDispatchDO.class));
        server.verify();
    }

    @Test
    void dispatchTransportFailurePropagatesForReconciling() {
        server.expect(requestTo(BASE + "/api/v1/collections"))
                .andRespond(withException(new java.io.IOException("connect refused")));

        assertThrows(RuntimeException.class, () -> gateway.dispatch(command("secret".toCharArray())));

        verify(dispatchMapper, never()).insert(any(DeviceOpsDispatchDO.class));
        server.verify();
    }

    @Test
    void queryUsesPersistedMappingAndObservesStatus() {
        server.expect(requestTo(BASE + "/api/v1/collections/dac-1?namespace=npdms"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"collectionId\":\"dac-1\",\"status\":\"EXECUTING\"}",
                        MediaType.APPLICATION_JSON));
        DeviceOpsDispatchDO row = dispatchRow("dac-1");
        when(dispatchMapper.selectByPlatformTaskId("task-1")).thenReturn(row);

        var snapshot = gateway.query("task-1");

        assertEquals("dac-1", snapshot.externalTaskId());
        assertEquals("EXECUTING", snapshot.externalStatus());
        assertEquals("EXECUTING", row.getExternalStatus());
        server.verify();
    }

    @Test
    void queryRecoversMappingFromManagementListing() {
        server.expect(requestTo(BASE + "/api/v1/management/collections?namespace=npdms&page=0&size=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"total":1,"items":[{"collectionId":"dac-9","externalRequestId":"task-1",\
                        "status":"SUCCEEDED"}]}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/v1/collections/dac-9?namespace=npdms"))
                .andRespond(withSuccess("{\"collectionId\":\"dac-9\",\"status\":\"SUCCEEDED\"}",
                        MediaType.APPLICATION_JSON));
        when(dispatchMapper.selectByPlatformTaskId("task-1")).thenReturn(null);

        var snapshot = gateway.query("task-1");

        assertEquals("SUCCEEDED", snapshot.externalStatus());
        assertEquals("dac-9", snapshot.externalTaskId());
        ArgumentCaptor<DeviceOpsDispatchDO> inserted = ArgumentCaptor.forClass(DeviceOpsDispatchDO.class);
        verify(dispatchMapper).insert(inserted.capture());
        assertEquals("dac-9", inserted.getValue().getCollectionId());
        assertEquals("SUCCEEDED", inserted.getValue().getExternalStatus());
        server.verify();
    }

    @Test
    void queryWithoutAnyDacRecordReportsUnknown() {
        server.expect(requestTo(BASE + "/api/v1/management/collections?namespace=npdms&page=0&size=50"))
                .andRespond(withSuccess("{\"total\":0,\"items\":[]}", MediaType.APPLICATION_JSON));
        when(dispatchMapper.selectByPlatformTaskId("task-1")).thenReturn(null);

        var snapshot = gateway.query("task-1");

        assertEquals("UNKNOWN", snapshot.externalStatus());
        assertEquals("NOT_DISPATCHED", snapshot.failureCategory());
        verify(dispatchMapper, never()).insert(any(DeviceOpsDispatchDO.class));
        server.verify();
    }

    @Test
    void cancelRecordsIntentWithoutExternalCall() {
        assertDoesNotThrow(() -> gateway.cancel("task-1", "manual"));
        server.verify();
    }

    @Test
    void commandTimeoutCannotExceedThirtySeconds() {
        properties.setCommandTimeoutSeconds(120);
        server.expect(requestTo(BASE + "/api/v1/collections"))
                .andExpect(jsonPath("$.commandTimeoutSeconds").value(30))
                .andRespond(withSuccess("{\"collectionId\":\"dac-1\",\"existing\":false}",
                        MediaType.APPLICATION_JSON));
        when(dispatchMapper.selectByPlatformTaskId("task-1")).thenReturn(null);

        gateway.dispatch(command("secret".toCharArray()));

        server.verify();
    }

    private DeviceOpsDispatchCommand command(char[] secret) {
        return new DeviceOpsDispatchCommand("task-1", "batch-1", 0L, "project-1", "device-1",
                "Device", "10.0.0.1", 22, "SSH", "template-1", "v1", "a".repeat(64),
                List.of("show version", "display current"), "TEMPORARY_SECRET", null,
                "operator", secret, "DEVICE_OPS", "trace-1");
    }

    private DeviceOpsDispatchDO dispatchRow(String collectionId) {
        DeviceOpsDispatchDO row = new DeviceOpsDispatchDO();
        row.setId(1L);
        row.setTenantId(0L);
        row.setPlatformTaskId("task-1");
        row.setIdempotencyKey("task-1");
        row.setNamespace("npdms");
        row.setCollectionId(collectionId);
        row.setExternalStatus("ACCEPTED");
        return row;
    }

    private static String sha256(String content) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
