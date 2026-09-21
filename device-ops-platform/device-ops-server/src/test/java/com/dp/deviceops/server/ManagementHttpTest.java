package com.dp.deviceops.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.*;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ManagementHttpTest {
    private ConfigurableApplicationContext context;
    private String base;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeAll void start() {
        context = new SpringApplicationBuilder(DeviceOpsServerApplication.class, Fixtures.class)
                .web(WebApplicationType.SERVLET).run("--server.port=0", "--spring.datasource.url=jdbc:h2:mem:management-http-" + UUID.randomUUID(),
                        "--spring.datasource.username=sa", "--spring.datasource.password=", "--device-ops.security.mode=oauth2",
                        "--device-ops.runtime.auth-mode=oauth2", "--device-ops.schedule.enabled=false", "--device-ops.callback.enabled=false",
                        "--device-ops.parser.worker.shutdown-grace=100ms", "--device-ops.executor.shutdown-graceful-period=100ms");
        base = "http://127.0.0.1:" + ((WebServerApplicationContext)context).getWebServer().getPort();
    }
    @AfterAll void stop() { if (context != null) context.close(); http.shutdownNow(); }

    @Test void managementReadRoutesRequireScopeAndValidateFilters() throws Exception {
        for (String path : List.of("collections", "overview", "scripts", "settings")) {
            assertEquals(401, get("/api/v1/management/" + path, null).statusCode());
            assertEquals(403, get("/api/v1/management/" + path, "no-scope").statusCode());
            assertEquals(200, get("/api/v1/management/" + path, "reader").statusCode());
        }
        for (String query : List.of("size=101", "size=0", "page=-1", "status=INVALID", "from=bad",
                "from=2026-09-09T00:00:00Z&to=2026-09-08T00:00:00Z")) {
            assertEquals(400, get("/api/v1/management/collections?" + query, "reader").statusCode(), query);
        }
        assertEquals(403, get("/api/v1/management/collections?namespace=foreign", "reader").statusCode());
        assertEquals(403, get("/api/v1/management/collections?project=foreign", "reader").statusCode());
        assertEquals(404, get("/api/v1/management/scripts/content?collectionId=missing", "reader").statusCode());
    }

    @Test void settingsExposeOnlyIdentityAndEffectiveCapabilityAllowlist() throws Exception {
        var response = get("/api/v1/management/settings", "reader");
        assertEquals(200, response.statusCode());
        var body = json.readTree(response.body());
        assertEquals("owner", body.path("subject").asText());
        assertEquals("oauth2", body.path("authMode").asText());
        assertFalse(body.path("capabilities").path("scheduleEnabled").asBoolean(true));
        assertEquals("owned", body.path("namespaces").get(0).asText());
        assertFalse(response.body().contains("jdbc:"));
        assertFalse(response.body().contains("master-key"));
    }

    @Test void activeBindingReadUsesExistingParserReadScopeAndDistinguishesUnknownType() throws Exception {
        var repository = context.getBean(com.dp.deviceops.parser.runtime.port.ParserReleaseRepository.class);
        var now = Instant.now();
        repository.createLogType(new com.dp.deviceops.parser.runtime.model.LogType("management-fixture", "Fixture", "", now, now));
        String path = "/api/v1/parser-log-types/management-fixture/active-release";
        assertEquals(401, get(path, null).statusCode());
        assertEquals(403, get(path, "no-scope").statusCode());
        var response = get(path, "reader");
        assertEquals(200, response.statusCode());
        assertTrue(json.readTree(response.body()).path("releaseId").isNull());
        assertEquals(404, get("/api/v1/parser-log-types/not-existing/active-release", "reader").statusCode());
    }

    @Test void queryAndContentHonorFallbackWildcardProvenanceAndRedactSummaries() throws Exception {
        var jdbc = context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class);
        for (String ns : List.of("owned", "foreign", "*")) {
            String id = "http-" + ns;
            jdbc.sql("insert into device_ops_collection(task_id,namespace,project_key,idempotency_key,script_source,script_key,script_version,script_content,script_sha256,script_policy,parser_type) values(:id,:ns,'p',:id,'LOCAL_MANAGED','http-script','1','SECRET-SCRIPT',:hash,'REGISTER_VERSION','NONE')")
                    .param("id", id).param("ns", ns).param("hash", "a".repeat(64)).update();
            jdbc.sql("insert into device_ops_collection_target(task_id,project_name,project_code,device_key,device_name,vendor,model,extensions_json,host,port,username,status,standard_output,standard_error,output_truncated) values(:id,'P','P','device','D','V','M','{}','synthetic.invalid',22,'test','QUEUED','','',false)")
                    .param("id", id).update();
            jdbc.sql("insert into device_ops_script(namespace,script_key,source) values(:ns,'http-script','LOCAL_MANAGED')").param("ns", ns).update();
        }
        jdbc.sql("insert into device_ops_script_version(script_id,version,content,sha256,parser_type) select id,'1','SECRET-SCRIPT',:hash,'NONE' from device_ops_script where script_key='http-script'")
                .param("hash", "a".repeat(64)).update();
        for (String token : List.of("fallback-star", "subject-star")) {
            var response = get("/api/v1/management/collections", token);
            assertEquals(200, response.statusCode());
            assertEquals(1, json.readTree(response.body()).path("total").asInt());
            assertEquals("*", json.readTree(response.body()).path("items").get(0).path("namespace").asText());
            assertFalse(response.body().contains("SECRET-SCRIPT"));
            assertEquals(404, get("/api/v1/management/scripts/content?collectionId=http-foreign", token).statusCode());
            assertEquals(200, get("/api/v1/management/scripts/content?collectionId=http-*", token).statusCode());
            assertEquals(403, get("/api/v1/collections/http-foreign/evidence?namespace=foreign", token).statusCode());
            assertEquals(200, get("/api/v1/collections/http-*/evidence?namespace=*", token).statusCode());
        }
        for (String route : List.of("/api/v1/collections/http-owned/evidence?namespace=owned", "/api/v1/projects/p/collections/http-owned/evidence?namespace=owned")) {
            assertEquals(401, get(route, null).statusCode());
            assertEquals(403, get(route, "no-scope").statusCode());
            var evidence = get(route, "reader");
            assertEquals(200, evidence.statusCode());
            assertEquals("no-store", evidence.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals("SECRET-SCRIPT", json.readTree(evidence.body()).path("input").path("content").asText());
            assertEquals("RECONSTRUCTED_FACTS", json.readTree(evidence.body()).path("submission").path("provenance").asText());
        }
        assertEquals(404, get("/api/v1/projects/other/collections/http-owned/evidence?namespace=owned", "reader").statusCode());
        assertEquals(404, get("/api/v1/collections/HTTP-owned/evidence?namespace=owned", "reader").statusCode());
        var ordinaryDetails = get("/api/v1/collections/http-owned?namespace=owned", "reader");
        assertEquals(200, ordinaryDetails.statusCode());
        assertFalse(ordinaryDetails.body().contains("SECRET-SCRIPT"));
        assertEquals(200, get("/api/v1/collections/http-foreign/evidence?namespace=foreign", "explicit-star").statusCode());
        assertEquals(3, json.readTree(get("/api/v1/management/collections", "explicit-star").body()).path("total").asInt());
        assertEquals(1, json.readTree(get("/api/v1/management/collections", "reader").body()).path("total").asInt());
        assertEquals(1, json.readTree(get("/api/v1/management/overview", "reader").body()).path("total").asInt());
        assertEquals(200, get("/api/v1/management/collections?from=2026-01-01T00:00:00Z&to=2099-01-01T00:00:00Z", "reader").statusCode());
    }

    HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    @TestConfiguration static class Fixtures {
        @Bean @Primary JwtDecoder decoder() {
            return token -> {
                var builder = Jwt.withTokenValue(token).header("alg", "test-only")
                        .subject(token.equals("subject-star") ? "*" : "owner")
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                        .claim("scope", token.equals("no-scope") ? "" : "device-ops:collections:read parser:release:read")
                        .claim("device_ops_projects", List.of("p"));
                if (token.equals("fallback-star")) builder.claim("client_namespace", "*");
                else if (!token.equals("subject-star")) builder.claim("device_ops_namespaces", List.of(token.equals("explicit-star") ? "*" : "owned"));
                return builder.build();
            };
        }
    }
}
