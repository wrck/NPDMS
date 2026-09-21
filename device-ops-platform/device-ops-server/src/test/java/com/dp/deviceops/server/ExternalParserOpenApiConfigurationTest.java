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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalParserOpenApiConfigurationTest {

    private static final String TASKS = "/api/v1/parse-tasks";
    private static final String TASK = TASKS + "/{taskId}";
    private static final String TASK_RESULT = TASK + "/result";
    private static final String RESULT = "/api/v1/parse-results/{resultId}";
    private static final Map<String, Set<HttpMethod>> EXTERNAL_OPERATIONS = Map.of(
            TASKS, Set.of(HttpMethod.POST, HttpMethod.GET),
            TASK, Set.of(HttpMethod.GET),
            TASK_RESULT, Set.of(HttpMethod.GET),
            RESULT, Set.of(HttpMethod.GET));

    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void loadDocumentationConfigurationOnly() {
        context = new AnnotationConfigApplicationContext(ExternalParserOpenApiConfiguration.class);
    }

    @AfterAll
    static void closeContext() {
        context.close();
    }

    @Test
    void bearerSchemeDoesNotSecurePublicRoutesOrChangeReleaseAdministration() {
        Operation publicRoute = new Operation().summary("Public configuration");
        Operation admin = new Operation().summary("Release administration");
        SecurityScheme existingScheme = new SecurityScheme().type(SecurityScheme.Type.APIKEY);
        OpenAPI docs = new OpenAPI().components(new Components().addSecuritySchemes("Existing", existingScheme))
                .paths(new Paths()
                        .addPathItem("/api/v1/runtime-config", new PathItem().get(publicRoute))
                        .addPathItem("/api/v1/parser-releases/{releaseId}", new PathItem().get(admin)));

        customizeDefault(docs);

        assertBearerScheme(docs);
        assertSame(existingScheme, docs.getComponents().getSecuritySchemes().get("Existing"));
        assertEquals("Public configuration", publicRoute.getSummary());
        assertNull(publicRoute.getSecurity());
        assertEquals("Release administration", admin.getSummary());
        assertNull(admin.getSecurity());
        assertNull(docs.getSecurity(), "Bearer must not become an inaccurate global requirement");
    }

    @Test
    void groupWhitelistsAllFiveOperationsAndRejectsReleaseAdminControlRoutesAndExtraMethods() {
        GroupedOpenApi group = externalGroup();
        assertEquals(EXTERNAL_OPERATIONS.keySet(), Set.copyOf(group.getPathsToMatch()));
        OpenAPI docs = externalDocument();
        docs.getPaths().values().forEach(item -> {
            item.delete(new Operation());
            item.put(new Operation());
            item.patch(new Operation());
            item.head(new Operation());
            item.options(new Operation());
            item.trace(new Operation());
        });
        docs.getPaths().get(TASK_RESULT).post(new Operation());
        docs.getPaths().addPathItem("/api/v1/parser-releases/{releaseId}", new PathItem().get(new Operation()));
        docs.getPaths().addPathItem(TASK + "/cancellations", new PathItem().post(new Operation()));
        docs.getPaths().addPathItem(TASK + "/terminations", new PathItem().post(new Operation()));
        docs.getPaths().addPathItem(TASK + "/internal", new PathItem().get(new Operation()));

        customizeDefault(docs);
        group.getOpenApiCustomizers().forEach(customizer -> customizer.customise(docs));

        assertBearerScheme(docs);
        assertEquals(EXTERNAL_OPERATIONS.keySet(), docs.getPaths().keySet());
        docs.getPaths().forEach((path, item) -> {
            assertEquals(EXTERNAL_OPERATIONS.get(path), item.readOperationsMap().keySet());
            item.readOperations().forEach(ExternalParserOpenApiConfigurationTest::assertExternalOperation);
        });
        assertNull(docs.getSecurity());
    }

    @Test
    void eachExternalOperationDescribesItsScopeAndApplicableResponsesIncludingResultNotReady() {
        OpenAPI docs = externalDocument();

        customizeDefault(docs);
        // Repeated customization must not duplicate bearer requirements.
        customizeDefault(docs);

        docs.getPaths().values().forEach(item ->
                item.readOperations().forEach(ExternalParserOpenApiConfigurationTest::assertExternalOperation));
        Operation submit = docs.getPaths().get(TASKS).getPost();
        assertTrue(submit.getDescription().contains("parser:task:create"));
        assertTrue(submit.getDescription().contains("Idempotency-Key"));
        assertResponses(submit, "202", "400", "401", "403", "404", "409", "413", "422", "503");
        assertFalse(submit.getResponses().containsKey("200"));
        assertResponses(docs.getPaths().get(TASKS).getGet(), "200", "400", "401", "403");
        assertResponses(docs.getPaths().get(TASK).getGet(), "200", "401", "403", "404");
        assertResponses(docs.getPaths().get(RESULT).getGet(), "200", "401", "403", "404");
        Operation resultByTask = docs.getPaths().get(TASK_RESULT).getGet();
        assertResponses(resultByTask, "200", "401", "403", "404", "409");
        assertTrue(resultByTask.getResponses().get("409").getDescription().toLowerCase().contains("not ready"));
    }

    @Test
    void metadataPreservesGeneratedRequestResponseAndParameterSchemas() {
        Content content = new Content().addMediaType("application/json",
                new MediaType().schema(new StringSchema()));
        RequestBody body = new RequestBody().content(content);
        ApiResponse accepted = new ApiResponse().description("Generated").content(content);
        ApiResponse invalid = new ApiResponse().description("Generated").content(content);
        Parameter idempotency = new Parameter().name("Idempotency-Key").in("header")
                .required(true).schema(new StringSchema().maxLength(128));
        Operation submission = new Operation().requestBody(body).addParametersItem(idempotency)
                .responses(new ApiResponses().addApiResponse("202", accepted).addApiResponse("400", invalid));
        OpenAPI docs = new OpenAPI().paths(new Paths().addPathItem(TASKS, new PathItem().post(submission)));

        customizeDefault(docs);
        externalGroup().getOpenApiCustomizers().forEach(customizer -> customizer.customise(docs));

        assertExternalOperation(submission);
        assertSame(body, submission.getRequestBody());
        assertSame(content, body.getContent());
        assertSame(accepted, submission.getResponses().get("202"));
        assertSame(content, submission.getResponses().get("202").getContent());
        assertSame(invalid, submission.getResponses().get("400"));
        assertSame(content, submission.getResponses().get("400").getContent());
        assertSame(idempotency, submission.getParameters().getFirst());
        assertEquals(128, idempotency.getSchema().getMaxLength());
        assertFalse("Generated".equals(accepted.getDescription()));
    }

    private static OpenAPI externalDocument() {
        OpenAPI docs = new OpenAPI().paths(new Paths());
        EXTERNAL_OPERATIONS.forEach((path, methods) -> {
            PathItem item = new PathItem();
            methods.forEach(method -> item.operation(method, new Operation()));
            docs.getPaths().addPathItem(path, item);
        });
        return docs;
    }

    private static GroupedOpenApi externalGroup() {
        return context.getBeansOfType(GroupedOpenApi.class).values().stream()
                .filter(group -> "external-parser".equals(group.getGroup())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing external-parser OpenAPI group"));
    }

    private static void customizeDefault(OpenAPI docs) {
        var customizers = context.getBeansOfType(GlobalOpenApiCustomizer.class).values();
        assertFalse(customizers.isEmpty(), "External parser metadata must customize default and grouped docs");
        customizers.forEach(customizer -> customizer.customise(docs));
    }

    private static void assertBearerScheme(OpenAPI docs) {
        assertNotNull(docs.getComponents());
        assertNotNull(docs.getComponents().getSecuritySchemes(), "Explicit bearer JWT scheme is required");
        SecurityScheme bearer = docs.getComponents().getSecuritySchemes().get("Bearer");
        assertNotNull(bearer);
        assertEquals(SecurityScheme.Type.HTTP, bearer.getType());
        assertEquals("bearer", bearer.getScheme());
        assertEquals("JWT", bearer.getBearerFormat());
    }

    private static void assertExternalOperation(Operation operation) {
        assertNotNull(operation);
        assertNotNull(operation.getSummary());
        assertFalse(operation.getSummary().isBlank());
        assertNotNull(operation.getDescription());
        assertTrue(operation.getDescription().contains("parser:task:"), "Document the required JWT permission");
        assertNotNull(operation.getSecurity());
        assertEquals(1, operation.getSecurity().size());
        assertEquals(Map.of("Bearer", List.of()), operation.getSecurity().getFirst());
    }

    private static void assertResponses(Operation operation, String... statuses) {
        assertEquals(Set.of(statuses), operation.getResponses().keySet());
        for (String status : statuses) {
            assertNotNull(operation.getResponses().get(status), "Missing response " + status);
            assertFalse(operation.getResponses().get(status).getDescription().isBlank());
        }
    }
}
