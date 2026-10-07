package com.dp.deviceops.adapter.web;

import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.parser.semantic.log.DeviceLogSplitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** No device commands, persistence or credential acquisition occurs during splitting. */
@RestController
@RequestMapping("/api/v1/npdms/log-splits")
@PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
public class NpdmsLogSplitController {
    private final ProjectClaimAuthorizer claims;
    private final DeviceLogSplitter splitter = new DeviceLogSplitter();
    public NpdmsLogSplitController(ProjectClaimAuthorizer claims) { this.claims = claims; }

    @PostMapping
    public DeviceLogSplitter.Result split(@AuthenticationPrincipal Jwt jwt, @RequestBody Request request) {
        claims.requireNamespace(jwt, request.namespace());
        claims.require(jwt, request.projectId());
        if (request.content() == null || request.content().getBytes(StandardCharsets.UTF_8).length > DeviceLogSplitter.MAX_BYTES)
            throw new DeviceLogSplitter.SplitException("LOG_SIZE_INVALID");
        String text = switch (request.format() == null ? "" : request.format()) {
            case "TERMINAL_TEXT" -> request.content();
            case "DAC_CALLBACK" -> callbackText(request.content(), request.projectId());
            default -> throw new DeviceLogSplitter.SplitException("LOG_FORMAT_UNSUPPORTED");
        };
        return splitter.split(text);
    }

    @ExceptionHandler(DeviceLogSplitter.SplitException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Failure rejected(DeviceLogSplitter.SplitException failure) { return new Failure(failure.code(), failure.block()); }
    public record Failure(String code, Integer block) { }

    private static String callbackText(String input, String projectId) {
        try {
            var payload = new ObjectMapper().readTree(input);
            if (!projectId.equals(payload.path("projectKey").asText()) || payload.path("truncated").asBoolean()
                    || !Set.of("SUCCEEDED", "SUCCESS").contains(payload.path("status").asText()))
                throw new DeviceLogSplitter.SplitException("COLLECTION_LOG_INCOMPLETE");
            var blocks = payload.path("commandBlocks");
            if (!blocks.isArray() || blocks.isEmpty()) throw new DeviceLogSplitter.SplitException("COLLECTION_BLOCKS_MISSING");
            var text = new StringBuilder();
            for (var block : blocks) {
                if (block.path("truncated").asBoolean() || !"SUCCEEDED".equals(block.path("status").asText())
                        || (!block.path("exitCode").isNull() && block.path("exitCode").asInt() != 0))
                    throw new DeviceLogSplitter.SplitException("COLLECTION_LOG_INCOMPLETE");
                text.append("\n--- ").append(block.path("commandText").asText()).append(" ---\n")
                        .append(block.path("stdout").asText()).append('\n');
                String stderr=block.path("stderr").asText("");
                if (!stderr.isEmpty()) text.append("--- stderr ---\n").append(stderr).append('\n');
            }
            return text.toString();
        } catch (DeviceLogSplitter.SplitException failure) { throw failure; }
        catch (Exception failure) { throw new DeviceLogSplitter.SplitException("COLLECTION_LOG_FORMAT_INVALID"); }
    }
    public record Request(String namespace, String projectId, String format, String content) { }
}
