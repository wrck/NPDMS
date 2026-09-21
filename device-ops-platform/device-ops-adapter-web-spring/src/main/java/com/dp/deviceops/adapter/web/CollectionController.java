package com.dp.deviceops.adapter.web;

import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.core.model.*;
import com.dp.deviceops.core.port.CollectionQueryPort;
import com.dp.deviceops.core.port.CommandExecutionPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.*;

/** Compatible project-scoped collection boundary backed by the shared direct-connection mapper. */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/collections")
public class CollectionController {
    private final CollectionSubmissionCoordinator submissions;
    private final ConnectionRequestMapper connections;
    private final ProjectClaimAuthorizer authorizer;
    private final CollectionQueryPort queries;
    private final CollectionOutputStreamService outputStreams;

    public CollectionController(CollectionSubmissionCoordinator submissions, ConnectionRequestMapper connections,
                                ProjectClaimAuthorizer authorizer, CollectionQueryPort queries,
                                CollectionOutputStreamService outputStreams) {
        this.submissions = submissions;
        this.connections = connections;
        this.authorizer = authorizer;
        this.queries = queries;
        this.outputStreams = outputStreams;
    }

    @GetMapping("/{collectionId}")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public CollectionQueryPort.CollectionDetails get(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable("projectKey") String projectKey,
                                                     @PathVariable("collectionId") @NotBlank String collectionId,
                                                     @RequestParam("namespace") @NotBlank @Size(max = 100) String namespace) {
        authorizer.requireNamespace(jwt, namespace);
        authorizer.require(jwt, projectKey);
        return queries.find(namespace, projectKey, collectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "collection not found"));
    }

    @GetMapping(value = "/{collectionId}/output-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter outputEvents(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("projectKey") String projectKey,
            @PathVariable("collectionId") @NotBlank String collectionId,
            @RequestParam("namespace") @NotBlank String namespace,
            @RequestParam(name = "after", defaultValue = "0") @Min(0) long after) {
        authorizer.requireNamespace(jwt, namespace);
        authorizer.require(jwt, projectKey);
        queries.find(namespace, projectKey, collectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "collection not found"));
        return outputStreams.open(namespace, projectKey, collectionId, after);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
    public CollectionSubmissionCoordinator.Result submit(
            @AuthenticationPrincipal Jwt jwt, @PathVariable("projectKey") String projectKey,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 200) String idempotencyKey,
            @Valid @RequestBody Request request) {
        List<ExecutionConnectionContext> pending = new ArrayList<>();
        try {
            authorizer.requireNamespace(jwt, request.namespace());
            authorizer.require(jwt, projectKey);
            if (!projectKey.equals(request.project().projectKey())
                    || !request.namespace().equals(request.project().namespace())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path and project snapshot differ");
            }
            for (Target target : request.targets()) {
                if (!projectKey.equals(target.project().projectKey())
                        || !request.namespace().equals(target.project().namespace())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "target project snapshot differs");
                }
            }
            String fingerprint = CollectionRequestFingerprint.project(jwt.getSubject(), projectKey, request);
            var existing = submissions.findExisting(request.namespace(), idempotencyKey, fingerprint);
            if (existing.isPresent()) {
                return existing.get();
            }
            List<CollectionSubmissionCoordinator.PreparedTarget> prepared = new ArrayList<>();
            for (Target target : request.targets()) {
                if (!projectKey.equals(target.project().projectKey())
                        || !request.namespace().equals(target.project().namespace())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "target project snapshot differs");
                }
                ConnectionRequestMapper.MappedConnection mapped = connections.map(
                        jwt.getSubject(), request.namespace(), target.toConnection());
                ExecutionConnectionContext context = mapped.executionContext();
                pending.add(context);
                var spec = mapped.spec();
                requireProjectExecutionMode(spec);
                CollectionContextSnapshot snapshot = CollectionContextSnapshot.of(
                        target.project().namespace(), target.project().projectKey(), target.project().projectName(),
                        target.project().projectCode(), target.device().deviceKey(), target.device().deviceName(),
                        target.device().vendor(), target.device().model(),
                        target.extensions() == null ? Map.of() : target.extensions());
                prepared.add(new CollectionSubmissionCoordinator.PreparedTarget(
                        CollectionTarget.forSnapshot(snapshot, spec.protocol(), spec.host(), spec.port(), spec.username(),
                                spec.expectedHostKeyFingerprint(), spec.telnetPrompts()), context));
            }
            CollectionSubmissionCoordinator.Result result = submissions.submit(
                    new CollectionSubmissionCoordinator.Command(request.namespace(), projectKey,
                            request.externalRequestId(), idempotencyKey, prepared, request.script().toArtifact(),
                            request.activityType(), request.callbackUrl(),
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
            pending.clear();
            return result;
        } catch (RuntimeException failure) {
            pending.forEach(ExecutionConnectionContext::close);
            throw failure;
        } finally {
            request.clearCredentials();
        }
    }

    private static void requireProjectExecutionMode(CommandExecutionPort.ConnectionSpec connection) {
        if (connection.protocol() == ConnectionProtocol.SSH2
                && connection.executionMode() != CommandExecutionPort.ExecutionMode.EXEC) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSH2 requires EXEC execution mode");
        }
        if (connection.protocol() == ConnectionProtocol.TELNET
                && connection.executionMode() != CommandExecutionPort.ExecutionMode.SHELL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TELNET requires SHELL execution mode");
        }
    }

    public record Request(@NotBlank @Size(max = 100) String namespace, @NotNull @Valid Project project,
                          @NotEmpty List<@NotNull @Valid Target> targets, @NotNull @Valid Script script,
                          @Size(max = 200) String externalRequestId, @Size(max = 100) String activityType,
                          @Size(max = 2000) String callbackUrl, @Min(1) long commandTimeoutSeconds,
                          @Min(1) long parseTimeoutSeconds, @Min(0) long leaseGraceSeconds,
                          @Valid SemanticParsing semanticParsing) {
        public Request {
            targets = List.copyOf(targets);
        }

        public void clearCredentials() {
            if (targets != null) {
                targets.forEach(Target::clear);
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

    public record Target(@NotNull @Valid Project project, @NotNull @Valid Device device,
                         @NotNull Map<String, @NotBlank @Size(max = 1024) String> extensions,
                         ConnectionProtocol protocol, @Size(max = 500) String host, Integer port,
                         @Size(max = 500) String username, @Size(max = 1000) String hostKeyFingerprint,
                         @Size(max = 100) String authenticationType, @Size(max = 100) String executionMode,
                         @Valid ConnectionRequestMapper.TelnetPrompts telnetPrompts,
                         Long connectTimeoutSeconds,
                         @Size(max = 100) String credentialNamespace,
                         @Size(max = 36) String savedConnectionId,
                         @Size(max = 36) String credentialId,
                         @com.fasterxml.jackson.annotation.JsonProperty(access =
                                 com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) char[] password,
                         @com.fasterxml.jackson.annotation.JsonProperty(access =
                                 com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) char[] privateKey,
                         @com.fasterxml.jackson.annotation.JsonProperty(access =
                                 com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) char[] passphrase) {
        ConnectionRequestMapper.Connection toConnection() {
            boolean savedReference = savedConnectionId != null && !savedConnectionId.isBlank();
            if (credentialId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "credentialId is not supported for execution");
            }
            if (!savedReference && (savedConnectionId != null || credentialNamespace != null)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "direct connection must not contain saved connection selectors");
            }
            if (savedReference && (authenticationType != null || executionMode != null)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "saved connection reference must not contain direct connection fields");
            }
            CommandExecutionPort.AuthenticationType authentication = null;
            CommandExecutionPort.ExecutionMode execution = null;
            if (!savedReference && authenticationType != null) {
                try {
                    authentication = CommandExecutionPort.AuthenticationType.valueOf(authenticationType);
                } catch (IllegalArgumentException exception) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "authentication type is invalid");
                }
            }
            if (!savedReference) {
                try {
                    execution = executionMode == null ? null : CommandExecutionPort.ExecutionMode.valueOf(executionMode);
                } catch (IllegalArgumentException exception) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "execution mode is invalid");
                }
                if (protocol == ConnectionProtocol.SSH2 && execution != CommandExecutionPort.ExecutionMode.EXEC) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSH2 requires EXEC execution mode");
                }
                if (protocol == ConnectionProtocol.TELNET && execution != CommandExecutionPort.ExecutionMode.SHELL) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TELNET requires SHELL execution mode");
                }
            }
            return new ConnectionRequestMapper.Connection(protocol, host, port, username, authentication,
                    execution, hostKeyFingerprint, telnetPrompts, connectTimeoutSeconds, credentialNamespace,
                    savedConnectionId, credentialId, password, privateKey, passphrase);
        }

        void clear() {
            ConnectionRequestMapper.clear(password);
            ConnectionRequestMapper.clear(privateKey);
            ConnectionRequestMapper.clear(passphrase);
        }
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
