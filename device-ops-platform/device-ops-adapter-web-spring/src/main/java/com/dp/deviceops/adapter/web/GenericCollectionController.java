package com.dp.deviceops.adapter.web;

import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.core.model.*;
import com.dp.deviceops.core.port.CollectionQueryPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/collections")
public class GenericCollectionController {
    private final ConnectionRequestMapper connections;
    private final CollectionSubmissionCoordinator submissions;
    private final CollectionQueryPort queries;
    private final ProjectClaimAuthorizer authorizer;
    private final CollectionOutputStreamService outputStreams;

    public GenericCollectionController(ConnectionRequestMapper connections, CollectionSubmissionCoordinator submissions,
                                       CollectionQueryPort queries, ProjectClaimAuthorizer authorizer,
                                       CollectionOutputStreamService outputStreams) {
        this.connections = connections;
        this.submissions = submissions;
        this.queries = queries;
        this.authorizer = authorizer;
        this.outputStreams = outputStreams;
    }

    @GetMapping("/{collectionId}")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public CollectionQueryPort.CollectionDetails get(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("collectionId") @NotBlank String collectionId,
            @RequestParam("namespace") @NotBlank @Size(max = 100) String namespace) {
        authorizer.requireNamespace(jwt, namespace);
        var collection = queries.find(namespace, collectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "collection not found"));
        if (collection.projectKey() != null && !collection.projectKey().isBlank()) {
            authorizer.require(jwt, collection.projectKey());
        }
        return collection;
    }

    @GetMapping(value = "/{collectionId}/output-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter outputEvents(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("collectionId") @NotBlank String collectionId,
            @RequestParam("namespace") @NotBlank String namespace,
            @RequestParam(name = "after", defaultValue = "0") @Min(0) long after) {
        authorizer.requireNamespace(jwt, namespace);
        CollectionQueryPort.CollectionDetails collection = queries.find(namespace, collectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "collection not found"));
        if (collection.projectKey() != null && !collection.projectKey().isBlank()) {
            authorizer.require(jwt, collection.projectKey());
        }
        return outputStreams.open(namespace, collection.projectKey(), collectionId, after);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
    public CollectionSubmissionCoordinator.Result submit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 200) String idempotencyKey,
            @Valid @RequestBody Request request) {
        try {
            authorizer.requireNamespace(jwt, request.namespace());
            Project project = request.context() == null ? null : request.context().project();
            Device device = request.context() == null ? null : request.context().device();
            if (project != null) {
                if (!request.namespace().equals(project.namespace())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "project namespace differs");
                }
                authorizer.require(jwt, project.projectKey());
            }
            if (request.callbackUrl() != null && (project == null || device == null)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "callback requires project and device context");
            }
            String fingerprint = CollectionRequestFingerprint.generic(jwt.getSubject(), request);
            var existing = submissions.findExisting(request.namespace(), idempotencyKey, fingerprint);
            if (existing.isPresent()) {
                return existing.get();
            }
            ConnectionRequestMapper.MappedConnection mapped = connections.map(
                    jwt.getSubject(), request.namespace(), request.connection());
            boolean dispatched = false;
            try {
                CollectionContextSnapshot snapshot = CollectionContextSnapshot.ofOptional(
                        project == null ? null : new CollectionContextSnapshot.ProjectSnapshot(project.namespace(),
                                project.projectKey(), project.projectName(), project.projectCode()),
                        device == null ? null : new CollectionContextSnapshot.DeviceSnapshot(device.deviceKey(),
                                device.deviceName(), device.vendor(), device.model()),
                        request.context() == null || request.context().extensions() == null
                                ? Map.of() : request.context().extensions());
                var spec = mapped.spec();
                CollectionTarget target = CollectionTarget.forSnapshot(snapshot, spec.protocol(), spec.host(), spec.port(),
                        spec.username(), spec.expectedHostKeyFingerprint(), spec.telnetPrompts());
                CollectionSubmissionCoordinator.Result result = submissions.submit(
                        new CollectionSubmissionCoordinator.Command(
                                request.namespace(), project == null ? null : project.projectKey(),
                                request.externalRequestId(), idempotencyKey,
                                java.util.List.of(new CollectionSubmissionCoordinator.PreparedTarget(
                                        target, mapped.executionContext())),
                                request.script().toArtifact(), request.activityType(), request.callbackUrl(),
                                Duration.ofSeconds(request.commandTimeoutSeconds()),
                                Duration.ofSeconds(request.parseTimeoutSeconds()),
                                Duration.ofSeconds(request.leaseGraceSeconds()),
                                request.semanticParsing() == null ? null
                                        : new CollectionSubmissionCoordinator.SemanticParsing(
                                                request.semanticParsing().enabled(),
                                                request.semanticParsing().logType(),
                                                request.semanticParsing().releaseId(),
                                                request.semanticParsing().inputFormat(),
                                                request.semanticParsing().resultConsumerId()), fingerprint, CollectionSubmissionSnapshot.current()));
                dispatched = true;
                return result;
            } finally {
                if (!dispatched) {
                    mapped.close();
                }
            }
        } finally {
            request.clearCredentials();
        }
    }

    public record Request(@NotBlank @Size(max = 100) String namespace, @Valid Context context,
                          @NotNull @Valid ConnectionRequestMapper.Connection connection,
                          @NotNull @Valid Script script, @Size(max = 200) String externalRequestId,
                          @Size(max = 100) String activityType, @Size(max = 2000) String callbackUrl,
                          @Min(1) long commandTimeoutSeconds, @Min(1) long parseTimeoutSeconds,
                          @Min(0) long leaseGraceSeconds,
                          @Valid SemanticParsing semanticParsing) {
        public void clearCredentials() {
            if (connection != null) {
                connection.clearCredentials();
            }
        }
    }

    public record SemanticParsing(Boolean enabled, String logType, String releaseId,
                                  String inputFormat, String resultConsumerId) {
        public SemanticParsing {
            enabled = enabled == null || enabled;
            if (enabled && (logType == null || logType.isBlank() || inputFormat == null || inputFormat.isBlank())) {
                throw new IllegalArgumentException("enabled semantic parsing requires logType and inputFormat");
            }
        }
    }

    public record Context(@Valid Project project, @Valid Device device,
                          Map<String, @NotBlank @Size(max = 1024) String> extensions) {
    }

    public record Project(@NotBlank String namespace, @NotBlank String projectKey,
                          @Size(max = 500) String projectName, @Size(max = 200) String projectCode) {
    }

    public record Device(@NotBlank String deviceKey, @Size(max = 500) String deviceName,
                         @Size(max = 200) String vendor, @Size(max = 200) String model) {
    }

    public record Script(@NotBlank String source, @NotBlank String key, @NotBlank String version,
                         @NotNull String content, @Pattern(regexp = "[0-9a-fA-F]{64}") String sha256,
                         @NotBlank String policy, @NotBlank String parserType,
                         @Size(max = 10000) String parserConfig) {
        ScriptArtifact toArtifact() {
            return switch (ScriptArtifact.ScriptSource.valueOf(source)) {
                case LOCAL_MANAGED -> ScriptArtifact.local(key, version, content, sha256,
                        ScriptArtifact.PersistencePolicy.valueOf(policy), parserType, parserConfig);
                case EXTERNAL_DELIVERED -> ScriptArtifact.external(key, version, content, sha256,
                        ScriptArtifact.PersistencePolicy.valueOf(policy), parserType, parserConfig);
                case ADHOC_INLINE -> ScriptArtifact.adHoc(key, version, content, sha256, parserType, parserConfig);
            };
        }
    }
}
