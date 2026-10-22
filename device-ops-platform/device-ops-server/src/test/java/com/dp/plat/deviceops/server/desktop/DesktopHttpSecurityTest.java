package com.dp.plat.deviceops.server.desktop;

import com.dp.plat.deviceops.server.DeviceOpsServerApplication;
import com.dp.plat.deviceops.core.port.CommandExecutionPort;
import com.dp.plat.deviceops.core.model.CommandOutputBlock;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Full desktop wiring and migrations, using mock HTTP and a new in-memory database, without a service port. */
@SpringBootTest(classes=DeviceOpsServerApplication.class, webEnvironment=SpringBootTest.WebEnvironment.MOCK, properties={
        "spring.datasource.url=jdbc:h2:mem:desktop-http-security;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "device-ops.desktop.server-url=http://10.210.0.11:59191", "device-ops.desktop.web-origin=http://10.210.0.11:19191",
        "device-ops.credentials.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "device-ops.desktop.parser-release-directory=../parser-releases/device-command-output-1.4.0",
        "device-ops.callback.poll-interval=1h", "device-ops.executor.shutdown-await-seconds=1",
        "device-ops.executor.shutdown-graceful-period=500ms", "device-ops.parser.worker.shutdown-grace=1s"
})
@ActiveProfiles("desktop")
class DesktopHttpSecurityTest {
    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    @MockitoBean DesktopServerClient server;
    @MockitoBean(name="commandExecutionPort") CommandExecutionPort commands;
    @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
    @Autowired DesktopSynchronization synchronization;
    @Autowired DesktopParserInitialization parserInitialization;
    @Autowired com.dp.plat.deviceops.parser.runtime.port.ParserReleaseRepository releases;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new DesktopDiscoverySecurity.LoopbackHostFilter(), security).build();
    }

    @Test void localStatusPageIsReadOnlyAndRetainsHostAndTicketProtection() throws Exception {
        mvc.perform(get("/desktop-status"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("NPDMS DAC 本机状态")));
        mvc.perform(get("/desktop-status").with(request -> { request.setServerName("attacker.example"); return request; }))
                .andExpect(status().isForbidden());
        mvc.perform(post("/desktop-status")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/desktop/saved-connections")).andExpect(status().isForbidden());
        mvc.perform(get("/")).andExpect(status().isForbidden());
        verify(server, never()).resolve(any());
    }

    @Test void exactOriginCanDiscoverAndPreflightWhileOtherSitesAndGenericRoutesAreDenied() throws Exception {
        mvc.perform(get("/api/v1/desktop/status").header("Origin", "http://10.210.0.11:19191"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://10.210.0.11:19191"))
                .andExpect(jsonPath("$.product").value("npdms-dac"));
        mvc.perform(options("/api/v1/desktop/tasks/task-1/executions").header("Origin", "http://10.210.0.11:19191")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Private-Network", "true")
                .header("Access-Control-Request-Headers", "content-type,x-npdms-dac-ticket,tenant-id"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Private-Network", "true"));
        mvc.perform(get("/api/v1/desktop/status").header("Origin", "https://other.example")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/collections").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verify(server, never()).resolve(any());
    }

    @Test void readCapabilityCannotExecuteAndValidConnectionReadUsesOwnerScopedStorage() throws Exception {
        when(server.resolve(any())).thenReturn(new DesktopServerClient.Grant(1, 42, "7", "npdms-1", "CONNECTIONS_READ", null, null, null, null));
        mvc.perform(get("/api/v1/desktop/saved-connections").header("Origin", "http://10.210.0.11:19191")
                .header("X-NPDMS-DAC-Ticket", "test-ticket").header("tenant-id", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(post("/api/v1/desktop/tasks/task-1/executions").header("Origin", "http://10.210.0.11:19191")
                .header("X-NPDMS-DAC-Ticket", "test-ticket").header("tenant-id", "1")
                .contentType("application/json").content("{\"password\":\"fixture-secret\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void authorizedCommandFinishesOfflineAndOriginalOutboxIsUploadedOnceAfterReconnect() throws Exception {
        String taskId = "task-" + UUID.randomUUID();
        String callback = "http://10.210.0.11:59191/api/v1/pms/integration/device-ops/desktop/callbacks";
        String commandText = "show version";
        String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(commandText.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Map<String,Object> submission = Map.of(
                "namespace", "npdms-1", "externalRequestId", taskId,
                "context", Map.of("project", Map.of("namespace", "npdms-1", "projectKey", "7"), "device", Map.of("deviceKey", "9", "deviceName", "fixture")),
                "connection", Map.of("host", "192.0.2.10", "port", 22, "protocol", "SSH2", "username", "operator", "authenticationType", "PASSWORD", "executionMode", "SHELL", "connectTimeoutSeconds", 5),
                "script", Map.of("source", "EXTERNAL_DELIVERED", "key", "fixture", "version", "1", "content", commandText, "sha256", hash, "policy", "EXECUTION_ONLY", "parserType", "NONE"),
                "commandTimeoutSeconds", 30, "parseTimeoutSeconds", 30, "leaseGraceSeconds", 0);
        when(server.callbackUrl()).thenReturn(callback);
        when(server.resolve(any())).thenReturn(new DesktopServerClient.Grant(1, 42, "7", "npdms-1", "EXECUTE", taskId,
                submission, new DesktopServerClient.ExpectedConnection("192.0.2.10", 22, "SSH2", "operator", null, null), "fixture-task-token-01234567890123456789"));
        when(commands.execute(any(), any(), any(), eq(commandText), any(), any())).thenReturn(
                new CommandExecutionPort.CommandResult(0, "version fixture", "", false, false, 1,
                        List.of(CommandOutputBlock.legacy("version fixture", "", 0, false, Map.of(), null))));
        when(server.observe(any())).thenThrow(new org.springframework.web.client.ResourceAccessException("offline"));
        String body = mvc.perform(post("/api/v1/desktop/tasks/" + taskId + "/executions").header("Origin", "http://10.210.0.11:19191")
                .header("X-NPDMS-DAC-Ticket", "fixture-ticket").header("tenant-id", "1")
                .contentType("application/json").content("{\"password\":\"local-only-fixture-secret\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String collectionId = new ObjectMapper().readTree(body).path("collectionId").asText();
        assertFalse(collectionId.isBlank());
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (jdbc.sql("SELECT COUNT(*) FROM device_ops_outbox WHERE aggregate_id=:id").param("id", collectionId).query(Integer.class).single() == 0
                && System.nanoTime() < deadline) Thread.sleep(20);
        String eventId = jdbc.sql("SELECT event_id FROM device_ops_outbox WHERE aggregate_id=:id").param("id", collectionId).query(String.class).single();
        String payload = jdbc.sql("SELECT payload FROM device_ops_outbox WHERE event_id=:id").param("id", eventId).query(String.class).single();
        assertEquals("SUCCEEDED", new ObjectMapper().readTree(payload).path("status").asText());
        assertFalse(payload.contains("local-only-fixture-secret"));
        synchronization.synchronize();
        assertEquals(1, jdbc.sql("SELECT attempt_count FROM device_ops_outbox WHERE event_id=:id").param("id", eventId).query(Integer.class).single());
        var builder = org.springframework.web.client.RestClient.builder();
        var upstream = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        upstream.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(callback))
                .andExpect(request -> {
                    String callbackId = request.getHeaders().getFirst("X-DAC-Nonce");
                    assertNotNull(callbackId);
                    assertTrue(callbackId.matches("[a-f0-9]{64}"));
                    assertNotEquals(eventId, callbackId);
                    assertTrue(((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString().contains(payload));
                })
                .andRespond(request -> org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"status\":\"ACKNOWLEDGED\",\"callbackId\":\"" + request.getHeaders().getFirst("X-DAC-Nonce")
                                + "\",\"receiptId\":12}", org.springframework.http.MediaType.APPLICATION_JSON).createResponse(request));
        when(server.callbackClient(anyString())).thenReturn(builder.build());
        doReturn(new DesktopServerClient.Control(false, false)).when(server).observe(any());
        jdbc.sql("UPDATE device_ops_outbox SET next_attempt_at=CURRENT_TIMESTAMP WHERE event_id=:id").param("id", eventId).update();
        synchronization.synchronize();
        upstream.verify();
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM device_ops_outbox WHERE event_id=:id AND delivered_at IS NOT NULL").param("id", eventId).query(Integer.class).single());
        assertEquals(payload, jdbc.sql("SELECT payload FROM device_ops_outbox WHERE event_id=:id").param("id", eventId).query(String.class).single());
        verify(commands, times(1)).execute(any(), any(), any(), eq(commandText), any(), any());
    }

    @Test void freshInstallationPublishesVerifiedParserAndRestartPreservesExistingSelection() throws Exception {
        var active = releases.findActive("device-command-output").orElseThrow();
        assertEquals("1.4.0", active.releaseVersion());
        assertTrue(active.validation().passed());
        parserInitialization.run(null);
        assertEquals(active, releases.findActive("device-command-output").orElseThrow());
        assertEquals(1, releases.listReleases("device-command-output").size());
    }
}
