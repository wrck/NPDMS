package com.dp.deviceops.adapter.web.parser;

import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.core.port.CollectionQueryPort;
import com.dp.deviceops.parser.runtime.model.ParseResultEnvelope;
import com.dp.deviceops.parser.runtime.model.ParseTaskState;
import com.dp.deviceops.parser.runtime.model.ParseWaitReason;
import com.dp.deviceops.parser.runtime.port.ParseResultQueryPort;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/collections")
public class CollectionSemanticResultController {

    private final CollectionQueryPort collections;
    private final ParseResultQueryPort results;
    private final ProjectClaimAuthorizer authorizer;

    public CollectionSemanticResultController(CollectionQueryPort collections, ParseResultQueryPort results,
            ProjectClaimAuthorizer authorizer) {
        this.collections = collections;
        this.results = results;
        this.authorizer = authorizer;
    }

    @GetMapping("/{collectionId}/semantic-results")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public List<CollectionSemanticResult> list(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("collectionId") @NotBlank String collectionId,
            @RequestParam("namespace") @NotBlank String namespace) {
        authorizer.requireNamespace(jwt, namespace);
        var collection = collections.find(namespace, collectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "collection not found"));
        if (collection.projectKey() != null && !collection.projectKey().isBlank()) {
            authorizer.require(jwt, collection.projectKey());
        }
        return results.listTaskResultsByRequestPrefix(namespace, "collection:" + collectionId + ":target:").stream()
                .map(item -> new CollectionSemanticResult(targetId(item.task().requestId()), item.task().taskId(),
                        item.task().state(), item.task().waitReason(), item.task().releaseId(),
                        item.task().coordinate(), item.task().resultId(), item.result()))
                .toList();
    }

    /** Extracts the target id from a collection parser request id ("collection:<id>:target:<targetId>"). */
    public static long targetId(String requestId) {
        int separator = requestId.lastIndexOf(':');
        if (separator < 0 || separator == requestId.length() - 1) {
            throw new IllegalStateException("collection parser request id has no target id");
        }
        return Long.parseLong(requestId.substring(separator + 1));
    }

    public record CollectionSemanticResult(long targetId, String taskId, ParseTaskState state,
            ParseWaitReason waitReason, String releaseId, ParserCoordinate coordinate, String resultId,
            ParseResultEnvelope result) { }
}
