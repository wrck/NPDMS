package com.dp.deviceops.adapter.web.management;

import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.port.ManagementQueryPort;
import com.dp.deviceops.core.port.ManagementQueryPort.Filter;
import com.dp.deviceops.core.port.ManagementQueryPort.Overview;
import com.dp.deviceops.core.port.ManagementQueryPort.Page;
import com.dp.deviceops.core.port.ManagementQueryPort.Scope;
import com.dp.deviceops.core.port.ManagementQueryPort.ScriptContent;
import com.dp.deviceops.core.port.ManagementQueryPort.ScriptVersion;
import com.dp.deviceops.core.port.ManagementQueryPort.Summary;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/management")
@PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
public class ManagementController {
    private final ManagementQueryPort queries;
    private final ProjectClaimAuthorizer claims;
    public ManagementController(ManagementQueryPort queries, ProjectClaimAuthorizer claims) {
        this.queries = queries;
        this.claims = claims;
    }

    @GetMapping("/collections")
    public Page<Summary> collections(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name="namespace", required=false) String namespace, @RequestParam(name="project", required=false) String project,
            @RequestParam(name="device", required=false) String device, @RequestParam(name="status", required=false) CollectionStatus status,
            @RequestParam(name="from", required=false) Instant from, @RequestParam(name="to", required=false) Instant to,
            @RequestParam(name="page", defaultValue="0") int page, @RequestParam(name="size", defaultValue="20") int size) {
        return queries.collections(scope(jwt, namespace, project), filter(namespace, project, device, status, from, to), page, size);
    }

    @GetMapping("/overview")
    public Overview overview(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name="namespace", required=false) String namespace, @RequestParam(name="project", required=false) String project,
            @RequestParam(name="device", required=false) String device, @RequestParam(name="status", required=false) CollectionStatus status,
            @RequestParam(name="from", required=false) Instant from, @RequestParam(name="to", required=false) Instant to) {
        return queries.overview(scope(jwt, namespace, project), filter(namespace, project, device, status, from, to));
    }

    @GetMapping("/scripts")
    public Page<ScriptVersion> scripts(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name="namespace", required=false) String namespace, @RequestParam(name="project", required=false) String project,
            @RequestParam(name="page", defaultValue="0") int page, @RequestParam(name="size", defaultValue="20") int size) {
        return queries.scripts(scope(jwt, namespace, project), filter(namespace, project, null, null, null, null), page, size);
    }

    @GetMapping("/scripts/content")
    public ScriptContent content(@AuthenticationPrincipal Jwt jwt, @RequestParam("collectionId") String collectionId,
                                 @RequestParam(name="namespace", required=false) String namespace) {
        text(collectionId, 100, "collectionId");
        if (namespace != null) text(namespace, 100, "namespace");
        return queries.content(claims.visibleScope(jwt), namespace, collectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "script content not found"));
    }

    private Scope scope(Jwt jwt, String namespace, String project) {
        if (namespace != null) {
            text(namespace, 100, "namespace");
            claims.requireNamespace(jwt, namespace);
        }
        if (project != null) {
            text(project, 200, "project");
            claims.require(jwt, project);
        }
        return claims.visibleScope(jwt);
    }
    private static Filter filter(String namespace, String project, String device, CollectionStatus status, Instant from, Instant to) {
        if (device != null) text(device, 200, "device");
        return new Filter(namespace, project, device, status, from, to);
    }
    private static void text(String value, int max, String name) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException("invalid " + name);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public java.util.Map<String, String> badRequest() { return java.util.Map.of("error", "invalid management query"); }
}
