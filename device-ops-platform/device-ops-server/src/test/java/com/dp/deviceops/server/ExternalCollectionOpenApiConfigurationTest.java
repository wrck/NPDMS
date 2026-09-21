package com.dp.deviceops.server;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExternalCollectionOpenApiConfigurationTest {
    private static final String COLLECTIONS = "/api/v1/collections";
    private static final String COLLECTION = COLLECTIONS + "/{collectionId}";
    private static final String PROJECT_COLLECTIONS = "/api/v1/projects/{projectKey}/collections";
    private static final String PROJECT_COLLECTION = PROJECT_COLLECTIONS + "/{collectionId}";
    private static final Map<String, HttpMethod> OPERATIONS = Map.of(
            COLLECTIONS, HttpMethod.POST,
            COLLECTION, HttpMethod.GET,
            COLLECTION + "/output-events", HttpMethod.GET,
            COLLECTION + "/semantic-results", HttpMethod.GET,
            COLLECTION + "/evidence", HttpMethod.GET,
            PROJECT_COLLECTIONS, HttpMethod.POST,
            PROJECT_COLLECTION, HttpMethod.GET,
            PROJECT_COLLECTION + "/output-events", HttpMethod.GET,
            PROJECT_COLLECTION + "/evidence", HttpMethod.GET);
    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void loadOnlyDocumentationConfigurations() {
        context = new AnnotationConfigApplicationContext(
                ExternalParserOpenApiConfiguration.class, ExternalCollectionOpenApiConfiguration.class);
    }

    @AfterAll
    static void closeContext() { context.close(); }

    @Test
    void collectionGroupIncludesExactlyNineOperationsAndExcludesManagementAndAdditionalMethods() {
        GroupedOpenApi group = collectionGroup();
        assertEquals(OPERATIONS.keySet(), Set.copyOf(group.getPathsToMatch()));
        OpenAPI docs = externalDocument();
        docs.getPaths().values().forEach(item -> {
            for (HttpMethod method : HttpMethod.values()) {
                if (!item.readOperationsMap().containsKey(method)) item.operation(method, new Operation());
            }
        });
        docs.getPaths().addPathItem("/api/v1/parser-log-types", new PathItem().get(new Operation()));
        docs.getPaths().addPathItem("/api/v1/parse-tasks", new PathItem().post(new Operation()));
        docs.getPaths().addPathItem("/api/v1/connections", new PathItem().get(new Operation()));
        docs.getPaths().addPathItem(COLLECTION + "/cancellations", new PathItem().post(new Operation()));
        docs.getPaths().addPathItem(PROJECT_COLLECTION + "/semantic-results", new PathItem().get(new Operation()));
        customize(docs);
        group.getOpenApiCustomizers().forEach(customizer -> customizer.customise(docs));
        assertEquals(OPERATIONS.keySet(), docs.getPaths().keySet());
        docs.getPaths().forEach((path, item) -> {
            assertEquals(Set.of(OPERATIONS.get(path)), item.readOperationsMap().keySet());
            assertBearer(docs, item.readOperations().getFirst());
        });
        assertNull(docs.getSecurity(), "There must be no global collection authentication requirement");
    }

    @Test
    void metadataExplainsScopesRequiredNamespaceReplayAndEveryApplicableResponse() {
        OpenAPI docs = externalDocument();
        customize(docs);
        customize(docs);
        docs.getPaths().forEach((path, item) -> {
            Operation operation = item.readOperations().getFirst();
            assertBearer(docs, operation);
            assertNotNull(operation.getSummary());
            assertFalse(operation.getSummary().isBlank());
            assertNotNull(operation.getDescription());
            assertTrue(operation.getDescription().contains("namespace"));
            assertTrue(operation.getDescription().toLowerCase().contains("required"));
            boolean submit = OPERATIONS.get(path) == HttpMethod.POST;
            assertTrue(operation.getDescription().contains(submit
                    ? "device-ops:collections:execute" : "device-ops:collections:read"));
            assertEquals(submit ? Set.of("202", "400", "401", "403", "409", "429")
                    : Set.of("200", "400", "401", "403", "404"), operation.getResponses().keySet());
            operation.getResponses().values().forEach(response -> assertFalse(response.getDescription().isBlank()));
            if (submit) {
                assertTrue(operation.getDescription().contains("Idempotency-Key"));
                assertTrue(operation.getDescription().contains("409"));
                assertTrue(operation.getDescription().contains("semanticParsing"));
            } else {
                Parameter namespace = operation.getParameters().stream()
                        .filter(parameter -> "namespace".equals(parameter.getName())).findFirst().orElseThrow();
                assertEquals("query", namespace.getIn());
                assertEquals(Boolean.TRUE, namespace.getRequired());
            }
            if (path.contains("/output-events")) {
                assertTrue(operation.getDescription().contains("after"));
                assertTrue(operation.getDescription().contains("output"));
                assertTrue(operation.getDescription().contains("complete"));
            }
            if (path.endsWith("/evidence")) {
                assertTrue(operation.getDescription().contains("CAPTURED_SUBMISSION"));
                assertTrue(operation.getDescription().contains("RECONSTRUCTED_FACTS"));
                assertTrue(operation.getDescription().contains("no-store"));
                assertTrue(operation.getDescription().contains("passwords"));
            }
        });
    }

    @Test
    void publicManagementAndExternalParserMetadataRemainUnchangedWithBothConfigurationsLoaded() {
        Operation publicRoute = new Operation().summary("Public runtime configuration");
        Operation management = new Operation().summary("Manage saved connections");
        Operation parser = new Operation();
        SecurityScheme existing = new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic");
        OpenAPI docs = externalDocument().components(new Components().addSecuritySchemes("Existing", existing));
        docs.getPaths().addPathItem("/api/v1/runtime-config", new PathItem().get(publicRoute));
        docs.getPaths().addPathItem("/api/v1/connections", new PathItem().get(management));
        docs.getPaths().addPathItem("/api/v1/parse-tasks", new PathItem().post(parser));
        customize(docs);
        assertBearer(docs, docs.getPaths().get(COLLECTIONS).getPost());
        assertSame(existing, docs.getComponents().getSecuritySchemes().get("Existing"));
        assertNull(publicRoute.getSecurity());
        assertNull(management.getSecurity());
        assertEquals("Public runtime configuration", publicRoute.getSummary());
        assertEquals("Manage saved connections", management.getSummary());
        assertNull(docs.getSecurity());
        assertEquals(List.of(Map.of("Bearer", List.of())), parser.getSecurity());
        assertTrue(parser.getDescription().contains("parser:task:create"));
        assertFalse(parser.getDescription().contains("device-ops:collections"));
        assertTrue(docs.getComponents().getSecuritySchemes().get("Bearer").getDescription()
                .contains("parser:task:create"), "The collection scheme must not overwrite the parser scheme");
        GroupedOpenApi parserGroup = context.getBeansOfType(GroupedOpenApi.class).values().stream()
                .filter(group -> "external-parser".equals(group.getGroup())).findFirst().orElseThrow();
        assertEquals(Set.of("/api/v1/parse-tasks", "/api/v1/parse-tasks/{taskId}",
                "/api/v1/parse-tasks/{taskId}/result", "/api/v1/parse-results/{resultId}"),
                Set.copyOf(parserGroup.getPathsToMatch()));
        parserGroup.getOpenApiCustomizers().forEach(customizer -> customizer.customise(docs));
        assertEquals(Set.of("/api/v1/parse-tasks"), docs.getPaths().keySet());
        assertSame(parser, docs.getPaths().get("/api/v1/parse-tasks").getPost());
    }

    @Test
    void enrichmentPreservesGeneratedBodiesResponseSchemasAndNamespaceParameterIdentity() {
        Content content = new Content().addMediaType("application/json", new MediaType().schema(new StringSchema()));
        RequestBody body = new RequestBody().content(content).required(true);
        ApiResponse accepted = new ApiResponse().description("Generated").content(content);
        Parameter idempotency = new Parameter().name("Idempotency-Key").in("header")
                .required(true).schema(new StringSchema().maxLength(200));
        Operation submit = new Operation().requestBody(body).addParametersItem(idempotency)
                .responses(new ApiResponses().addApiResponse("202", accepted));
        Parameter namespace = new Parameter().name("namespace").in("query")
                .required(false).schema(new StringSchema().maxLength(100));
        Operation read = new Operation().addParametersItem(namespace);
        OpenAPI docs = new OpenAPI().paths(new Paths().addPathItem(COLLECTIONS, new PathItem().post(submit))
                .addPathItem(COLLECTION, new PathItem().get(read)));
        customize(docs);
        customize(docs);
        assertBearer(docs, submit);
        assertSame(body, submit.getRequestBody());
        assertSame(content, body.getContent());
        assertSame(accepted, submit.getResponses().get("202"));
        assertSame(content, accepted.getContent());
        assertSame(idempotency, submit.getParameters().getFirst());
        assertEquals(200, idempotency.getSchema().getMaxLength());
        assertEquals(1, read.getParameters().size());
        assertSame(namespace, read.getParameters().getFirst());
        assertEquals(Boolean.TRUE, namespace.getRequired());
        assertEquals(100, namespace.getSchema().getMaxLength());
    }

    private static GroupedOpenApi collectionGroup() {
        return context.getBeansOfType(GroupedOpenApi.class).values().stream()
                .filter(group -> "external-collection".equals(group.getGroup())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing external-collection OpenAPI group"));
    }

    private static OpenAPI externalDocument() {
        OpenAPI docs = new OpenAPI().paths(new Paths());
        OPERATIONS.forEach((path, method) -> {
            PathItem item = new PathItem();
            item.operation(method, new Operation());
            docs.getPaths().addPathItem(path, item);
        });
        return docs;
    }

    private static void customize(OpenAPI docs) {
        context.getBeansOfType(GlobalOpenApiCustomizer.class).values()
                .forEach(customizer -> customizer.customise(docs));
    }

    private static void assertBearer(OpenAPI docs, Operation operation) {
        assertNotNull(operation.getSecurity(), "Collection operations require explicit Bearer JWT metadata");
        assertEquals(1, operation.getSecurity().size());
        assertEquals(1, operation.getSecurity().getFirst().size());
        String key = operation.getSecurity().getFirst().keySet().iterator().next();
        assertEquals(List.of(), operation.getSecurity().getFirst().get(key));
        SecurityScheme scheme = docs.getComponents().getSecuritySchemes().get(key);
        assertNotNull(scheme);
        assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
        assertEquals("bearer", scheme.getScheme());
        assertEquals("JWT", scheme.getBearerFormat());
        assertFalse(docs.getComponents().getSecuritySchemes().values().stream()
                .anyMatch(candidate -> candidate.getType() == SecurityScheme.Type.APIKEY));
    }
}
