package com.dp.deviceops.server;

import com.dp.deviceops.parser.runtime.model.LogType;
import com.dp.deviceops.parser.runtime.model.ParserRelease;
import com.dp.deviceops.parser.runtime.model.ReleaseState;
import com.dp.deviceops.parser.runtime.port.ParserReleaseRepository;
import com.dp.deviceops.parser.runtime.service.ParserReleaseService;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundleCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExternalParserHttpTest {
    private static final String ISSUER = "https://test-issuer.invalid";
    private static final String SCOPES = "parser:task:create parser:task:read";
    private static final KeyPair KEYS = keys();
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private ConfigurableApplicationContext context;
    private String base;

    @BeforeAll
    void startServer() throws Exception {
        context = new SpringApplicationBuilder(DeviceOpsServerApplication.class, TestJwtConfiguration.class)
                .web(WebApplicationType.SERVLET)
                .run("--server.port=0", "--spring.datasource.url=jdbc:h2:mem:external-api-" + UUID.randomUUID(),
                        "--spring.datasource.username=sa", "--spring.datasource.password=",
                        "--device-ops.security.mode=oauth2", "--device-ops.runtime.auth-mode=oauth2",
                        "--device-ops.parser.max-input-bytes=1024",
                        "--device-ops.parser.worker.poll-interval=50ms",
                        "--device-ops.parser.worker.heartbeat-interval=50ms",
                        "--device-ops.executor.shutdown-await-seconds=1",
                        "--device-ops.executor.shutdown-graceful-period=500ms");
        base = "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
        var bundle = new ParserReleaseBundleCodec().decode(Path.of("../parser-releases/device-command-output-1.3.0"));
        var manifest = bundle.manifest();
        Instant now = Instant.now();
        context.getBean(ParserReleaseRepository.class).createLogType(new LogType(
                manifest.logType(), "Device output", "HTTP integration fixture", now, now));
        var service = context.getBean(ParserReleaseService.class);
        var coordinate = new ParserCoordinate(manifest.logType(), manifest.releaseVersion(), manifest.engineVersion(),
                manifest.ruleVersion(), manifest.projectionVersion(), null, null);
        service.saveDraft(new ParserRelease("http-release", manifest.logType(), manifest.releaseVersion(),
                ReleaseState.DRAFT, coordinate, 1, null, now, null), bundle);
        service.validate("http-release");
        service.publish("http-release");
        service.activate(manifest.logType(), "http-release", null, 0);
    }

    @AfterAll
    void stopServer() {
        if (context != null) {
            context.close();
        }
        http.close();
    }

    @Test
    void signedTokenSubmissionCanBeRetriedPolledAndReadWithoutAnotherResultLookup() throws Exception {
        String token = token("integration-a", SCOPES);
        Map<String, Object> body = submission("Serial Number: SN-HTTP-001\nSoftware Release TEST-1.2.3");
        var first = send("POST", "/api/v1/parse-tasks", token, "replay-http", body);
        assertStatus(202, first);
        String taskId = json.readTree(first.body()).path("taskId").asText();
        assertStatus(202, send("POST", "/api/v1/parse-tasks", token, "replay-http", body));
        var replay = send("POST", "/api/v1/parse-tasks", token, "replay-http", body);
        assertEquals(taskId, json.readTree(replay.body()).path("taskId").asText());
        JsonNode task = awaitSuccess(taskId, token);
        assertEquals("http-release", task.path("releaseId").asText());
        var result = send("GET", "/api/v1/parse-tasks/" + taskId + "/result", token, null, null);
        assertStatus(200, result);
        JsonNode envelope = json.readTree(result.body());
        assertEquals(taskId, envelope.path("taskId").asText());
        assertTrue(result.body().contains("SN-HTTP-001"), result.body());
        assertTrue(result.body().contains("genericContent"), result.body());
        assertStatus(200, send("GET", "/api/v1/parse-results/" + task.path("resultId").asText(), token, null, null));
        assertStatus(409, send("POST", "/api/v1/parse-tasks", token, "replay-http", submission("changed")));
    }

    @Test
    void tokenScopeAndNamespaceAreEnforcedIncludingTheNewResultRoute() throws Exception {
        assertStatus(401, send("GET", "/api/v1/parse-tasks", null, null, null));
        assertStatus(401, send("GET", "/api/v1/parse-tasks", "invalid-token", null, null));
        assertStatus(403, send("POST", "/api/v1/parse-tasks", token("reader", "parser:task:read"),
                "forbidden", submission("hello")));
        assertStatus(403, send("GET", "/api/v1/parse-tasks/unknown/result", token("writer", "parser:task:create"), null, null));
        String owner = token("owner", SCOPES);
        var submitted = send("POST", "/api/v1/parse-tasks", owner, "private-task", submission("private output"));
        assertStatus(202, submitted);
        String taskId = json.readTree(submitted.body()).path("taskId").asText();
        JsonNode task = awaitSuccess(taskId, owner);
        String other = token("other-client", SCOPES);
        assertStatus(404, send("GET", "/api/v1/parse-tasks/" + taskId, other, null, null));
        assertStatus(404, send("GET", "/api/v1/parse-tasks/" + taskId + "/result", other, null, null));
        assertStatus(404, send("GET", "/api/v1/parse-results/" + task.path("resultId").asText(), other, null, null));
        Map<String, Object> reference = Map.of("logType", "device-command-output", "inputFormat", "command-output-block/v1",
                "inputRef", task.path("inputRef").asText());
        var forbiddenInput = send("POST", "/api/v1/parse-tasks", other, "foreign-input", reference);
        assertStatus(404, forbiddenInput);
        assertEquals("INPUT_NOT_FOUND", json.readTree(forbiddenInput.body()).path("code").asText());
        assertStatus(202, send("POST", "/api/v1/parse-tasks", owner, "owned-input", reference));
        assertStatus(403, send("GET", "/api/v1/parse-tasks", token(null, SCOPES), null, null));
    }

    @Test
    void invalidRequestsHaveStableErrorsAndOversizedInputNeverCreatesATask() throws Exception {
        String token = token("validation", SCOPES);
        var missingHeader = send("POST", "/api/v1/parse-tasks", token, null, submission("small"));
        assertStatus(400, missingHeader);
        assertEquals("INVALID_REQUEST", json.readTree(missingHeader.body()).path("code").asText());
        assertStatus(400, send("POST", "/api/v1/parse-tasks", token, " ", submission("small")));
        assertStatus(400, send("POST", "/api/v1/parse-tasks", token, "x".repeat(201), submission("small")));
        assertStatus(400, send("GET", "/api/v1/parse-tasks?limit=0", token, null, null));
        assertStatus(400, send("GET", "/api/v1/parse-tasks?limit=not-a-number", token, null, null));
        var malformed = http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/parse-tasks"))
                .header("Authorization", "Bearer " + token).header("Idempotency-Key", "malformed")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{broken-json")).build(), HttpResponse.BodyHandlers.ofString());
        assertStatus(400, malformed);
        assertEquals("INVALID_REQUEST", json.readTree(malformed.body()).path("code").asText());
        assertStatus(400, send("POST", "/api/v1/parse-tasks", token, "missing-type", Map.of(
                "inputFormat", "command-output-block/v1", "inputContent", "{}")));
        assertStatus(400, send("POST", "/api/v1/parse-tasks", token, "both-inputs", Map.of(
                "logType", "device-command-output", "inputFormat", "command-output-block/v1",
                "inputRef", "payload", "inputContent", "{}")));
        var oversized = send("POST", "/api/v1/parse-tasks", token, "oversized", submission("中".repeat(400)));
        assertStatus(413, oversized);
        assertEquals("INPUT_TOO_LARGE", json.readTree(oversized.body()).path("code").asText());
        var list = send("GET", "/api/v1/parse-tasks", token, null, null);
        assertStatus(200, list);
        assertEquals(0, json.readTree(list.body()).size());
    }

    @Test
    void externalOpenApiIsPublicAndDocumentsBearerRatherThanJwtArguments() throws Exception {
        var response = send("GET", "/v3/api-docs/external-parser", null, null, null);
        assertStatus(200, response);
        JsonNode document = json.readTree(response.body());
        assertTrue(document.path("components").path("securitySchemes").toString().contains("bearer"));
        JsonNode paths = document.path("paths");
        assertTrue(paths.has("/api/v1/parse-tasks/{taskId}/result"));
        assertFalse(paths.has("/api/v1/parser-log-types"));
        assertTrue(paths.path("/api/v1/parse-tasks").path("post").path("responses").has("202"));
        assertFalse(paths.path("/api/v1/parse-tasks").path("post").path("parameters").toString().contains("\"jwt\""));
    }

    private JsonNode awaitSuccess(String taskId, String token) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        JsonNode task;
        do {
            var response = send("GET", "/api/v1/parse-tasks/" + taskId, token, null, null);
            assertStatus(200, response);
            task = json.readTree(response.body());
            if ("SUCCEEDED".equals(task.path("state").asText())) {
                return task;
            }
            assertNotEquals("FAILED", task.path("state").asText(), task.toString());
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        fail("Task did not succeed: " + task);
        return task;
    }

    private Map<String, Object> submission(String stdout) throws Exception {
        String input = json.writeValueAsString(Map.of("schemaVersion", "1.0.0", "commandBlocks", List.of(Map.of(
                "commandIndex", 1, "commandText", "show version", "status", "SUCCEEDED", "stdout", stdout,
                "stderr", "", "receivedBytes", stdout.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                "pageCount", 1, "truncated", false, "exitCode", 0))));
        return Map.of("logType", "device-command-output", "inputFormat", "command-output-block/v1",
                "inputContent", input, "mediaType", "application/json", "contextSnapshot", Map.of("deviceModel", "generic"));
    }

    private HttpResponse<String> send(String method, String path, String token, String key, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (key != null) request.header("Idempotency-Key", key);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String token(String namespace, String scopes) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issuer(ISSUER)
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300))).claim("scope", scopes);
        if (namespace != null) claims.subject("service-" + namespace).claim("client_namespace", namespace);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), claims.build());
        jwt.sign(new RSASSASigner(KEYS.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair keys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void assertStatus(int expected, HttpResponse<String> response) {
        assertEquals(expected, response.statusCode(), response.body());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestJwtConfiguration {
        @Bean
        JwtDecoder externalTestJwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder;
        }
    }
}
