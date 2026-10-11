package com.dp.deviceops.server;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class ExternalParserOpenApiConfiguration {

    private static final String BEARER = "Bearer";
    private static final String TASKS = "/api/v1/parse-tasks";
    private static final String TASK = TASKS + "/{taskId}";
    private static final String TASK_RESULT = TASK + "/result";
    private static final String RESULT = "/api/v1/parse-results/{resultId}";
    private static final String LOGS = "/api/v1/log-parses";
    private static final String LOG = LOGS + "/{requestId}";
    private static final String READ_ACCESS = "Requires JWT scope parser:task:read. "
            + "Access is restricted to the caller namespace derived from the JWT.";

    private static final Map<String, Map<HttpMethod, OperationDocumentation>> OPERATIONS = Map.of(
            LOGS, Map.of(HttpMethod.POST, new OperationDocumentation(
                    "Parse raw device text or a saved collection log",
                    "Requires parser:task:create or device-ops:collections:execute. Explicit namespace and projectKey "
                            + "must be authorized by the JWT. Supply inputText or sourceCollectionId, never both. "
                            + "A saved collection additionally requires device-ops:collections:read. requestId is the "
                            + "namespace-scoped idempotency key. Input is sanitized before persistence; no device command "
                            + "is executed. The active published parser release is pinned on acceptance.",
                    Map.of("202", "Parse accepted or identical submission replayed; poll by requestId.",
                            "400", "Invalid source selection, log text or request identifier.",
                            "404", "Collection or active parser release not found.",
                            "409", "Request identifier already binds different input or parser release is unavailable.",
                            "413", "Log exceeds the configured input size limit."))),
            LOG, Map.of(HttpMethod.GET, new OperationDocumentation(
                    "Get log parse status and structured result by request ID",
                    "Requires parser:task:read or device-ops:collections:read. The namespace query parameter and "
                            + "the task project must be authorized by the JWT. Returns the fixed release, warnings "
                            + "and result envelope when available. A status query never submits or re-executes work.",
                    Map.of("200", "Current parsing state and optional structured result.",
                            "400", "Invalid request identifier or namespace.",
                            "404", "Task or result not found in this namespace."))),
            TASKS, Map.of(
                    HttpMethod.POST, new OperationDocumentation(
                            "Submit an asynchronous parse task",
                            "Requires JWT scope parser:task:create. Send an Idempotency-Key header; replaying "
                                    + "the same submission in the caller namespace returns the existing task, while "
                                    + "reusing the key for different content returns 409. Supply the parser log type, "
                                    + "input format and input content as described by the request schema. The task "
                                    + "pins the selected published release (or the active release when omitted). "
                                    + "A 202 response acknowledges submission, not parsing completion; poll the "
                                    + "returned statusUrl, then retrieve /api/v1/parse-tasks/{taskId}/result.",
                            Map.of(
                                    "202", "Task accepted or an identical idempotent submission replayed; processing is asynchronous.",
                                    "400", "Invalid request body, input selection or missing/invalid Idempotency-Key header.",
                                    "404", "Requested parser release or active release was not found, or INPUT_NOT_FOUND: "
                                            + "the input reference does not exist in the caller namespace.",
                                    "409", "IDEMPOTENCY_CONFLICT or the selected release is not published for this log type.",
                                    "413", "Input content exceeds the configured payload size limit.",
                                    "422", "Input format, parser semantics or result consumer configuration cannot be processed.",
                                    "503", "Parser artifacts or backing storage are temporarily unavailable.")),
                    HttpMethod.GET, new OperationDocumentation(
                            "List parse tasks for the caller",
                            READ_ACCESS + " Returns a cursor-based page; limit defaults to 50 and must be "
                                    + "between 1 and 200. Use afterTaskId to continue from the previous page. "
                                    + "Supply externalRequestId to list only the tasks derived from the collection "
                                    + "submission that carried that business request identifier.",
                            Map.of("200", "Parse tasks in the caller namespace; the list may be empty.",
                                    "400", "Invalid limit or pagination parameters."))),
            TASK, Map.of(HttpMethod.GET, new OperationDocumentation(
                    "Get parse task status",
                    READ_ACCESS + " Poll this endpoint to inspect asynchronous task state, pinned parser "
                            + "release and wait reason before retrieving its result.",
                    Map.of("200", "Current parse task state and metadata.",
                            "404", "TASK_NOT_FOUND: the task does not exist in the caller namespace."))),
            RESULT, Map.of(HttpMethod.GET, new OperationDocumentation(
                    "Get a parse result by result ID",
                    READ_ACCESS + " Returns the persisted parse result envelope, including structured output "
                            + "and parser provenance, for a known resultId.",
                    Map.of("200", "Persisted parse result envelope.",
                            "404", "RESULT_NOT_FOUND: the result does not exist in the caller namespace."))),
            TASK_RESULT, Map.of(HttpMethod.GET, new OperationDocumentation(
                    "Get a parse result by task ID",
                    READ_ACCESS + " Retrieve the persisted result without first knowing its resultId. "
                            + "If the task exists but no result is available, inspect its status before polling again.",
                    Map.of("200", "Persisted parse result envelope for the task.",
                            "404", "TASK_NOT_FOUND: the task does not exist in the caller namespace.",
                            "409", "RESULT_NOT_READY: the task exists but its parse result is not ready."))));

    @Bean
    GroupedOpenApi externalParserOpenApi() {
        return GroupedOpenApi.builder()
                .group("external-parser")
                .displayName("External parser API")
                .pathsToMatch(TASKS, TASK, RESULT, TASK_RESULT, LOGS, LOG)
                .addOpenApiCustomizer(this::retainExternalOperations)
                .build();
    }

    @Bean
    GlobalOpenApiCustomizer externalParserDocumentation() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSecuritySchemes(BEARER, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Bearer JWT issued by the configured identity provider. Submission requires "
                            + "parser:task:create; task and result queries require parser:task:read. "
                            + "The caller namespace is derived from the token for parse-tasks; log-parses validates "
                            + "its explicit namespace and project against the token claims."));
            // Only the allowlisted operations require Bearer; public routes keep their generated security.
            if (openApi.getPaths() != null) {
                openApi.getPaths().forEach((path, item) -> {
                    Map<HttpMethod, OperationDocumentation> documentation = OPERATIONS.get(path);
                    if (documentation != null) {
                        item.readOperationsMap().forEach((method, operation) -> {
                            OperationDocumentation metadata = documentation.get(method);
                            if (metadata != null) {
                                describe(operation, metadata);
                            }
                        });
                    }
                });
            }
        };
    }

    private void retainExternalOperations(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        // Path matching alone would admit future DELETE/POST/admin operations on these paths.
        openApi.getPaths().entrySet().removeIf(entry -> !OPERATIONS.containsKey(entry.getKey()));
        openApi.getPaths().forEach((path, item) -> {
            for (HttpMethod method : HttpMethod.values()) {
                if (!OPERATIONS.get(path).containsKey(method)) {
                    item.operation(method, null);
                }
            }
        });
        openApi.getPaths().entrySet().removeIf(entry -> entry.getValue().readOperations().isEmpty());
    }

    private static void describe(Operation operation, OperationDocumentation documentation) {
        operation.setSummary(documentation.summary());
        operation.setDescription(documentation.description());
        operation.setSecurity(List.of(new SecurityRequirement().addList(BEARER)));
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        documentation.responses().forEach((status, description) -> describeResponse(operation, status, description));
        describeResponse(operation, "401", "Bearer JWT is missing, invalid or expired.");
        describeResponse(operation, "403", "Authenticated caller lacks the required parser task scope "
                + "or has a missing/invalid caller namespace identity.");
    }

    private static void describeResponse(Operation operation, String status, String description) {
        // Enrich descriptions in place: Springdoc owns the generated request and response schemas.
        operation.getResponses().computeIfAbsent(status, ignored -> new ApiResponse()).setDescription(description);
    }

    private record OperationDocumentation(String summary, String description, Map<String, String> responses) { }
}
