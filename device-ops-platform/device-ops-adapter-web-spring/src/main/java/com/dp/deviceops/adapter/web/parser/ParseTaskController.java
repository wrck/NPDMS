package com.dp.deviceops.adapter.web.parser;

import com.dp.deviceops.parser.runtime.model.ParseResultEnvelope;
import com.dp.deviceops.parser.runtime.model.ParseTask;
import com.dp.deviceops.parser.runtime.model.ParseTaskState;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.runtime.port.ParseResultQueryPort;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository.SubmitOutcome;
import com.dp.deviceops.parser.runtime.port.ParserPayloadStore;
import com.dp.deviceops.parser.runtime.service.ParseTaskService;
import com.dp.deviceops.parser.runtime.service.ParserRuntimeError;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ParseTaskController {

    private final ParseTaskService service;
    private final ParseResultQueryPort queries;
    private final ParserPayloadStore payloads;
    private final ParserControlProperties properties;

    public ParseTaskController(ParseTaskService service, ParseResultQueryPort queries,
            ParserPayloadStore payloads, ParserControlProperties properties) {
        this.service = service;
        this.queries = queries;
        this.payloads = payloads;
        this.properties = properties;
    }

    @PostMapping("/parse-tasks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('SCOPE_parser:task:create')")
    public SubmissionResponse submit(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 200) String idempotencyKey,
            @Valid @RequestBody SubmitRequest request) throws IOException {
        String namespace = namespace(jwt);
        String inputRef = request.inputRef();
        if (inputRef == null || inputRef.isBlank()) {
            if (request.inputContent() == null || request.inputContent().isBlank()) {
                throw new IllegalArgumentException("non-empty inputRef or inputContent is required");
            }
            if (request.inputContent().length() > properties.getMaxInputBytes()) {
                throw new ParserRuntimeError("INPUT_TOO_LARGE", "input exceeds the configured byte limit");
            }
            byte[] content = request.inputContent().getBytes(StandardCharsets.UTF_8);
            if (content.length > properties.getMaxInputBytes()) {
                throw new ParserRuntimeError("INPUT_TOO_LARGE", "input exceeds the configured byte limit");
            }
            inputRef = payloads.putScoped(namespace, request.mediaType() == null ? "text/plain" : request.mediaType(),
                    new ByteArrayInputStream(content));
        } else if (request.inputContent() != null) {
            throw new IllegalArgumentException("inputRef and inputContent are mutually exclusive");
        } else if (!payloads.isOwnedBy(namespace, inputRef)) {
            throw new ParserApiNotFound("INPUT_NOT_FOUND");
        }
        SubmitOutcome outcome = service.submit(new ParseTaskService.SubmitCommand(idempotencyKey,
                namespace, request.logType(), request.releaseId(), request.inputFormat(), inputRef,
                request.contextSnapshot(), null, request.resultConsumerId()));
        return new SubmissionResponse(outcome.taskId(), outcome.state(), outcome.releaseId(), outcome.coordinate(),
                URI.create("/api/v1/parse-tasks/" + outcome.taskId()));
    }

    @GetMapping("/parse-tasks/{taskId}")
    @PreAuthorize("hasAuthority('SCOPE_parser:task:read')")
    public ParseTask getTask(@AuthenticationPrincipal Jwt jwt, @PathVariable("taskId") String taskId) {
        return queries.findTask(namespace(jwt), taskId)
                .orElseThrow(() -> new ParserApiNotFound("TASK_NOT_FOUND"));
    }

    @GetMapping("/parse-tasks")
    @PreAuthorize("hasAuthority('SCOPE_parser:task:read')")
    public List<ParseTask> listTasks(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "limit", defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam(name = "afterTaskId", required = false) String afterTaskId) {
        return queries.listTasks(namespace(jwt), limit, afterTaskId);
    }

    @GetMapping("/parse-results/{resultId}")
    @PreAuthorize("hasAuthority('SCOPE_parser:task:read')")
    public ParseResultEnvelope getResult(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("resultId") String resultId) {
        return queries.findResult(namespace(jwt), resultId)
                .orElseThrow(() -> new ParserApiNotFound("RESULT_NOT_FOUND"));
    }

    @GetMapping("/parse-tasks/{taskId}/result")
    @PreAuthorize("hasAuthority('SCOPE_parser:task:read')")
    public ParseResultEnvelope getTaskResult(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("taskId") String taskId) {
        String namespace = namespace(jwt);
        ParseTask task = queries.findTask(namespace, taskId)
                .orElseThrow(() -> new ParserApiNotFound("TASK_NOT_FOUND"));
        if (task.resultId() == null) {
            throw new ParserRuntimeError("RESULT_NOT_READY", "task has no result; inspect its state before retrying");
        }
        return queries.findResult(namespace, task.resultId())
                .orElseThrow(() -> new ParserApiNotFound("RESULT_NOT_FOUND"));
    }

    @PostMapping("/parse-tasks/{taskId}/cancellations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SCOPE_parser:task:cancel')")
    public void cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable("taskId") String taskId) {
        service.cancel(namespace(jwt), taskId);
    }

    @PostMapping("/parse-tasks/{taskId}/terminations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SCOPE_parser:task:terminate')")
    public void terminate(@AuthenticationPrincipal Jwt jwt, @PathVariable("taskId") String taskId) {
        service.terminateWaiting(namespace(jwt), taskId);
    }

    private String namespace(Jwt jwt) {
        if (jwt == null) {
            throw new AccessDeniedException("caller identity is required");
        }
        String claim = jwt.getClaimAsString(properties.getClientNamespaceClaim());
        String namespace = claim == null || claim.isBlank() ? jwt.getSubject() : claim;
        if (namespace == null || namespace.isBlank() || namespace.length() > 200) {
            throw new AccessDeniedException("valid caller namespace is required");
        }
        return namespace;
    }

    public record SubmitRequest(@NotBlank @Size(max = 200) String logType, @Size(max = 100) String releaseId,
            @NotBlank @Size(max = 100) String inputFormat, @Size(max = 100) String inputRef,
            String inputContent, @Size(max = 200) String mediaType, Map<String, Object> contextSnapshot,
            @Size(max = 200) String resultConsumerId) {
        public SubmitRequest {
            contextSnapshot = contextSnapshot == null ? Map.of() : Map.copyOf(contextSnapshot);
        }
    }

    public record SubmissionResponse(String taskId, ParseTaskState state, String releaseId,
            ParserCoordinate coordinate, URI statusUrl) { }
}
