package com.dp.deviceops.adapter.web;

import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.core.port.CollectionEvidenceQueryPort;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CollectionEvidenceController {
    private final CollectionEvidenceQueryPort queries;
    private final ProjectClaimAuthorizer authorizer;
    public CollectionEvidenceController(CollectionEvidenceQueryPort queries, ProjectClaimAuthorizer authorizer) {
        this.queries = queries; this.authorizer = authorizer;
    }
    @GetMapping("/api/v1/collections/{collectionId}/evidence")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public ResponseEntity<CollectionEvidenceQueryPort.Evidence> generic(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("collectionId") String id, @RequestParam("namespace") String namespace) {
        return evidence(jwt, namespace, null, id);
    }
    @GetMapping("/api/v1/projects/{projectKey}/collections/{collectionId}/evidence")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public ResponseEntity<CollectionEvidenceQueryPort.Evidence> project(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("projectKey") String project, @PathVariable("collectionId") String id,
            @RequestParam("namespace") String namespace) {
        return evidence(jwt, namespace, project, id);
    }
    private ResponseEntity<CollectionEvidenceQueryPort.Evidence> evidence(Jwt jwt, String namespace, String project, String id) {
        authorizer.requireNamespace(jwt, namespace);
        var result = queries.find(authorizer.visibleScope(jwt), namespace, project, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "collection not found"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
