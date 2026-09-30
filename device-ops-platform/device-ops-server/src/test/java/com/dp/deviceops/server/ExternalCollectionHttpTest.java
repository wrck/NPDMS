package com.dp.deviceops.server;

import com.dp.deviceops.core.port.RemoteEndpointPolicy;
import com.dp.deviceops.parser.runtime.model.LogType;
import com.dp.deviceops.parser.runtime.model.ParserRelease;
import com.dp.deviceops.parser.runtime.model.ReleaseState;
import com.dp.deviceops.parser.runtime.port.ParserReleaseRepository;
import com.dp.deviceops.parser.runtime.service.ParserReleaseService;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundleCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP, signature validation, JDBC persistence, and SSH execution; no mocked collection boundary. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExternalCollectionHttpTest {
    private static final String ISSUER = "https://collection-test-issuer.invalid";
    private static final String READ = "device-ops:collections:read";
    private static final String EXECUTE = "device-ops:collections:execute";
    private static final String SCOPES = READ + " " + EXECUTE;
    private static final String PROJECT = "http-project";
    private static final String OTHER_PROJECT = "other-http-project";
    private static final String USERNAME = "collection-fixture-user";
    private static final String PASSWORD = "collection-fixture-password";
    private static final KeyPair JWT_KEYS = keys();
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private ConfigurableApplicationContext context;
    private SshFixture ssh;
    private String base;

    @BeforeAll
    void startRealServerAndActivateAutomaticParser() throws Exception {
        context = new SpringApplicationBuilder(DeviceOpsServerApplication.class, CollectionHttpFixtures.class)
                .web(WebApplicationType.SERVLET)
                .run("--server.port=0", "--spring.datasource.url=jdbc:h2:mem:external-collection-" + UUID.randomUUID(),
                        "--spring.datasource.username=sa", "--spring.datasource.password=",
                        "--device-ops.security.mode=oauth2", "--device-ops.runtime.auth-mode=oauth2",
                        "--device-ops.parser.automatic-parsing-enabled=true",
                        "--device-ops.parser.worker.poll-interval=50ms",
                        "--device-ops.parser.worker.heartbeat-interval=50ms",
                        "--device-ops.parser.worker.shutdown-grace=1s",
                        "--device-ops.executor.shutdown-await-seconds=1",
                        "--device-ops.executor.shutdown-graceful-period=500ms",
                        "--device-ops.output-stream.shutdown-await-seconds=1",
                        "--spring.lifecycle.timeout-per-shutdown-phase=3s");
        ssh = context.getBean(SshFixture.class);
        base = "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
        var bundle = new ParserReleaseBundleCodec().decode(Path.of("../parser-releases/device-command-output-1.3.0"));
        var manifest = bundle.manifest();
        Instant now = Instant.now();
        context.getBean(ParserReleaseRepository.class).createLogType(new LogType(
                manifest.logType(), "Collection output", "Real SSH HTTP fixture", now, now));
        var service = context.getBean(ParserReleaseService.class);
        var coordinate = new ParserCoordinate(manifest.logType(), manifest.releaseVersion(), manifest.engineVersion(),
                manifest.ruleVersion(), manifest.projectionVersion(), null, null);
        service.saveDraft(new ParserRelease("collection-http-release", manifest.logType(), manifest.releaseVersion(),
                ReleaseState.DRAFT, coordinate, 1, null, now, null), bundle);
        service.validate("collection-http-release");
        service.publish("collection-http-release");
        service.activate(manifest.logType(), "collection-http-release", null, 0);
    }

    @AfterAll
    void closeServerSshFixtureAndHttpClient() {
        try {
            if (context != null) context.close();
        } finally {
            http.shutdownNow();
        }
    }

    @Test
    void duplicateFieldsAndMalformedSecretBodyReturnSanitizedBadRequest() throws Exception {
        String namespace = unique("invalid-json");
        for (String body : List.of("{\"namespace\":\"one\",\"namespace\":\"two\"}", "{\"connection\":{\"password\":\"SECRET_SENTINEL")) {
            var request = HttpRequest.newBuilder(URI.create(base + submitPath(false)))
                    .header("Authorization", "Bearer " + owner(namespace)).header("Idempotency-Key", unique("invalid"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            assertStatus(400, response);
            assertFalse(response.body().contains("SECRET_SENTINEL"));
        }
    }

    @Test
    void oversizedCollectionBodyIsRejectedBeforeExecution() throws Exception {
        String namespace = unique("oversize");
        ObjectNode body = submission(namespace, false);
        ((ObjectNode) body.path("script")).put("content", "x".repeat(4 * 1024 * 1024));
        assertStatus(413, send("POST", submitPath(false), owner(namespace), unique("large"), body));
    }

    @ParameterizedTest(name = "direct SSH collection succeeds and replays; project route = {0}")
    @ValueSource(booleans = {false, true})
    void signedSubmissionExecutesOncePollsOutputAndReplaysSseWithSemanticOptOut(boolean project) throws Exception {
        String namespace = unique("success");
        String token = owner(namespace);
        ObjectNode body = submission(namespace, project);
        String originalJson = json.writeValueAsString(body);
        String path = submitPath(project);
        String key = unique("replay");
        HttpResponse<String> first = send("POST", path, token, key, body);
        String id = acceptedId(first, false);
        JsonNode details = awaitSuccess(readPath(project, id), namespace, token);
        assertOutput(details, body);
        assertEquals(namespace, details.path("namespace").asText());
        if (project) assertEquals(PROJECT, details.path("projectKey").asText());
        assertExecutedOnce(body);

        List<SseEvent> events = stream(readPath(project, id) + "/output-events", namespace, token, 0);
        List<SseEvent> outputs = events.stream().filter(event -> "output".equals(event.name())).toList();
        assertTrue(outputs.size() >= 2, events.toString());
        assertEquals(expectedOutput(body), outputs.stream().map(event -> event.data().path("content").asText())
                .reduce("", String::concat));
        long previous = 0;
        for (SseEvent output : outputs) {
            assertTrue(output.id() > previous, "SSE IDs must be strictly increasing");
            assertEquals(output.id(), output.data().path("sequence").asLong());
            previous = output.id();
        }
        assertComplete(events, previous);
        long cursor = outputs.getFirst().id();
        List<SseEvent> resumed = stream(readPath(project, id) + "/output-events", namespace, token, cursor);
        assertEquals(outputs.stream().filter(event -> event.id() > cursor).toList(), resumed.stream()
                .filter(event -> "output".equals(event.name())).toList());
        assertComplete(resumed, previous);
        List<SseEvent> drained = stream(readPath(project, id) + "/output-events", namespace, token, previous);
        assertEquals(List.of("complete"), drained.stream().map(SseEvent::name).toList());
        assertComplete(drained, previous);
        assertExecutedOnce(body);

        var semantic = send("GET", genericPath(id) + "/semantic-results?namespace=" + namespace, token, null, null);
        assertStatus(200, semantic);
        assertEquals(json.createArrayNode(), json.readTree(semantic.body()),
                "Explicit opt-out must win even with automatic parsing and an active release");
        assertFalse(details.toString().contains(PASSWORD), "Evidence must not disclose transient credentials");
        assertFalse(details.path("script").has("content"), "Evidence must not disclose script content");

        var evidenceResponse = send("GET", readPath(project, id) + "/evidence?namespace=" + namespace, token, null, null);
        assertStatus(200, evidenceResponse);
        assertEquals("no-store", evidenceResponse.headers().firstValue("Cache-Control").orElseThrow());
        var evidence = json.readTree(evidenceResponse.body());
        assertEquals(body.path("script").path("content"), evidence.path("input").path("content"));
        assertEquals("CAPTURED_SUBMISSION", evidence.path("submission").path("provenance").asText());
        var captured = evidence.path("submission").path("snapshot");
        assertEquals(path, captured.path("request").path("path").asText());
        assertEquals(key, captured.path("request").path("idempotencyKey").asText());
        assertEquals(body.path("semanticParsing"), captured.path("body").path("semanticParsing"));
        assertEquals(body.path("commandTimeoutSeconds"), captured.path("body").path("commandTimeoutSeconds"));
        assertFalse(evidenceResponse.body().contains(PASSWORD));
        assertFalse(captured.path("body").path("script").has("content"));
        assertEquals(id, acceptedId(send("POST", path, token, key, body), true));
        ObjectNode credentialFreeReplay = body.deepCopy();
        ObjectNode replayConnection = (ObjectNode) (project
                ? credentialFreeReplay.path("targets").get(0) : credentialFreeReplay.path("connection"));
        replayConnection.remove("password");
        assertEquals(id, acceptedId(send("POST", path, token, key, credentialFreeReplay), true));
        var replayEvidence = json.readTree(send("GET", readPath(project, id) + "/evidence?namespace=" + namespace, token, null, null).body());
        assertEquals(evidence.path("submission"), replayEvidence.path("submission"), "Replay must not replace captured winner body");
        assertEquals(originalJson, json.writeValueAsString(body), "Only the server may clear its credential copy");
        assertEquals(events, stream(readPath(project, id) + "/output-events", namespace, token, 0),
                "Submission replay must retain the original persisted output event stream");
        assertExecutedOnce(body);
    }

    @Test
    void legacyGenericNestedConnectionShapeWorksWithoutSemanticSelectionOrProjectContext() throws Exception {
        String namespace = unique("legacy");
        String token = owner(namespace);
        ObjectNode body = submission(namespace, false);
        body.remove("semanticParsing");
        assertFalse(body.has("targets"));
        assertFalse(body.has("context"));
        String id = acceptedId(send("POST", submitPath(false), token, unique("legacy"), body), false);
        assertOutput(awaitSuccess(genericPath(id), namespace, token), body);
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        JsonNode results = json.createArrayNode();
        do {
            var response = send("GET", genericPath(id) + "/semantic-results?namespace=" + namespace, token, null, null);
            assertStatus(200, response);
            results = json.readTree(response.body());
            if (!results.isEmpty() && "SUCCEEDED".equals(results.get(0).path("state").asText())) break;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        assertEquals(1, results.size(), results.toString());
        assertEquals("SUCCEEDED", results.get(0).path("state").asText(), results.toString());
        assertEquals("collection-http-release", results.get(0).path("releaseId").asText());
        assertExecutedOnce(body);
    }

    @Test
    void collectionDerivedParseTasksCarryAndQueryTheInitiatingBusinessRequest() throws Exception {
        String namespace = unique("business-ref");
        String token = owner(namespace);
        ObjectNode body = submission(namespace, false);
        body.remove("semanticParsing");
        String platformTaskId = unique("npdms-task");
        body.put("externalRequestId", platformTaskId).put("activityType", "HTTP_COLLECTION");
        String id = acceptedId(send("POST", submitPath(false), token, platformTaskId, body), false);
        awaitSuccess(genericPath(id), namespace, token);

        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        JsonNode results = json.createArrayNode();
        do {
            var response = send("GET", "/api/v1/npdms/collections/" + platformTaskId
                    + "/semantic-results?namespace=" + namespace, token, null, null);
            assertStatus(200, response);
            results = json.readTree(response.body());
            if (!results.isEmpty() && "SUCCEEDED".equals(results.get(0).path("state").asText())) break;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        assertEquals(1, results.size(), results.toString());
        assertEquals("collection-http-release", results.get(0).path("releaseId").asText());
        assertEquals(platformTaskId, results.get(0).path("result").path("contextSnapshot")
                .path("externalRequestId").asText(), results.toString());
        assertEquals("HTTP_COLLECTION", results.get(0).path("result").path("contextSnapshot")
                .path("activityType").asText(), results.toString());

        String parserToken = signedToken("parser-client", "parser:task:read",
                Map.of("client_namespace", namespace), JWT_KEYS);
        var filtered = send("GET", "/api/v1/parse-tasks?externalRequestId=" + platformTaskId, parserToken, null, null);
        assertStatus(200, filtered);
        JsonNode tasks = json.readTree(filtered.body());
        assertEquals(1, tasks.size(), filtered.body());
        assertEquals(platformTaskId, tasks.get(0).path("externalRequestId").asText(), filtered.body());
        assertEquals("HTTP_COLLECTION", tasks.get(0).path("activityType").asText(), filtered.body());
        assertExecutedOnce(body);
    }

    @ParameterizedTest(name = "idempotency conflicts; project route = {0}")
    @ValueSource(booleans = {false, true})
    void changedScriptBodyOrSubjectCannotReuseAnIdempotencyKey(boolean project) throws Exception {
        String namespace = unique("conflict");
        String token = owner(namespace);
        ObjectNode body = submission(namespace, project);
        String key = unique("conflict");
        String id = acceptedId(send("POST", submitPath(project), token, key, body), false);
        awaitSuccess(readPath(project, id), namespace, token);
        ObjectNode changedScript = body.deepCopy();
        String replacement = body.path("script").path("content").asText() + "\nchanged-command";
        ((ObjectNode) changedScript.get("script")).put("content", replacement).put("sha256", sha256(replacement));
        ObjectNode changedBody = body.deepCopy().put("commandTimeoutSeconds", 11);
        String otherSubject = token("other-service", SCOPES, Map.of("device_ops_namespaces", List.of(namespace),
                "device_ops_projects", List.of(PROJECT)));
        assertAll(
                () -> assertStatus(409, send("POST", submitPath(project), token, key, changedScript)),
                () -> assertStatus(409, send("POST", submitPath(project), token, key, changedBody)),
                () -> assertStatus(409, send("POST", submitPath(project), otherSubject, key, body)));
        assertEquals(id, acceptedId(send("POST", submitPath(project), token, key, body), true));
        assertExecutedOnce(body);
        assertEquals(0, ssh.executions("changed-command"));
    }

    @Test
    void authenticationAndScopesProtectEveryCollectionReadAndSubmitRoute() throws Exception {
        String namespace = unique("scopes");
        String reader = token("reader", READ, claims(namespace, List.of(PROJECT)));
        String writer = token("writer", EXECUTE, claims(namespace, List.of(PROJECT)));
        for (boolean project : List.of(false, true)) {
            ObjectNode body = submission(namespace, project);
            assertAll(
                    () -> assertStatus(401, send("POST", submitPath(project), null, "missing", body)),
                    () -> assertStatus(401, send("POST", submitPath(project), "invalid-token", "invalid", body)),
                    () -> assertStatus(403, send("POST", submitPath(project), reader, "reader", body)));
        }
        String forged = signedToken("forged", SCOPES, claims(namespace, List.of(PROJECT)), keys());
        for (String path : allReadPaths("unknown")) {
            String query = path + "?namespace=" + namespace;
            assertAll(path,
                    () -> assertStatus(401, send("GET", query, null, null, null)),
                    () -> assertStatus(401, send("GET", query, "invalid-token", null, null)),
                    () -> assertStatus(401, send("GET", query, forged, null, null)),
                    () -> assertStatus(403, send("GET", query, writer, null, null)));
        }
    }

    @Test
    void namespaceAuthorizationProtectsBothSubmitRoutesAndAllStoredCollectionReads() throws Exception {
        String namespace = unique("namespace");
        String token = owner(namespace);
        String id = acceptedId(send("POST", submitPath(true), token, unique("owned"), submission(namespace, true)), false);
        awaitSuccess(readPath(true, id), namespace, token);
        String outsider = owner(unique("foreign"));
        for (boolean project : List.of(false, true)) {
            assertStatus(403, send("POST", submitPath(project), outsider, unique("denied"), submission(namespace, project)));
        }
        for (String path : allReadPaths(id)) {
            assertStatus(403, send("GET", path + "?namespace=" + namespace, outsider, null, null));
        }
    }

    @Test
    void trustedNamespaceClaimsHaveNoMissingClaimOrImplicitWildcardBypassAndRequireSubject() throws Exception {
        String namespace = unique("claims");
        String path = genericPath("unknown") + "?namespace=" + namespace;
        assertAll(
                () -> assertStatus(404, send("GET", path, token("service", SCOPES,
                        Map.of("device_ops_namespaces", List.of(namespace))), null, null)),
                () -> assertStatus(404, send("GET", path, token("service", SCOPES,
                        Map.of("client_namespace", namespace)), null, null)),
                () -> assertStatus(404, send("GET", path, token(namespace, SCOPES, Map.of()), null, null)),
                () -> assertStatus(403, send("GET", path, token("another-subject", SCOPES, Map.of()), null, null)),
                () -> assertStatus(403, send("GET", path, token(namespace, SCOPES,
                        Map.of("device_ops_namespaces", List.of())), null, null)),
                () -> assertStatus(403, send("GET", path, token(namespace, SCOPES,
                        Map.of("device_ops_namespaces", "*")), null, null)),
                () -> assertStatus(403, send("GET", path, token("service", SCOPES,
                        Map.of("client_namespace", "*")), null, null)),
                () -> assertStatus(404, send("GET", path, token("service", SCOPES,
                        Map.of("device_ops_namespaces", List.of("*"))), null, null)),
                () -> assertStatus(403, send("GET", path, token(null, SCOPES,
                        Map.of("device_ops_namespaces", List.of(namespace))), null, null)));
        for (boolean project : List.of(false, true)) {
            assertStatus(403, send("POST", submitPath(project), token(null, SCOPES,
                    claims(namespace, List.of(PROJECT))), unique("no-sub"), submission(namespace, project)));
        }
    }

    @Test
    void projectClaimsAlsoProtectGenericReadsAndProjectPathsCannotRebindACollection() throws Exception {
        String namespace = unique("project");
        String owner = owner(namespace);
        String id = acceptedId(send("POST", submitPath(true), owner, unique("project"), submission(namespace, true)), false);
        awaitSuccess(readPath(true, id), namespace, owner);
        String noProject = token("same-namespace-service", SCOPES, claims(namespace, List.of()));
        for (String path : allReadPaths(id)) {
            assertStatus(403, send("GET", path + "?namespace=" + namespace, noProject, null, null));
        }
        ObjectNode genericWithProject = submission(namespace, false);
        genericWithProject.set("context", json.createObjectNode().set("project", project(namespace)));
        assertStatus(403, send("POST", submitPath(false), noProject, unique("project-denied"), genericWithProject));
        assertStatus(403, send("POST", submitPath(true), noProject, unique("project-denied"), submission(namespace, true)));
        String bothProjects = token("both-projects", SCOPES, claims(namespace, List.of(PROJECT, OTHER_PROJECT)));
        String wrongPath = "/api/v1/projects/" + OTHER_PROJECT + "/collections/" + id;
        assertAll(
                () -> assertStatus(404, send("GET", wrongPath + "?namespace=" + namespace, bothProjects, null, null)),
                () -> assertStatus(404, send("GET", wrongPath + "/output-events?namespace=" + namespace,
                        bothProjects, null, null)));
        assertStatus(200, send("GET", genericPath(id) + "?namespace=" + namespace, bothProjects, null, null));
    }

    @Test
    void requiredNamespaceIdempotencyAndProjectBindingProduceBadRequestInsteadOfServerErrors() throws Exception {
        String namespace = unique("validation");
        String token = owner(namespace);
        for (boolean project : List.of(false, true)) {
            ObjectNode body = submission(namespace, project);
            assertAll(
                    () -> assertStatus(400, send("POST", submitPath(project), token, null, body)),
                    () -> assertStatus(400, send("POST", submitPath(project), token, "x".repeat(201), body)));
        }
        ObjectNode mismatch = submission(namespace, true);
        ((ObjectNode) mismatch.get("project")).put("projectKey", OTHER_PROJECT);
        assertStatus(400, send("POST", submitPath(true), token, unique("binding"), mismatch));
        ObjectNode namespaceMismatch = submission(namespace, true);
        ((ObjectNode) namespaceMismatch.get("project")).put("namespace", "different-namespace");
        assertStatus(400, send("POST", submitPath(true), token, unique("namespace-binding"), namespaceMismatch));
        for (String path : allReadPaths("unknown")) {
            assertAll(path,
                    () -> assertStatus(400, send("GET", path, token, null, null)),
                    () -> assertStatus(400, send("GET", path + "?namespace=", token, null, null)),
                    () -> assertStatus(404, send("GET", path + "?namespace=" + namespace, token, null, null)));
        }
        for (boolean project : List.of(false, true)) {
            assertStatus(400, send("GET", readPath(project, "unknown") + "/output-events?namespace="
                    + namespace + "&after=-1", token, null, null));
        }
    }

    @Test
    void testOnlyLoopbackExceptionIsLimitedToTheFixtureAndProductionPolicyIsUnchanged() {
        RemoteEndpointPolicy fixturePolicy = context.getBean(RemoteEndpointPolicy.class);
        assertEquals("127.0.0.1", fixturePolicy.resolveConnectAddress("127.0.0.1", ssh.port()));
        assertThrows(IllegalArgumentException.class,
                () -> fixturePolicy.resolveConnectAddress("127.0.0.1", ssh.port() == 65535 ? 65534 : ssh.port() + 1));
        assertThrows(IllegalArgumentException.class, () -> fixturePolicy.resolveConnectAddress("localhost", ssh.port()));
        assertThrows(IllegalArgumentException.class,
                () -> new SecureRemoteEndpointPolicy().resolveConnectAddress("127.0.0.1", ssh.port()));
    }

    @Test
    void externalCollectionDocsArePublicAndContainExactlyNineBearerOperations() throws Exception {
        var response = send("GET", "/v3/api-docs/external-collection", null, null, null);
        assertStatus(200, response);
        JsonNode docs = json.readTree(response.body());
        JsonNode paths = docs.path("paths");
        Set<String> expected = Set.of(submitPath(false), genericPath("{collectionId}"),
                genericPath("{collectionId}") + "/output-events", genericPath("{collectionId}") + "/semantic-results",
                genericPath("{collectionId}") + "/evidence",
                "/api/v1/projects/{projectKey}/collections", "/api/v1/projects/{projectKey}/collections/{collectionId}",
                "/api/v1/projects/{projectKey}/collections/{collectionId}/output-events",
                "/api/v1/projects/{projectKey}/collections/{collectionId}/evidence");
        Set<String> actual = new java.util.HashSet<>();
        paths.fieldNames().forEachRemaining(actual::add);
        assertEquals(expected, actual);
        paths.forEach(item -> {
            assertEquals(1, item.size());
            item.forEach(operation -> {
                assertEquals(1, operation.path("security").size());
                assertFalse(operation.path("parameters").toString().contains("\"jwt\""));
            });
        });
        assertTrue(docs.path("security").isMissingNode() || docs.path("security").isEmpty());
        assertTrue(docs.path("components").path("securitySchemes").toString().contains("bearer"));
        assertTrue(paths.path(submitPath(false)).path("post").path("responses").has("202"));
        var parserDocs = send("GET", "/v3/api-docs/external-parser", null, null, null);
        assertStatus(200, parserDocs);
        assertFalse(json.readTree(parserDocs.body()).path("paths").has(submitPath(false)));
    }

    private ObjectNode submission(String namespace, boolean projectScoped) throws Exception {
        String command = unique("show-version") + "\n" + unique("show-clock");
        ObjectNode body = json.createObjectNode().put("namespace", namespace)
                .put("externalRequestId", unique("external")).put("activityType", "HTTP_COLLECTION")
                .put("commandTimeoutSeconds", 10).put("parseTimeoutSeconds", 3).put("leaseGraceSeconds", 1);
        body.set("script", json.createObjectNode().put("source", "EXTERNAL_DELIVERED")
                .put("key", "external-http-script").put("version", "1")
                .put("content", command).put("sha256", sha256(command))
                .put("policy", "EXECUTION_ONLY").put("parserType", "none"));
        body.set("semanticParsing", json.createObjectNode().put("enabled", false));
        ObjectNode connection = json.createObjectNode().put("protocol", "SSH2").put("host", "127.0.0.1")
                .put("port", ssh.port()).put("username", USERNAME).put("authenticationType", "PASSWORD")
                .put("executionMode", "EXEC").put("hostKeyFingerprint", ssh.fingerprint())
                .put("connectTimeoutSeconds", 5).put("password", PASSWORD);
        if (projectScoped) {
            body.set("project", project(namespace));
            ObjectNode target = connection.deepCopy();
            target.set("project", project(namespace));
            target.set("device", json.createObjectNode().put("deviceKey", unique("device"))
                    .put("deviceName", "Real SSH fixture").put("vendor", "generic").put("model", "generic"));
            target.set("extensions", json.createObjectNode());
            body.set("targets", json.createArrayNode().add(target));
        } else {
            body.set("connection", connection);
        }
        return body;
    }

    private ObjectNode project(String namespace) {
        return json.createObjectNode().put("namespace", namespace).put("projectKey", PROJECT)
                .put("projectName", "HTTP project").put("projectCode", "HTTP");
    }

    private String acceptedId(HttpResponse<String> response, boolean existing) throws Exception {
        assertStatus(202, response);
        JsonNode accepted = json.readTree(response.body());
        assertEquals(existing, accepted.path("existing").asBoolean(), response.body());
        String id = accepted.path("collectionId").asText();
        assertFalse(id.isBlank(), response.body());
        return id;
    }

    private JsonNode awaitSuccess(String path, String namespace, String token) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        JsonNode details;
        do {
            var response = send("GET", path + "?namespace=" + namespace, token, null, null);
            assertStatus(200, response);
            details = json.readTree(response.body());
            String state = details.path("status").asText();
            if ("SUCCEEDED".equals(state)) return details;
            assertFalse(Set.of("FAILED", "PARTIAL_SUCCESS", "TIMED_OUT", "CANCELLED").contains(state), details.toString());
            Thread.sleep(30);
        } while (System.nanoTime() < deadline);
        fail("Collection did not succeed: " + details);
        return details;
    }

    private void assertOutput(JsonNode details, ObjectNode body) {
        assertEquals(1, details.path("targets").size(), details.toString());
        JsonNode target = details.path("targets").get(0);
        assertEquals("SUCCEEDED", target.path("status").asText(), details.toString());
        assertEquals(0, target.path("exitCode").asInt(-1));
        assertEquals(expectedOutput(body), target.path("stdout").asText());
        assertEquals("", target.path("stderr").asText());
        assertEquals(2, target.path("commandBlocks").size());
        assertEquals(body.path("script").path("sha256").asText(), details.path("script").path("sha256").asText());
    }

    private static String expectedOutput(ObjectNode body) {
        return body.path("script").path("content").asText().lines().map(command -> command + "-output\n")
                .reduce("", String::concat);
    }

    private void assertExecutedOnce(ObjectNode body) {
        body.path("script").path("content").asText().lines().forEach(command ->
                assertEquals(1, ssh.executions(command), "SSH command executed more than once: " + command));
    }

    private List<SseEvent> stream(String path, String namespace, String token, long after) throws Exception {
        var response = send("GET", path + "?namespace=" + namespace + "&after=" + after, token, null, null);
        assertStatus(200, response);
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));
        List<SseEvent> events = new ArrayList<>();
        for (String frame : response.body().replace("\r\n", "\n").split("\n\n")) {
            String name = "";
            long id = 0;
            StringBuilder data = new StringBuilder();
            for (String line : frame.split("\n")) {
                if (line.startsWith("event:")) name = line.substring(6).strip();
                if (line.startsWith("id:")) id = Long.parseLong(line.substring(3).strip());
                if (line.startsWith("data:")) data.append(line.substring(5).stripLeading());
            }
            if (!name.isEmpty()) events.add(new SseEvent(name, id, json.readTree(data.toString())));
        }
        assertFalse(response.body().contains(PASSWORD));
        return List.copyOf(events);
    }

    private static void assertComplete(List<SseEvent> events, long cursor) {
        assertEquals(1, events.stream().filter(event -> "complete".equals(event.name())).count());
        SseEvent complete = events.getLast();
        assertEquals("complete", complete.name());
        assertEquals("SUCCEEDED", complete.data().path("status").asText());
        assertEquals(cursor, complete.data().path("lastSequence").asLong());
    }

    private HttpResponse<String> send(String method, String path, String token, String key, ObjectNode body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15))
                .header("Accept", path.contains("/output-events") ? "text/event-stream" : "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (key != null) request.header("Idempotency-Key", key);
        if (body != null) request.header("Content-Type", "application/json");
        // Never pass mutable request records/char[] to the server or reuse a credential-cleared server DTO.
        String payload = body == null ? null : json.writeValueAsString(body.deepCopy());
        request.method(method, payload == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        var response = http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
        try {
            return response.get(20, TimeUnit.SECONDS);
        } finally {
            if (!response.isDone()) response.cancel(true);
        }
    }

    private static List<String> allReadPaths(String id) {
        return List.of(genericPath(id), genericPath(id) + "/output-events", genericPath(id) + "/semantic-results",
                readPath(true, id), readPath(true, id) + "/output-events");
    }

    private static String submitPath(boolean project) {
        return project ? "/api/v1/projects/" + PROJECT + "/collections" : "/api/v1/collections";
    }

    private static String genericPath(String id) { return submitPath(false) + "/" + id; }
    private static String readPath(boolean project, String id) { return submitPath(project) + "/" + id; }
    private static String unique(String prefix) { return prefix + "-" + UUID.randomUUID(); }

    private static String owner(String namespace) throws Exception {
        return token("service-" + namespace, SCOPES, claims(namespace, List.of(PROJECT)));
    }

    private static Map<String, Object> claims(String namespace, List<String> projects) {
        return Map.of("device_ops_namespaces", List.of(namespace), "device_ops_projects", projects);
    }

    private static String token(String subject, String scopes, Map<String, Object> extra) throws Exception {
        return signedToken(subject, scopes, extra, JWT_KEYS);
    }

    private static String signedToken(String subject, String scopes, Map<String, Object> extra, KeyPair keys) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issuer(ISSUER).issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).claim("scope", scopes);
        if (subject != null) claims.subject(subject);
        extra.forEach(claims::claim);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), claims.build());
        jwt.sign(new RSASSASigner(keys.getPrivate()));
        return jwt.serialize();
    }

    private static String sha256(String script) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(script.getBytes(StandardCharsets.UTF_8)));
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
        assertEquals(expected, response.statusCode(), response.request().method() + " " + response.uri() + "\n" + response.body());
    }

    private record SseEvent(String name, long id, JsonNode data) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class CollectionHttpFixtures {
        @Bean
        JwtDecoder collectionTestJwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) JWT_KEYS.getPublic()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder;
        }

        @Bean(destroyMethod = "close")
        SshFixture collectionSshFixture() throws IOException { return new SshFixture(); }

        @Bean
        @Primary
        RemoteEndpointPolicy collectionFixtureEndpointPolicy(SshFixture fixture) {
            RemoteEndpointPolicy production = new SecureRemoteEndpointPolicy();
            return (host, port) -> "127.0.0.1".equals(host) && port == fixture.port()
                    ? "127.0.0.1" : production.resolveConnectAddress(host, port);
        }
    }

    static final class SshFixture implements AutoCloseable {
        private final SshServer server = SshServer.setUpDefaultServer();
        private final KeyPair hostKey = keys();
        private final ExecutorService commands = Executors.newFixedThreadPool(2);
        private final Map<String, AtomicInteger> executions = new ConcurrentHashMap<>();

        SshFixture() throws IOException {
            server.setHost("127.0.0.1");
            server.setPort(0);
            server.setKeyPairProvider(session -> List.of(hostKey));
            server.setPasswordAuthenticator((username, password, session) ->
                    USERNAME.equals(username) && PASSWORD.equals(password));
            server.setCommandFactory((channel, command) -> new FixtureCommand(command));
            try {
                server.start();
            } catch (IOException failure) {
                commands.shutdownNow();
                server.close(true);
                throw failure;
            }
        }

        int port() { return server.getPort(); }
        String fingerprint() { return KeyUtils.getFingerPrint(hostKey.getPublic()); }
        int executions(String command) { return executions.getOrDefault(command, new AtomicInteger()).get(); }

        @Override
        public void close() throws IOException, InterruptedException {
            try {
                server.stop(true);
            } finally {
                commands.shutdownNow();
                assertTrue(commands.awaitTermination(3, TimeUnit.SECONDS), "SSH fixture commands did not terminate");
            }
        }

        private final class FixtureCommand implements Command {
            private final String command;
            private OutputStream output;
            private ExitCallback callback;
            private Future<?> worker;

            FixtureCommand(String command) { this.command = command; }
            @Override public void setInputStream(InputStream input) { }
            @Override public void setOutputStream(OutputStream output) { this.output = output; }
            @Override public void setErrorStream(OutputStream error) { }
            @Override public void setExitCallback(ExitCallback callback) { this.callback = callback; }

            @Override
            public void start(ChannelSession channel, Environment environment) {
                worker = commands.submit(() -> {
                    executions.computeIfAbsent(command, ignored -> new AtomicInteger()).incrementAndGet();
                    try {
                        output.write((command + "-output\n").getBytes(StandardCharsets.UTF_8));
                        output.flush();
                        callback.onExit(0);
                    } catch (IOException failure) {
                        callback.onExit(1, "fixture output failed");
                    }
                });
            }

            @Override
            public void destroy(ChannelSession channel) {
                if (worker != null) worker.cancel(true);
            }
        }
    }
}
