package com.dp.deviceops.adapter.web;

import com.dp.deviceops.core.model.SavedConnection;
import com.dp.deviceops.core.model.SavedConnectionDraft;
import com.dp.deviceops.core.model.TransientCredential;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.SavedConnectionStore;
import com.dp.deviceops.core.service.SavedConnectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Owner- and namespace-scoped APIs for verified saved connections. */
@RestController
@RequestMapping("/api/v1/saved-connections")
public class SavedConnectionController {
    private final SavedConnectionStore store;
    private final SavedConnectionService savedConnections;
    private final ConnectionRequestMapper mapper;
    private final com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer claims;

    public SavedConnectionController(SavedConnectionStore store, SavedConnectionService savedConnections,
                                     ConnectionRequestMapper mapper,
                                     com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer claims) {
        this.store = store;
        this.savedConnections = savedConnections;
        this.mapper = mapper;
        this.claims = claims;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public ListResponse list(@AuthenticationPrincipal Jwt jwt,
                             @RequestParam("namespace") @NotBlank @Size(max = 100) String namespace) {
        claims.requireNamespace(jwt, namespace);
        requireAvailable();
        return new ListResponse(store.findAll(jwt.getSubject(), namespace));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public SavedConnection get(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") @NotBlank String id,
                               @RequestParam("namespace") @NotBlank @Size(max = 100) String namespace) {
        claims.requireNamespace(jwt, namespace);
        requireAvailable();
        return findRequired(jwt.getSubject(), namespace, id);
    }

    @PostMapping("/verify-and-create")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
    public SavedConnectionService.SaveResult verifyAndCreate(@AuthenticationPrincipal Jwt jwt,
                                                              @Valid @RequestBody CreateRequest request) {
        try {
            claims.requireNamespace(jwt, request.namespace());
            requireAvailable();
            try (ConnectionRequestMapper.MappedConnection mapped = mapNewCredential(request.connection())) {
                return savedConnections.verifyAndCreate(jwt.getSubject(), request.namespace(),
                        new SavedConnectionDraft(request.displayName(), request.description(), mapped.spec()),
                        mapped.credential());
            }
        } finally {
            request.clear();
        }
    }

    @PutMapping("/{id}/verify-and-replace")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
    public SavedConnectionService.SaveResult verifyAndReplace(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable("id") @NotBlank String id,
                                                               @Valid @RequestBody ReplaceRequest request) {
        try {
            claims.requireNamespace(jwt, request.namespace());
            requireAvailable();
            rejectExecutionSelectors(request.connection());
            CommandExecutionPort.ConnectionSpec spec = toConnectionSpec(request.connection());
            SavedConnectionDraft draft = new SavedConnectionDraft(request.displayName(), request.description(), spec);
            if (!hasSuppliedCredential(request.connection())) {
                validateRetainedCredential(jwt.getSubject(), request.namespace(), id, draft);
                return savedConnections.verifyAndReplace(jwt.getSubject(), request.namespace(), id,
                        request.version().longValue(), draft, null);
            }
            try (ConnectionRequestMapper.MappedConnection mapped = mapNewCredential(request.connection())) {
                return savedConnections.verifyAndReplace(jwt.getSubject(), request.namespace(), id,
                        request.version().longValue(), new SavedConnectionDraft(request.displayName(), request.description(), mapped.spec()),
                        mapped.credential());
            }
        } catch (SavedConnectionStore.NotFoundException exception) {
            throw notFound();
        } catch (SavedConnectionStore.VersionConflictException exception) {
            throw conflict();
        } finally {
            request.clear();
        }
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
    public SavedConnection rename(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") @NotBlank String id,
                                  @Valid @RequestBody RenameRequest request) {
        claims.requireNamespace(jwt, request.namespace());
        requireAvailable();
        findRequired(jwt.getSubject(), request.namespace(), id);
        try {
            return store.rename(jwt.getSubject(), request.namespace(), id, request.version().longValue(),
                    request.displayName(), request.description());
        } catch (SavedConnectionStore.NotFoundException exception) {
            throw notFound();
        } catch (SavedConnectionStore.VersionConflictException exception) {
            throw conflict();
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") @NotBlank String id,
                       @RequestParam("namespace") @NotBlank @Size(max = 100) String namespace,
                       @RequestParam("version") @NotNull @Min(0) Long version) {
        claims.requireNamespace(jwt, namespace);
        requireAvailable();
        findRequired(jwt.getSubject(), namespace, id);
        try {
            store.delete(jwt.getSubject(), namespace, id, version.longValue());
        } catch (SavedConnectionStore.NotFoundException exception) {
            throw notFound();
        } catch (SavedConnectionStore.VersionConflictException exception) {
            throw conflict();
        }
    }

    private ConnectionRequestMapper.MappedConnection mapNewCredential(ConnectionRequestMapper.Connection connection) {
        rejectExecutionSelectors(connection);
        CommandExecutionPort.ConnectionSpec spec = toConnectionSpec(connection);
        if (!hasSuppliedCredential(connection)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "credential secret is required");
        }
        char[] secret = validateNewCredential(spec.authenticationType(), connection);
        return new ConnectionRequestMapper.MappedConnection(spec,
                new TransientCredential(secret, spec.authenticationType() == CommandExecutionPort.AuthenticationType.PASSWORD
                        ? null : connection.passphrase()));
    }

    private static void rejectExecutionSelectors(ConnectionRequestMapper.Connection connection) {
        if (connection.savedConnectionId() != null || connection.credentialId() != null
                || connection.credentialNamespace() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "saved connection requests must not contain execution selectors");
        }
    }

    private static boolean hasSuppliedCredential(ConnectionRequestMapper.Connection connection) {
        return connection.password() != null || connection.privateKey() != null || connection.passphrase() != null;
    }

    private static char[] validateNewCredential(CommandExecutionPort.AuthenticationType authenticationType,
                                                 ConnectionRequestMapper.Connection connection) {
        boolean passwordAuthentication = authenticationType == CommandExecutionPort.AuthenticationType.PASSWORD;
        char[] secret = passwordAuthentication ? connection.password() : connection.privateKey();
        if (secret == null || secret.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "credential secret is required");
        }
        if (passwordAuthentication && (connection.privateKey() != null || connection.passphrase() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid password credential");
        }
        if (!passwordAuthentication && connection.password() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid private key credential");
        }
        return secret;
    }

    private CommandExecutionPort.ConnectionSpec toConnectionSpec(ConnectionRequestMapper.Connection connection) {
        return mapper.directSpec(connection);
    }

    private SavedConnection findRequired(String ownerId, String namespace, String id) {
        return store.find(ownerId, namespace, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "saved connection not found"));
    }

    private void validateRetainedCredential(String ownerId, String namespace, String id, SavedConnectionDraft draft) {
        SavedConnection existing = findRequired(ownerId, namespace, id);
        if (existing.connection().authenticationType() != draft.connection().authenticationType()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "authentication type requires a replacement credential");
        }
    }

    private void requireAvailable() {
        if (!store.available()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "saved connection storage is disabled because no master key is configured");
        }
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "saved connection version conflict");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "saved connection not found");
    }

    public record ListResponse(List<SavedConnection> items) {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 100) String namespace,
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 2000) String description,
            @NotNull @Valid ConnectionRequestMapper.Connection connection) {
        void clear() {
            if (connection != null) {
                connection.clearCredentials();
            }
        }
    }

    public record ReplaceRequest(
            @NotBlank @Size(max = 100) String namespace,
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 2000) String description,
            @NotNull @Valid ConnectionRequestMapper.Connection connection) {
        void clear() {
            if (connection != null) {
                connection.clearCredentials();
            }
        }
    }

    public record RenameRequest(
            @NotBlank @Size(max = 100) String namespace,
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 2000) String description) {
    }
}
