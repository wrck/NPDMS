package com.dp.deviceops.adapter.web;

import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.core.model.SavedConnectionDraft;
import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.core.model.TransientCredential;
import com.dp.deviceops.core.port.SavedConnectionStore;
import com.dp.deviceops.core.port.ScriptArtifactRepository;
import com.dp.deviceops.core.service.SavedConnectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** NPDMS resource registration reuses the native verified connection and immutable artifact stores. */
@RestController
@RequestMapping("/api/v1/npdms/resources")
@PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
public class NpdmsResourceController {
    private final SavedConnectionStore connections;
    private final SavedConnectionService service;
    private final ConnectionRequestMapper mapper;
    private final ProjectClaimAuthorizer claims;
    private final ScriptArtifactRepository scripts;

    public NpdmsResourceController(SavedConnectionStore connections, SavedConnectionService service,
                                   ConnectionRequestMapper mapper, ProjectClaimAuthorizer claims, ScriptArtifactRepository scripts) {
        this.connections = connections; this.service = service; this.mapper = mapper; this.claims = claims; this.scripts = scripts;
    }

    @PutMapping("/connections/{id}")
    public SavedConnectionService.SaveResult create(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") String id,
                                                     @Valid @RequestBody SavedConnectionController.CreateRequest request) {
        try {
            claims.requireNamespace(jwt, request.namespace());
            if (!id.matches("[a-zA-Z0-9-]{16,100}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            if (!connections.available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "credential storage unavailable");
            var c = request.connection();
            if (c.savedConnectionId() != null || c.credentialId() != null || c.credentialNamespace() != null
                    || c.password() == null || c.password().length == 0 || c.privateKey() != null || c.passphrase() != null
                    || c.authenticationType() != com.dp.deviceops.core.port.CommandExecutionPort.AuthenticationType.PASSWORD) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "direct password connection required");
            }
            var draft = new SavedConnectionDraft(request.displayName(), request.description(), mapper.directSpec(c));
            try (var secret = new TransientCredential(c.password(), null)) {
                return service.verifyAndCreateIdentified(jwt.getSubject(), request.namespace(), id, draft, secret);
            }
        } finally { request.clear(); }
    }

    @PutMapping("/scripts/{key}/versions/{version}")
    public void register(@AuthenticationPrincipal Jwt jwt, @PathVariable("key") String key, @PathVariable("version") String version,
                         @RequestBody ScriptRegistration request) {
        claims.requireNamespace(jwt, request.namespace());
        if (!key.matches("[a-zA-Z0-9-]{1,200}") || !version.matches("[a-zA-Z0-9.-]{1,100}")
                || request.content() == null || request.content().isBlank() || request.content().length() > 65536) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        scripts.save(request.namespace(), ScriptArtifact.external(key, version, request.content(), request.sha256(),
                ScriptArtifact.PersistencePolicy.REGISTER_VERSION, "NONE", null));
    }

    public record ScriptRegistration(String namespace, String content, String sha256) { }
}
