package com.dp.deviceops.adapter.web;

import com.dp.deviceops.adapter.web.concurrency.KeyedCollectionDispatcher;
import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.core.port.CollectionQueryPort;
import com.dp.deviceops.core.port.CollectionRepository;
import com.dp.deviceops.core.port.CallbackOutboxPort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import jakarta.validation.Valid;

/** Resolve NPDMS tasks by the exact, namespace-scoped submission idempotency key. */
@RestController
@RequestMapping("/api/v1/npdms/collections")
public class NpdmsCollectionController {
    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "TIMED_OUT", "CANCELLED");
    private final CollectionRepository repository;
    private final CollectionQueryPort queries;
    private final ProjectClaimAuthorizer authorizer;
    private final KeyedCollectionDispatcher dispatcher;
    private final CallbackOutboxPort callbacks;
    private final GenericCollectionController collections;
    private final com.dp.deviceops.adapter.web.parser.CollectionSemanticResultController semanticResults;
    private final String requestSigningKey;
    @org.springframework.beans.factory.annotation.Autowired
    private com.dp.deviceops.core.port.SavedConnectionStore savedConnections;

    public NpdmsCollectionController(CollectionRepository repository, CollectionQueryPort queries,
                                    ProjectClaimAuthorizer authorizer, KeyedCollectionDispatcher dispatcher,
                                    CallbackOutboxPort callbacks, GenericCollectionController collections,
                                    com.dp.deviceops.adapter.web.parser.CollectionSemanticResultController semanticResults,
                                    @Value("${device-ops.npdms.request-signing-key:}") String requestSigningKey) {
        this.repository = repository;
        this.queries = queries;
        this.authorizer = authorizer;
        this.dispatcher = dispatcher;
        this.callbacks = callbacks;
        this.collections = collections;
        this.semanticResults = semanticResults;
        this.requestSigningKey = requestSigningKey;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
    public CollectionSubmissionCoordinator.Result submit(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String taskId,
            @RequestHeader("X-DAC-Grant-Time") String timestamp,
            @RequestHeader("X-DAC-Grant") String signature,
            @Valid @RequestBody GenericCollectionController.Request request) {
        try {
            verifyGrant(jwt, taskId, timestamp, signature, request);
            return collections.submit(jwt, taskId, request);
        } finally {
            request.clearCredentials();
        }
    }

    private void verifyGrant(Jwt jwt, String id, String timestamp, String signature, GenericCollectionController.Request request) {
        try {
            var utf8 = java.nio.charset.StandardCharsets.UTF_8;
            var connection = request.connection();
            com.dp.deviceops.core.model.SavedConnection saved = null;
            if (connection.savedConnectionId() != null) {
                authorizer.requireNamespace(jwt, request.namespace());
                saved = savedConnections.find(jwt.getSubject(), request.namespace(), connection.savedConnectionId()).orElseThrow();
                if (connection.savedConnectionVersion() == null || connection.savedConnectionVersion() != saved.version()) throw new IllegalArgumentException();
            }
            var authentication = saved == null ? connection.authenticationType() : saved.connection().authenticationType();
            var mode = saved == null ? connection.executionMode() : saved.connection().executionMode();
            long now = java.time.Instant.now().getEpochSecond(), issued = Long.parseLong(timestamp);
            if (requestSigningKey.getBytes(utf8).length < 32 || issued < now - 60 || issued > now + 60
                    || !id.equals(request.externalRequestId()) || request.context() == null
                    || request.context().project() == null || request.context().device() == null
                    || authentication != com.dp.deviceops.core.port.CommandExecutionPort.AuthenticationType.PASSWORD
                    || mode != com.dp.deviceops.core.port.CommandExecutionPort.ExecutionMode.SHELL
                    || !"EXTERNAL_DELIVERED".equals(request.script().source())
                    || !"EXECUTION_ONLY".equals(request.script().policy())) throw new IllegalArgumentException();
            String binding = java.util.stream.Stream.of(timestamp, request.namespace(), id, request.context().project().projectKey(),
                    request.context().device().deviceKey(), saved == null ? connection.host() : saved.connection().host(),
                    saved == null ? connection.port().toString() : Integer.toString(saved.connection().port()),
                    saved == null ? connection.protocol().name() : saved.connection().protocol().name(),
                    saved == null ? connection.username() : saved.connection().username(), request.script().key(),
                    request.script().version(), request.script().sha256().toLowerCase(java.util.Locale.ROOT),
                    Long.toString(request.commandTimeoutSeconds()), request.callbackUrl() == null ? "" : request.callbackUrl())
                    .map(value -> value.length() + ":" + value).collect(java.util.stream.Collectors.joining());
            if (saved != null) {
                String version = Long.toString(saved.version());
                binding += saved.id().length() + ":" + saved.id() + version.length() + ":" + version;
            }
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(requestSigningKey.getBytes(utf8), "HmacSHA256"));
            if (!java.security.MessageDigest.isEqual(mac.doFinal(binding.getBytes(utf8)), java.util.HexFormat.of().parseHex(signature))) {
                throw new IllegalArgumentException();
            }
        } catch (Exception invalid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "task execution grant rejected");
        }
    }

    @GetMapping("/{platformTaskId}")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public CollectionQueryPort.CollectionDetails get(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("platformTaskId") String platformTaskId, @RequestParam("namespace") String namespace) {
        authorizer.requireNamespace(jwt, namespace);
        if (repository.isCancelledBeforeSubmission(namespace, platformTaskId)) {
            authorizer.require(jwt, "*");
            throw new ResponseStatusException(HttpStatus.GONE, "CANCELLED_BEFORE_DISPATCH") {
                @Override public org.springframework.http.HttpHeaders getHeaders() {
                    var headers = new org.springframework.http.HttpHeaders();
                    headers.set("X-DAC-Cancellation", "BEFORE_DISPATCH");
                    return headers;
                }
            };
        }
        var task = repository.findByIdempotencyKey(namespace, platformTaskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!platformTaskId.equals(task.externalRequestId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "external request binding differs");
        }
        task.projectKey().ifPresent(project -> authorizer.require(jwt, project));
        return queries.find(namespace, task.taskId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** Structured parse records of the collection initiated by this platform task, keyed by the initiating business object. */
    @GetMapping("/{platformTaskId}/semantic-results")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public java.util.List<com.dp.deviceops.adapter.web.parser.CollectionSemanticResultController.CollectionSemanticResult>
    semanticResults(@AuthenticationPrincipal Jwt jwt, @PathVariable("platformTaskId") String platformTaskId,
                    @RequestParam("namespace") String namespace) {
        var task = get(jwt, platformTaskId, namespace);
        return semanticResults.list(jwt, task.collectionId(), namespace);
    }

    @PostMapping("/{platformTaskId}/cancellations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
    public void cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable("platformTaskId") String platformTaskId,
                       @RequestParam("namespace") String namespace) {
        authorizer.requireNamespace(jwt, namespace);
        if (platformTaskId == null || platformTaskId.isBlank() || platformTaskId.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (repository.findByIdempotencyKey(namespace, platformTaskId).isEmpty()) {
            // A missing request has no project to authorize against. Only the namespace's all-project service may fence it.
            authorizer.require(jwt, "*");
            if (repository.cancelBeforeSubmission(namespace, platformTaskId)) return;
        }
        var task = get(jwt, platformTaskId, namespace);
        for (var target : task.targets()) {
            if (!TERMINAL.contains(target.status().name()) && !dispatcher.cancel(target.targetId())) {
                // Never acknowledge cancellation of an execution owned by another process.
                throw new ResponseStatusException(HttpStatus.CONFLICT, "execution owner is unavailable; query and retry");
            }
        }
    }

    @PostMapping("/{platformTaskId}/result-redeliveries")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
    public void redeliver(@AuthenticationPrincipal Jwt jwt, @PathVariable("platformTaskId") String platformTaskId,
                          @RequestParam("namespace") String namespace) {
        var task = get(jwt, platformTaskId, namespace);
        if (!TERMINAL.contains(task.status().name())) throw new ResponseStatusException(HttpStatus.CONFLICT);
        callbacks.redeliverTerminalResult(task.collectionId());
    }
}
