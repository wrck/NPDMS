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
    @Test void onlyExplicitDurableCancellationIsTerminalProof() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-DAC-Cancellation", "BEFORE_DISPATCH");
        server.expect(requestTo(BASE + "/api/v1/npdms/collections/task-1?namespace=npdms-0"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.GONE).headers(headers));
        var result = gateway.query("task-1");
        assertEquals("CANCELLED", result.externalStatus());
        assertEquals("CANCELLED_BEFORE_DISPATCH", result.failureCategory());
        assertNull(result.externalTaskId());
        verify(dispatchMapper, never()).insert(any(DeviceOpsDispatchDO.class));
        server.verify();
    }

    @Test void unqualifiedGoneResponseIsNotCancellationProof() {
        server.expect(requestTo(BASE + "/api/v1/npdms/collections/task-1?namespace=npdms-0"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.GONE));
        assertThrows(org.springframework.web.client.HttpClientErrorException.Gone.class, () -> gateway.query("task-1"));
    }

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
        properties.setRequestSigningKey("test-only-request-grant-key-32-bytes");
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
        server.expect(requestTo(BASE + "/api/v1/npdms/collections"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "task-1"))
                .andExpect(jsonPath("$.namespace").value("npdms-0"))
                .andExpect(jsonPath("$.context.project.projectKey").value("project-1"))
                .andExpect(jsonPath("$.context.device.deviceKey").value("device-1"))
                .andExpect(jsonPath("$.connection.protocol").value("SSH2"))
                .andExpect(jsonPath("$.connection.executionMode").value("SHELL"))
                .andExpect(jsonPath("$.connection.authenticationType").value("PASSWORD"))
                .andExpect(jsonPath("$.connection.password").value("secret"))
                .andExpect(jsonPath("$.script.source").value("EXTERNAL_DELIVERED"))
                .andExpect(jsonPath("$.script.content").value("show version\ndisplay current"))
                .andExpect(jsonPath("$.script.sha256").value(sha256("show version\ndisplay current")))
                .andExpect(jsonPath("$.externalRequestId").value("task-1"))
                .andExpect(jsonPath("$.commandTimeoutSeconds").value(30))
                .andExpect(jsonPath("$.parseTimeoutSeconds").value(30))
                .andExpect(jsonPath("$.leaseGraceSeconds").value(0))
                .andExpect(jsonPath("$.connection.connectTimeoutSeconds").value(10))
                .andRespond(withSuccess("{\"collectionId\":\"dac-1\",\"existing\":false}",
                        MediaType.APPLICATION_JSON));
        when(dispatchMapper.selectByTenantAndPlatformTaskId(0L, "task-1")).thenReturn(null);

        var result = gateway.dispatch(command("secret".toCharArray()));

        assertTrue(result.accepted());
        assertEquals(false, result.replayed());
        assertEquals("dac-1", result.externalTaskId());
        ArgumentCaptor<DeviceOpsDispatchDO> inserted = ArgumentCaptor.forClass(DeviceOpsDispatchDO.class);
        verify(dispatchMapper).insert(inserted.capture());
        assertEquals("dac-1", inserted.getValue().getCollectionId());
        assertEquals("npdms-0", inserted.getValue().getNamespace());
        server.verify();
    }

    @Test
    void dispatchReplayReportsExistingCollection() {
        server.expect(requestTo(BASE + "/api/v1/npdms/collections"))
                .andRespond(withSuccess("{\"collectionId\":\"dac-1\",\"existing\":true}",
                        MediaType.APPLICATION_JSON));
        when(dispatchMapper.selectByTenantAndPlatformTaskId(0L, "task-1")).thenReturn(dispatchRow("dac-1"));

        var result = gateway.dispatch(command("secret".toCharArray()));

        assertTrue(result.replayed());
        verify(dispatchMapper).updateById(any(DeviceOpsDispatchDO.class));
        server.verify();
    }

    @Test
    void dispatchExplicitRejectionPropagatesWithoutMapping() {
        server.expect(requestTo(BASE + "/api/v1/npdms/collections"))
                .andRespond(withStatus(BAD_REQUEST));

        assertEquals(false, gateway.dispatch(command("secret".toCharArray())).accepted());

        verify(dispatchMapper, never()).insert(any(DeviceOpsDispatchDO.class));
        server.verify();
    }

    @Test
    void dispatchTransportFailurePropagatesForReconciling() {
        server.expect(requestTo(BASE + "/api/v1/npdms/collections"))
                .andRespond(withException(new java.io.IOException("connect refused")));
        server.expect(requestTo(BASE + "/api/v1/npdms/collections/task-1?namespace=npdms-0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new java.net.SocketTimeoutException("reconnect timeout")));

        assertThrows(RuntimeException.class, () -> gateway.dispatch(command("secret".toCharArray())));

        verify(dispatchMapper, never()).insert(any(DeviceOpsDispatchDO.class));
        server.verify();
    }

    @Test
    void queryUsesPersistedMappingAndObservesStatus() {
        server.expect(requestTo(BASE + "/api/v1/collections/dac-1?namespace=npdms-0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"collectionId\":\"dac-1\",\"namespace\":\"npdms-0\",\"externalRequestId\":\"task-1\",\"status\":\"EXECUTING\"}",
                        MediaType.APPLICATION_JSON));
        DeviceOpsDispatchDO row = dispatchRow("dac-1");
        when(dispatchMapper.selectByTenantAndPlatformTaskId(0L, "task-1")).thenReturn(row);

        var snapshot = gateway.query("task-1");

        assertEquals("dac-1", snapshot.externalTaskId());
        assertEquals("EXECUTING", snapshot.externalStatus());
        assertEquals("EXECUTING", row.getExternalStatus());
        server.verify();
    }

    @Test
    void queryRecoversMappingFromExactTaskEndpoint() {
        server.expect(requestTo(BASE + "/api/v1/npdms/collections/task-1?namespace=npdms-0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"namespace":"npdms-0","collectionId":"dac-9","externalRequestId":"task-1",\
                        "status":"SUCCEEDED"}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/v1/collections/dac-9?namespace=npdms-0"))
                .andRespond(withSuccess("{\"namespace\":\"npdms-0\",\"externalRequestId\":\"task-1\",\"collectionId\":\"dac-9\",\"status\":\"SUCCEEDED\"}",
                        MediaType.APPLICATION_JSON));
        when(dispatchMapper.selectByTenantAndPlatformTaskId(0L, "task-1")).thenReturn(null);

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
        server.expect(requestTo(BASE + "/api/v1/npdms/collections/task-1?namespace=npdms-0"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        when(dispatchMapper.selectByTenantAndPlatformTaskId(0L, "task-1")).thenReturn(null);

        var snapshot = gateway.query("task-1");

        assertEquals("UNKNOWN", snapshot.externalStatus());
        assertEquals("NOT_DISPATCHED", snapshot.failureCategory());
        verify(dispatchMapper, never()).insert(any(DeviceOpsDispatchDO.class));
        server.verify();
    }

    @Test
    void cancelRequestsExecutionStop() {
        server.expect(requestTo(BASE + "/api/v1/npdms/collections/task-1/cancellations?namespace=npdms-0"))
                .andExpect(method(HttpMethod.POST)).andRespond(withStatus(org.springframework.http.HttpStatus.ACCEPTED));
        assertDoesNotThrow(() -> gateway.cancel("task-1", "manual"));
        server.verify();
    }

    @Test
    void commandTimeoutCannotExceedThirtySeconds() {
        properties.setCommandTimeoutSeconds(120);
        assertThrows(IllegalArgumentException.class, () -> gateway.dispatch(command("secret".toCharArray())));
        server.verify();
    }

    @Test
    void acceptedExecutionWithFailedMappingRequiresReconciliationAndErasesSecret() {
        server.expect(requestTo(BASE + "/api/v1/npdms/collections"))
                .andRespond(withSuccess("{\"collectionId\":\"dac-1\",\"existing\":false}", MediaType.APPLICATION_JSON));
        when(dispatchMapper.insert(any(DeviceOpsDispatchDO.class))).thenThrow(new IllegalStateException("database unavailable"));
        char[] secret = "temporary-secret".toCharArray();
        var failure = assertThrows(IllegalStateException.class, () -> gateway.dispatch(command(secret)));
        assertTrue(failure.getCause() instanceof java.io.IOException);
        org.junit.jupiter.api.Assertions.assertArrayEquals(new char[secret.length], secret);
        server.verify();
    }

    @Test
    void timeoutReconnectRecoversAcceptedTaskWithoutSecondDispatch() {
        server.expect(requestTo(BASE + "/api/v1/npdms/collections"))
                .andRespond(withException(new java.net.SocketTimeoutException("response timeout")));
        server.expect(requestTo(BASE + "/api/v1/npdms/collections/task-1?namespace=npdms-0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"namespace\":\"npdms-0\",\"collectionId\":\"dac-1\",\"externalRequestId\":\"task-1\",\"status\":\"EXECUTING\"}", MediaType.APPLICATION_JSON));
        char[] secret = "secret".toCharArray();
        var result = gateway.dispatch(command(secret));
        assertTrue(result.accepted()); assertTrue(result.replayed());
        assertEquals("dac-1", result.externalTaskId());
        org.junit.jupiter.api.Assertions.assertArrayEquals(new char[secret.length], secret);
        server.verify();
    }

    @Test
    void manualConfigurationUsesItsOwnWholeScriptBudgetAndSignsTheSameValue() {
        properties.setManualExecutionTimeoutSeconds(600);
        server.expect(requestTo(BASE + "/api/v1/npdms/collections"))
                .andExpect(jsonPath("$.commandTimeoutSeconds").value(600))
                .andExpect(request -> {
                    String time = request.getHeaders().getFirst("X-DAC-Grant-Time");
                    String binding = java.util.stream.Stream.of(time, "npdms-0", "task-1", "project-1", "device-1",
                            "10.0.0.1", "22", "SSH2", "operator", "plt-template-1", "v1",
                            sha256("show version\ndisplay current"), "600", "")
                            .map(v -> v.length() + ":" + v).collect(java.util.stream.Collectors.joining());
                    try {
                        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
                        mac.init(new javax.crypto.spec.SecretKeySpec(properties.getRequestSigningKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                        assertEquals(java.util.HexFormat.of().formatHex(mac.doFinal(binding.getBytes(StandardCharsets.UTF_8))),
                                request.getHeaders().getFirst("X-DAC-Grant"));
                    } catch (java.security.GeneralSecurityException failure) { throw new AssertionError(failure); }
                })
                .andRespond(withSuccess("{\"collectionId\":\"dac-1\",\"existing\":false}", MediaType.APPLICATION_JSON));
        assertTrue(gateway.dispatch(command("secret".toCharArray(), "SSH", "IMP", "Configuration")).accepted());
        server.verify();
    }

    @Test
    void invalidManualBudgetRejectsBeforeHttpAndClearsCredential() {
        properties.setManualExecutionTimeoutSeconds(0);
        char[] secret = "secret".toCharArray();
        assertThrows(IllegalArgumentException.class,
                () -> gateway.dispatch(command(secret, "SSH", "IMP", "Configuration")));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new char[secret.length], secret);
        server.verify();
    }

    @Test
    void otherImplementationObjectsKeepTheOriginalTimeoutLimit() {
        properties.setCommandTimeoutSeconds(31);
        assertThrows(IllegalArgumentException.class,
                () -> gateway.dispatch(command("secret".toCharArray(), "SSH", "IMP", "Other")));
        server.verify();
    }

    @Test
    void tenantMismatchCannotDispatchAndStillErasesSecret() {
        TenantContextHolder.setTenantId(9L);
        char[] secret = "temporary-secret".toCharArray();
        assertThrows(IllegalArgumentException.class, () -> gateway.dispatch(command(secret)));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new char[secret.length], secret);
        server.verify();
    }

    @Test
    void malformedSuccessResponseRequiresReconciliation() {
        server.expect(requestTo(BASE + "/api/v1/npdms/collections"))
                .andRespond(withSuccess("invalid-json", MediaType.APPLICATION_JSON));
        char[] secret = "temporary-secret".toCharArray();
        var failure = assertThrows(IllegalStateException.class, () -> gateway.dispatch(command(secret)));
        assertTrue(failure.getCause() instanceof java.io.IOException);
        org.junit.jupiter.api.Assertions.assertArrayEquals(new char[secret.length], secret);
        verify(dispatchMapper, never()).insert(any(DeviceOpsDispatchDO.class));
        server.verify();
    }

    private DeviceOpsDispatchCommand command(char[] secret) {
        return command(secret, "SSH");
    }

    @Test
    void telnetSubmissionIncludesRequiredLoginPrompts() {
        server.expect(requestTo(BASE + "/api/v1/npdms/collections"))
                .andExpect(jsonPath("$.connection.protocol").value("TELNET"))
                .andExpect(jsonPath("$.connection.telnetPrompts.login").value("(?i)(login|username)\\s*:\\s*$"))
                .andExpect(jsonPath("$.connection.telnetPrompts.command").value("[>#\\$]\\s*$"))
                .andRespond(withSuccess("{\"collectionId\":\"dac-1\",\"existing\":false}", MediaType.APPLICATION_JSON));
        assertTrue(gateway.dispatch(command("secret".toCharArray(), "TELNET")).accepted());
        server.verify();
    }

    private DeviceOpsDispatchCommand command(char[] secret, String protocol) {
        return command(secret, protocol, "INS", "Inspection");
    }

    private DeviceOpsDispatchCommand command(char[] secret, String protocol, String context, String objectType) {
        return new DeviceOpsDispatchCommand("task-1", "batch-1", 0L, "project-1", "device-1",
                "Device", "10.0.0.1", 22, protocol, "template-1", "v1", sha256("show version\ndisplay current"),
                List.of("show version", "display current"), "TEMPORARY_SECRET", null,
                "operator", secret, "DEVICE_OPS", "trace-1", context, objectType);
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
