package com.dp.deviceops.server;

import com.dp.deviceops.adapter.serial.SerialCommandExecutionAdapter;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.ProtocolCommandExecutionAdapter;
import com.dp.deviceops.core.service.ProtocolCommandExecutionRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerialWiringTest {

    @Test
    void routerRoutesSerialConnectionsToSerialAdapter() {
        SerialCommandExecutionAdapter serial = new SerialCommandExecutionAdapter(true, 8_388_608, 8_192, 10_000);
        ProtocolCommandExecutionRouter router = new ProtocolCommandExecutionRouter(List.of(serial));
        CommandExecutionPort.ConnectionSpec spec = new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.SERIAL, "COM3", 0, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD, CommandExecutionPort.ExecutionMode.SHELL,
                null, null, CommandExecutionPort.SerialParams.defaults(),
                CommandExecutionPort.SerialPrompts.defaults(), Duration.ofSeconds(5));
        assertSame(serial, routerAdapter(router, spec));
    }

    private ProtocolCommandExecutionAdapter routerAdapter(ProtocolCommandExecutionRouter router,
                                                          CommandExecutionPort.ConnectionSpec spec) {
        try {
            var field = ProtocolCommandExecutionRouter.class.getDeclaredField("adapters");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var adapters = (java.util.Map<ConnectionProtocol, ProtocolCommandExecutionAdapter>) field.get(router);
            return adapters.get(spec.protocol());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void serialPropertiesDefaultsAreEnabledWithTelnetSizedLimits() {
        SerialProperties properties = new SerialProperties();
        assertEquals(true, properties.isEnabled());
        assertEquals(8_388_608, properties.getMaxOutputBytes());
        assertEquals(8_192, properties.getEventChunkBytes());
        assertEquals(10_000, properties.getMaxPages());
    }

    @Test
    void serialPortsEndpointListsPortsWhenEnabledAndReturnsEmptyWhenDisabled() throws Exception {
        var json = new ObjectMapper();
        try (HttpClient http = HttpClient.newHttpClient()) {
            ConfigurableApplicationContext enabled = serverContext(true);
            try {
                String base = "http://127.0.0.1:" + ((WebServerApplicationContext) enabled).getWebServer().getPort();
                assertEquals(SerialCommandExecutionAdapter.class,
                        enabled.getBean(SerialCommandExecutionAdapter.class).getClass());
                HttpResponse<String> response = http.send(
                        HttpRequest.newBuilder(URI.create(base + "/api/v1/serial-ports")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertTrue(json.readTree(response.body()).path("ports").isArray(), response.body());
                assertTrue(json.readTree(response.body()).path("ports").size() >= 0);
                HttpResponse<String> runtimeConfig = http.send(
                        HttpRequest.newBuilder(URI.create(base + "/api/v1/runtime-config")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, runtimeConfig.statusCode());
                assertTrue(runtimeConfig.body().contains("\"serialEnabled\":true"), runtimeConfig.body());
            } finally {
                enabled.close();
            }
            ConfigurableApplicationContext disabled = serverContext(false);
            try {
                String base = "http://127.0.0.1:" + ((WebServerApplicationContext) disabled).getWebServer().getPort();
                HttpResponse<String> response = http.send(
                        HttpRequest.newBuilder(URI.create(base + "/api/v1/serial-ports")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertEquals(0, json.readTree(response.body()).path("ports").size(), response.body());
                HttpResponse<String> runtimeConfig = http.send(
                        HttpRequest.newBuilder(URI.create(base + "/api/v1/runtime-config")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, runtimeConfig.statusCode());
                assertTrue(runtimeConfig.body().contains("\"serialEnabled\":false"), runtimeConfig.body());
            } finally {
                disabled.close();
            }
        }
    }

    private ConfigurableApplicationContext serverContext(boolean serialEnabled) {
        return new SpringApplicationBuilder(DeviceOpsServerApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=jdbc:h2:mem:serial-wiring-" + UUID.randomUUID()
                                + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--device-ops.security.mode=local",
                        "--device-ops.runtime.auth-mode=local",
                        "--device-ops.serial.enabled=" + serialEnabled,
                        "--device-ops.executor.shutdown-await-seconds=1",
                        "--device-ops.executor.shutdown-graceful-period=500ms",
                        "--device-ops.executor.recovery-interval=50ms",
                        "--device-ops.executor.recovery-unclaimed-grace=1h");
    }
}
