package com.dp.deviceops.server;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
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

/** External collection documentation only; it does not alter runtime security or parser API documentation. */
@Configuration(proxyBeanMethods = false)
public class ExternalCollectionOpenApiConfiguration {
    private static final String BEARER = "CollectionBearer";
    private static final String COLLECTIONS = "/api/v1/collections";
    private static final String COLLECTION = COLLECTIONS + "/{collectionId}";
    private static final String PROJECT_COLLECTIONS = "/api/v1/projects/{projectKey}/collections";
    private static final String PROJECT_COLLECTION = PROJECT_COLLECTIONS + "/{collectionId}";
    private static final String NAMESPACE = "namespace is required and must be authorized by the trusted JWT "
            + "device_ops_namespaces array or client_namespace string; when neither claim is present, only the "
            + "JWT subject namespace is allowed. A nonblank JWT subject is required. Namespace wildcard access "
            + "requires an explicit device_ops_namespaces entry of '*'. ";
    private static final String PROJECT_ACCESS = "A project-bound collection also requires the project key in "
            + "the JWT device_ops_projects claim, including access through generic collection URLs. ";
    private static final String READ_ACCESS = "Requires JWT scope device-ops:collections:read. The query parameter "
            + NAMESPACE + PROJECT_ACCESS;
    private static final String SUBMIT_ACCESS = "Requires JWT scope device-ops:collections:execute. The request body "
            + NAMESPACE + PROJECT_ACCESS
            + "The Idempotency-Key header is required (nonblank, at most 200 characters). An identical request "
            + "by the same subject in the same namespace returns the existing collection without executing again. "
            + "Reusing the key with another subject or different submission content returns 409. A 202 response "
            + "acknowledges asynchronous execution; poll the collection status with namespace or consume output-events. "
            + "Set semanticParsing.enabled to false to opt out of automatic semantic parsing. ";
    private static final String STREAM = "Returns text/event-stream with persisted output events and a final complete "
            + "event. The optional after cursor defaults to 0 and must be nonnegative; only output events with "
            + "sequence IDs greater than after are replayed. Resume with the last received output event ID. "
            + "The complete event includes status and lastSequence. Disconnecting never cancels execution.";
    private static final String EVIDENCE = "Returns four evidence groups: metadata, the frozen script input "
            + "(source/key/version/policy/parserType/sha256, contentStatus and content), the submission group "
            + "(provenance, snapshot and omittedFields) and per-target executionFacts with the pinned semantic "
            + "parsing coordinates. New tasks store a CAPTURED_SUBMISSION whitelist snapshot that never contains "
            + "connection passwords, private keys, passphrases or the script body; historical tasks return "
            + "RECONSTRUCTED_FACTS with a null snapshot. The response is Cache-Control: no-store. Script content "
            + "and snapshots are sensitive operational evidence; do not log them or place them in URLs or "
            + "browser storage. Saved credentials are neither returned nor decrypted.";

    private static final Map<String, Documentation> OPERATIONS = Map.of(
            COLLECTIONS, new Documentation(HttpMethod.POST, "Submit a direct collection",
                    SUBMIT_ACCESS + "Use the direct nested connection object and script artifact; project/device "
                            + "context is optional. The script sha256 must match its UTF-8 content."),
            COLLECTION, new Documentation(HttpMethod.GET, "Get collection status and output",
                    READ_ACCESS + "Returns collection and target status, stdout/stderr, exit code and command blocks. "
                            + "Transient credentials and the script content field are not returned; command blocks "
                            + "still contain command text and output and must be treated as sensitive evidence."),
            COLLECTION + "/output-events", new Documentation(HttpMethod.GET, "Stream or replay collection output",
                    READ_ACCESS + STREAM),
            COLLECTION + "/semantic-results", new Documentation(HttpMethod.GET, "Get collection semantic results",
                    READ_ACCESS + "Returns per-target asynchronous semantic task state and available results. "
                            + "An empty list is valid when semantic parsing is disabled or no semantic task exists yet."),
            COLLECTION + "/evidence", new Documentation(HttpMethod.GET, "Get collection submission and execution evidence",
                    READ_ACCESS + EVIDENCE),
            PROJECT_COLLECTIONS, new Documentation(HttpMethod.POST, "Submit a project collection",
                    SUBMIT_ACCESS + "Use the project snapshot and flattened targets connection fields. The path "
                            + "projectKey, project snapshot and every target project/namespace must agree. SSH2 "
                            + "targets use EXEC mode; each script sha256 must match its UTF-8 content."),
            PROJECT_COLLECTION, new Documentation(HttpMethod.GET, "Get project collection status and output",
                    READ_ACCESS + "The collection must belong to both the requested namespace and path projectKey. "
                            + "Returns collection and target status with command output."),
            PROJECT_COLLECTION + "/output-events", new Documentation(HttpMethod.GET, "Stream or replay project collection output",
                    READ_ACCESS + "The collection must belong to the path projectKey. " + STREAM),
            PROJECT_COLLECTION + "/evidence", new Documentation(HttpMethod.GET, "Get project collection submission and execution evidence",
                    READ_ACCESS + "The collection must belong to the path projectKey. " + EVIDENCE));

