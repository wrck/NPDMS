package com.dp.deviceops.adapter.web;

import com.dp.deviceops.core.model.SavedCredential;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.CredentialStore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/credentials")
public class CredentialController {
    private final CredentialStore credentials;
    private final com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer claims;

    public CredentialController(CredentialStore credentials,
                                com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer claims) {
        this.credentials = credentials;
        this.claims = claims;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public ListResponse list(@AuthenticationPrincipal Jwt jwt,
                             @RequestParam("namespace") @NotBlank @Size(max = 100) String namespace) {
        claims.requireNamespace(jwt, namespace);
        return new ListResponse(credentials.available(),
                credentials.available() ? credentials.findAll(jwt.getSubject(), namespace) : List.of());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public SavedCredential get(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable("id") @NotBlank String id,
                               @RequestParam("namespace") @NotBlank @Size(max = 100) String namespace) {
        claims.requireNamespace(jwt, namespace);
        requireAvailable();
        return credentials.find(jwt.getSubject(), namespace, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "credential not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
    public SavedCredential create(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody Request request) {
        try {
            claims.requireNamespace(jwt, request.namespace());
            requireAvailable();
            Material material = request.material();
            return credentials.save(jwt.getSubject(), request.namespace(), request.name(), request.authenticationType(),
                    material.secret(), material.passphrase());
        } finally {
            request.clear();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
    public SavedCredential replace(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable("id") @NotBlank String id,
                                   @Valid @RequestBody Request request) {
        try {
            claims.requireNamespace(jwt, request.namespace());
            requireAvailable();
            Material material = request.material();
            return credentials.replace(jwt.getSubject(), request.namespace(), id, request.name(), request.authenticationType(),
                    material.secret(), material.passphrase());
        } finally {
            request.clear();
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
    public void delete(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable("id") @NotBlank String id,
                       @RequestParam("namespace") @NotBlank @Size(max = 100) String namespace) {
        claims.requireNamespace(jwt, namespace);
        requireAvailable();
        try {
            credentials.delete(jwt.getSubject(), namespace, id);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "credential not found");
        }
    }

    private void requireAvailable() {
        if (!credentials.available()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "credential storage is disabled because no master key is configured");
        }
    }

    public record ListResponse(boolean available, List<SavedCredential> items) {
    }

    public record Request(
            @NotBlank @Size(max = 100) String namespace,
            @NotBlank @Size(max = 200) String name,
            @NotNull CommandExecutionPort.AuthenticationType authenticationType,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) char[] password,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) char[] privateKey,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) char[] passphrase) {

        Material material() {
            boolean passwordAuthentication = authenticationType == CommandExecutionPort.AuthenticationType.PASSWORD;
            char[] secret = passwordAuthentication ? password : privateKey;
            if (secret == null || secret.length == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "credential secret is required");
            }
            if (passwordAuthentication && ((privateKey != null && privateKey.length > 0)
                    || (passphrase != null && passphrase.length > 0))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid password credential");
            }
            if (!passwordAuthentication && password != null && password.length > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid private key credential");
            }
            return new Material(secret, passwordAuthentication ? null : passphrase);
        }

        void clear() {
            ConnectionRequestMapper.clear(password);
            ConnectionRequestMapper.clear(privateKey);
            ConnectionRequestMapper.clear(passphrase);
        }
    }

    private record Material(char[] secret, char[] passphrase) {
    }
}
