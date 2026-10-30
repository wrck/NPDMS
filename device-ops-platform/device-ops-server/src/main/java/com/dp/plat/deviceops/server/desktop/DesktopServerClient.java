package com.dp.plat.deviceops.server.desktop;

import com.dp.plat.deviceops.adapter.persistence.jdbc.JdbcDesktopTaskStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

/** Every upstream URL comes from the installed environment, never from a browser request. */
@Component
@Profile("desktop")
public class DesktopServerClient {
    private final DesktopProperties properties;
    private final RestClient http;
    private final ObjectMapper json = new ObjectMapper();

    public DesktopServerClient(DesktopProperties properties) {
        this.properties = properties;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    Grant resolve(Ticket ticket) {
        String raw = http.post().uri(root() + "/tickets/resolve").header("X-NPDMS-DAC-Ticket", ticket.value())
                .header("tenant-id", Long.toString(ticket.tenantId())).retrieve().body(String.class);
        Grant grant = decode(raw, Grant.class);
        if (grant == null || grant.tenantId() != ticket.tenantId() || grant.actorId() < 1 || grant.projectId() == null
                || grant.namespace() == null || grant.namespace().length() > 100
                || !Set.of("EXECUTE", "TASK_READ", "CONNECTIONS_READ", "CONNECTION_SAVE").contains(grant.operation()))
            throw new IllegalStateException("desktop authorization response invalid");
        return grant;
    }

    void claim(TaskAuthorization authorization) {
        confirm(post(authorization, "/claim", Map.of("executionId", authorization.task().executionId())).body(String.class));
    }

    Control observe(Observation observation) {
        var task = observation.authorization().task();
        String raw = post(observation.authorization(), "/observations", Map.of("executionId", task.executionId(),
                "collectionId", observation.collectionId(), "status", observation.status(), "redeliveryApplied", observation.redeliveryApplied()))
                .body(String.class);
        return decode(raw, Control.class);
    }

    Control control(TaskAuthorization authorization) {
        return decode(post(authorization, "/control", Map.of("executionId", authorization.task().executionId())).body(String.class), Control.class);
    }

    void cancelledBeforeSubmission(TaskAuthorization authorization) {
        confirm(post(authorization, "/cancelled-before-submission", Map.of("executionId", authorization.task().executionId())).body(String.class));
    }

    private void confirm(String raw) {
        Confirmation confirmation = decode(raw, Confirmation.class);
        if (confirmation == null || !confirmation.accepted()) throw new IllegalStateException("desktop operation was not accepted");
    }

    private RestClient.ResponseSpec post(TaskAuthorization authorization, String path, Object body) {
        return http.post().uri(root() + "/tasks/{taskId}" + path, authorization.task().platformTaskId())
                .header("tenant-id", Long.toString(authorization.task().tenantId()))
                .header("X-NPDMS-DAC-Task-Token", authorization.token()).contentType(MediaType.APPLICATION_JSON).body(body).retrieve();
    }

    String callbackUrl() { return root() + "/callbacks"; }
    RestClient callbackClient(String token) { return http.mutate().defaultHeader("X-NPDMS-DAC-Task-Token", token).build(); }
    private String root() { return properties.serverUrl() + "/api/v1/pms/integration/device-ops/desktop"; }
    private <T> T decode(String raw, Class<T> type) {
        try {
            var node = json.readTree(raw);
            if (node.has("code")) {
                if (node.path("code").asInt(-1) != 0 || !node.hasNonNull("data")) throw new IllegalStateException();
                node = node.get("data");
            }
            return json.treeToValue(node, type);
        } catch (Exception invalid) { throw new IllegalStateException("desktop server response invalid"); }
    }

    record Ticket(String value, long tenantId) { @Override public String toString() { return "DesktopTicket[redacted]"; } }
    record TaskAuthorization(JdbcDesktopTaskStore.Task task, String token) { @Override public String toString() { return "DesktopTaskAuthorization[redacted]"; } }
    record Observation(TaskAuthorization authorization, String collectionId, String status, boolean redeliveryApplied) { }
    record Control(boolean cancelRequested, boolean redeliveryRequested) { }
    record Confirmation(boolean accepted) { }
    public record Grant(long tenantId, long actorId, String projectId, String namespace, String operation, String platformTaskId,
                        Map<String, Object> submission, ExpectedConnection connection, String taskToken, ConnectionScope connectionScope) {
        @Override public String toString() { return "DesktopGrant[redacted]"; }
    }
    public record ConnectionScope(String deviceId, String protocol) { }
    public record ExpectedConnection(String host, Integer port, String protocol, String username, String savedConnectionId, Long savedConnectionVersion) { }
}