    @Bean
    GroupedOpenApi externalCollectionOpenApi() {
        return GroupedOpenApi.builder().group("external-collection").displayName("External collection API")
                .pathsToMatch(OPERATIONS.keySet().toArray(String[]::new))
                .addOpenApiCustomizer(this::retainExternalOperations).build();
    }

    @Bean
    GlobalOpenApiCustomizer externalCollectionDocumentation() {
        return openApi -> {
            if (openApi.getPaths() == null || openApi.getPaths().entrySet().stream().noneMatch(entry -> {
                Documentation metadata = OPERATIONS.get(entry.getKey());
                return metadata != null && entry.getValue().readOperationsMap().containsKey(metadata.method());
            })) return;
            if (openApi.getComponents() == null) openApi.setComponents(new Components());
            // A distinct name preserves the external-parser Bearer description and authentication contract.
            openApi.getComponents().addSecuritySchemes(BEARER, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                    .description("Bearer JWT from the configured identity provider. Collection submission requires "
                            + "device-ops:collections:execute; reads and SSE require device-ops:collections:read. "
                            + "Namespace and project access are constrained by trusted token claims."));
            openApi.getPaths().forEach((path, item) -> {
                Documentation metadata = OPERATIONS.get(path);
                if (metadata != null) {
                    Operation operation = item.readOperationsMap().get(metadata.method());
                    if (operation != null) describe(operation, metadata);
                }
            });
        };
    }

    private void retainExternalOperations(OpenAPI openApi) {
        if (openApi.getPaths() == null) return;
        openApi.getPaths().entrySet().removeIf(entry -> !OPERATIONS.containsKey(entry.getKey()));
        openApi.getPaths().forEach((path, item) -> {
            for (HttpMethod method : HttpMethod.values()) {
                if (method != OPERATIONS.get(path).method()) item.operation(method, null);
            }
        });
        openApi.getPaths().entrySet().removeIf(entry -> entry.getValue().readOperations().isEmpty());
    }

    private static void describe(Operation operation, Documentation metadata) {
        operation.setSummary(metadata.summary());
        operation.setDescription(metadata.description());
        operation.setSecurity(List.of(new SecurityRequirement().addList(BEARER)));
        if (operation.getResponses() == null) operation.setResponses(new ApiResponses());
        if (metadata.method() == HttpMethod.POST) {
            response(operation, "202", "Collection accepted, or an identical submission replayed without re-execution.");
            response(operation, "400", "Invalid collection request, namespace/project binding, script hash or Idempotency-Key.");
            response(operation, "409", "Idempotency key conflicts with a different submission or authenticated subject.");
            response(operation, "429", "Collection queue is full; a task may already be persisted or partially dispatched. "
                    + "Retry the same Idempotency-Key to discover the existing task; do not blindly submit a new key.");
        } else {
            response(operation, "200", "Collection evidence, semantic results or an output event stream.");
            response(operation, "400", "Missing/invalid required namespace or invalid output replay cursor.");
            response(operation, "404", "Collection does not exist in the requested namespace and project binding.");
            requireNamespaceParameter(operation);
        }
        response(operation, "401", "Bearer JWT is missing, invalid or expired.");
        response(operation, "403", "Missing collection scope, JWT subject, namespace permission or project permission.");
    }

    private static void requireNamespaceParameter(Operation operation) {
        Parameter namespace = operation.getParameters() == null ? null : operation.getParameters().stream()
                .filter(parameter -> "namespace".equals(parameter.getName()) && "query".equals(parameter.getIn()))
                .findFirst().orElse(null);
        if (namespace == null) {
            namespace = new Parameter().name("namespace").in("query").schema(new StringSchema());
            operation.addParametersItem(namespace);
        }
        namespace.setRequired(true);
        namespace.setDescription("Required collection namespace, authorized by the caller's trusted JWT claims.");
    }

    private static void response(Operation operation, String status, String description) {
        // Enrich descriptions without replacing Springdoc-generated response bodies and schemas.
        operation.getResponses().computeIfAbsent(status, ignored -> new ApiResponse()).setDescription(description);
    }

    private record Documentation(HttpMethod method, String summary, String description) { }
}
