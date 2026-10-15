package com.dp.deviceops.adapter.web.parser;

import com.dp.deviceops.core.port.CollectionQueryPort;
import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.parser.runtime.model.ParseResultEnvelope;
import com.dp.deviceops.parser.runtime.model.ParseTask;
import com.dp.deviceops.parser.runtime.port.ParseResultQueryPort;
import com.dp.deviceops.parser.runtime.port.ParserPayloadStore;
import com.dp.deviceops.parser.runtime.service.ParseTaskService;
import com.dp.deviceops.parser.runtime.service.ParserRuntimeError;
import com.dp.deviceops.parser.semantic.cli.SemanticParserInput;
import com.dp.deviceops.parser.semantic.input.DeviceLogInputNormalizer;
import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Standalone log parsing. No device connection, collection submission, or business completion is performed. */
@RestController
@RequestMapping("/api/v1/log-parses")
public class DeviceLogParseController {
    private static final String PREFIX = "device-log:";
    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "TIMED_OUT", "CANCELLED");
    private final ParseTaskService service;
    private final ParseResultQueryPort results;
    private final ParserPayloadStore payloads;
    private final ParserControlProperties properties;
    private final ProjectClaimAuthorizer authorizer;
    private final CollectionQueryPort collections;
    private final DeviceLogInputNormalizer normalizer = new DeviceLogInputNormalizer();

    public DeviceLogParseController(ParseTaskService service, ParseResultQueryPort results,
            ParserPayloadStore payloads, ParserControlProperties properties,
            ProjectClaimAuthorizer authorizer, CollectionQueryPort collections) {
        this.service = service;
        this.results = results;
        this.payloads = payloads;
        this.properties = properties;
        this.authorizer = authorizer;
        this.collections = collections;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyAuthority('SCOPE_parser:task:create','SCOPE_device-ops:collections:execute')"
            + " and (#p1.sourceCollectionId() == null or hasAuthority('SCOPE_device-ops:collections:read'))")
    public View submit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Submit request) throws IOException {
        authorizer.requireNamespace(jwt, request.namespace());
        authorizer.require(jwt, request.projectKey());
        boolean collected = request.sourceCollectionId() != null;
        if (collected == (request.inputText() != null)) throw new IllegalArgumentException("LOG_SOURCE_INVALID");
        if (collected && request.commandHint() != null) throw new IllegalArgumentException("COLLECTED_LOG_HINT_INVALID");
        var context = new LinkedHashMap<String, Object>();
        context.put("projectKey", request.projectKey());
        context.put("sourceKind", collected ? "COLLECTION" : "UPLOAD");
        context.put("executionVerified", collected);
        DeviceLogInputNormalizer.Normalized input;
        if (collected) {
            var collection = collections.find(request.namespace(), request.projectKey(), request.sourceCollectionId())
                    .orElseThrow(() -> new ParserApiNotFound("COLLECTION_NOT_FOUND"));
            if (!Objects.equals(request.projectKey(), collection.projectKey())
                    || !TERMINAL.contains(collection.status().name()) || collection.targets().size() != 1) {
                throw new IllegalArgumentException("COLLECTION_LOG_NOT_AVAILABLE");
            }
            var target = collection.targets().getFirst();
            List<CommandOutputBlock> blocks = target.commandBlocks();
            if (blocks.isEmpty()) {
                CommandBlockStatus status = switch (target.status()) {
                    case SUCCEEDED -> CommandBlockStatus.SUCCEEDED;
                    case TIMED_OUT -> CommandBlockStatus.TIMED_OUT;
                    case CANCELLED -> CommandBlockStatus.CANCELLED;
                    default -> CommandBlockStatus.FAILED;
                };
                blocks = List.of(new CommandOutputBlock(1, "", status, target.stdout(), target.stderr(),
                        0, 0, target.truncated(), target.exitCode(), null, Map.of(), List.of(), null, null, true));
            }
            input = normalizer.fromCollectedBlocks(blocks);
            context.put("sourceCollectionId", request.sourceCollectionId());
            context.put("collectionId", collection.collectionId());
            context.put("targetId", target.targetId());
            if (target.contextSnapshot().device() != null && target.contextSnapshot().device().model() != null) {
                context.put("deviceModel", target.contextSnapshot().device().model());
            }
        } else {
            byte[] raw = request.inputText().getBytes(StandardCharsets.UTF_8);
            bounded(raw);
            context.put("originalInputSha256", hash(raw));
            context.put("originalCommandHintSha256", hash((request.commandHint() == null ? "" : request.commandHint())
                    .getBytes(StandardCharsets.UTF_8)));
            input = normalizer.normalize(request.inputText(), request.commandHint());
        }
        var serials=new java.util.LinkedHashSet<String>();
        input.input().commandBlocks().forEach(block -> serials.addAll(
                com.dp.deviceops.parser.semantic.log.DeviceLogSplitter.serialNumbers(block.stdout())));
        if (serials.size()>1) throw new IllegalArgumentException("MULTI_DEVICE_LOG_REQUIRES_SPLIT");
        context.put("inputWarnings", input.warnings());
        var normalized = new SemanticParserInput("1.0.0", null, context, input.input().commandBlocks());
        byte[] content = new CanonicalJson().mapper().writeValueAsBytes(normalized);
        bounded(content);
        // Only sanitized evidence reaches persistent storage, including on failed/repeated submissions.
        String inputRef = payloads.putScoped(request.namespace(), "application/json", new ByteArrayInputStream(content));
        var outcome = service.submit(new ParseTaskService.SubmitCommand(PREFIX + request.requestId(), request.namespace(),
                properties.getDefaultLogType(), null, DeviceLogInputNormalizer.INPUT_FORMAT, inputRef, context, null, null));
        ParseTask task = results.findTask(request.namespace(), outcome.taskId())
                .orElseThrow(() -> new ParserApiNotFound("TASK_NOT_FOUND"));
        return view(task);
    }

    @PostMapping("/actions/normalize")
    @PreAuthorize("hasAnyAuthority('SCOPE_parser:task:create','SCOPE_device-ops:collections:execute')")
    public NormalizedView normalize(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Normalize request) {
        authorizer.requireNamespace(jwt, request.namespace());
        authorizer.require(jwt, request.projectKey());
        // 原日志只驻留本次请求内存。复用既有脱敏器后才允许调用方登记安全文件。
        if (request.inputText().getBytes(StandardCharsets.UTF_8).length > 52_428_800)
            throw new ParserRuntimeError("INPUT_TOO_LARGE", "log exceeds 50 MiB");
        var normalized = normalizer.normalize(request.inputText(), request.commandHint());
        var serials = new java.util.LinkedHashSet<String>();
        var blocks = normalized.input().commandBlocks().stream().map(block -> {
            serials.addAll(com.dp.deviceops.parser.semantic.log.DeviceLogSplitter.serialNumbers(block.stdout()));
            return new NormalizedBlock(block.commandIndex(), block.commandText(), block.stdout(), block.stderr(), block.status().name(),
                    block.truncated(), block.exitCode());
        }).toList();
        return new NormalizedView(request.namespace(), request.projectKey(), blocks, List.copyOf(serials), normalized.warnings());
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_parser:task:read','SCOPE_device-ops:collections:read')")
    public View get(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("requestId") @Pattern(regexp = "[a-zA-Z0-9-]{16,64}") String requestId,
            @RequestParam("namespace") String namespace) {
        authorizer.requireNamespace(jwt, namespace);
        var matches = results.listTaskResultsByRequestPrefix(namespace, PREFIX + requestId).stream()
                .filter(item -> (PREFIX + requestId).equals(item.task().requestId())).toList();
        if (matches.size() != 1) throw new ParserApiNotFound("TASK_NOT_FOUND");
        var task = matches.getFirst().task();
        authorizer.require(jwt, String.valueOf(task.contextSnapshot().get("projectKey")));
        return view(task);
    }

    private View view(ParseTask task) {
        ParseResultEnvelope result = task.resultId() == null ? null
                : results.findResult(task.callerNamespace(), task.resultId())
                        .orElseThrow(() -> new ParserApiNotFound("RESULT_NOT_FOUND"));
        var warnings = task.contextSnapshot().get("inputWarnings");
        return new View(task.requestId().substring(PREFIX.length()), task.callerNamespace(),
                String.valueOf(task.contextSnapshot().get("projectKey")),
                (String) task.contextSnapshot().get("sourceCollectionId"), task.taskId(), task.state().name(),
                task.waitReason() == null ? null : task.waitReason().name(), task.coordinate().releaseVersion(),
                warnings instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of(), result);
    }

    private void bounded(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > properties.getMaxInputBytes()) {
            throw new ParserRuntimeError("INPUT_TOO_LARGE", "log exceeds the configured input limit");
        }
    }

    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record Submit(@NotBlank @Size(max = 100) String namespace,
            @NotBlank @Size(max = 100) String projectKey,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9-]{16,64}") String requestId,
            @Size(min = 1, max = 200) String sourceCollectionId,
            @Size(max = 8 * 1024 * 1024) String inputText, @Size(max = 256) String commandHint) { }

    public record View(String requestId, String namespace, String projectKey, String sourceCollectionId,
            String taskId, String state, String waitReason, String releaseVersion, List<String> warnings,
            ParseResultEnvelope result) { }

    public record Normalize(@NotBlank @Size(max = 100) String namespace,
                            @NotBlank @Size(max = 100) String projectKey,
                            @NotBlank @Size(max = 52_428_800) String inputText, @Size(max = 256) String commandHint) { }
    public record NormalizedBlock(Integer commandIndex, String commandText, String stdout, String stderr, String status,
                                  Boolean truncated, Integer exitCode) { }
    public record NormalizedView(String namespace, String projectKey, List<NormalizedBlock> commandBlocks,
                                 List<String> serialNumbers, List<String> warnings) { }
}
