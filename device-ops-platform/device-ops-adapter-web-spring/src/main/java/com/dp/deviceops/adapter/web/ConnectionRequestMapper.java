package com.dp.deviceops.adapter.web;

import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.model.ExecutionConnectionContext;
import com.dp.deviceops.core.model.TransientCredential;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.SavedConnectionStore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Arrays;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** Maps mutually exclusive saved-reference or direct HTTP connections into execution contexts. */
@Component
public final class ConnectionRequestMapper {
    private final SavedConnectionStore savedConnections;

    public ConnectionRequestMapper(SavedConnectionStore savedConnections) {
        this.savedConnections = savedConnections;
    }

    public MappedConnection map(Connection request) {
        return map(null, request.credentialNamespace(), request);
    }

    public MappedConnection map(String namespace, Connection request) {
        return map(null, namespace, request);
    }

    public MappedConnection map(String ownerId, String namespace, Connection request) {
        try {
            rejectLegacyCredentialId(request);
            if (hasSavedConnectionId(request)) {
                return mapSavedConnection(ownerId, namespace, request);
            }
            return mapDirectConnection(request);
        } catch (SavedConnectionStore.NotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "saved connection was not found");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage());
        } finally {
            request.clearCredentials();
        }
    }

    private MappedConnection mapSavedConnection(String ownerId, String namespace, Connection request) {
        rejectSavedReferenceDirectFields(request);
        if (ownerId == null || ownerId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "connection owner is required");
        }
        String credentialNamespace = requireNamespace(namespace, request);
        String savedConnectionId = request.savedConnectionId().strip();
        return savedConnections.withConnection(ownerId, credentialNamespace, savedConnectionId,
                (connection, credential) -> {
                    if (request.savedConnectionVersion() != null && request.savedConnectionVersion() != connection.version()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "saved connection version changed");
                    }
                    return credential.withCredentials((secret, passphrase) ->
                            new MappedConnection(connection.connection(), new TransientCredential(secret, passphrase)));
                });
    }

    private MappedConnection mapDirectConnection(Connection request) {
        rejectDirectSavedSelectors(request);
        CommandExecutionPort.ConnectionSpec spec = directSpec(request);
        if (!hasTransientCredential(request)) {
            throw new ResponseStatusException(BAD_REQUEST, "transient credential is required");
        }
        char[] secret = selectSecret(request);
        try {
            return new MappedConnection(spec, new TransientCredential(secret, request.passphrase()));
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    /** Validates direct endpoint fields independently of transient credential selection. */
    public CommandExecutionPort.ConnectionSpec directSpec(Connection request) {
        try {
            return new CommandExecutionPort.ConnectionSpec(
                    requireProtocol(request), requireHost(request), requirePort(request), requireUsername(request),
                    requireAuthenticationType(request), requireExecutionMode(request),
                    request.hostKeyFingerprint(), request.telnetPrompts() == null ? null : request.telnetPrompts().toCore(),
                    request.serialParams() == null ? null : request.serialParams().toCore(),
                    request.serialPrompts() == null ? null : request.serialPrompts().toCore(),
                    Duration.ofSeconds(requireConnectTimeout(request)));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage());
        }
    }

    private static boolean hasSavedConnectionId(Connection request) {
        return request.savedConnectionId() != null && !request.savedConnectionId().isBlank();
    }

    private static void rejectSavedReferenceDirectFields(Connection request) {
        if (request.protocol() != null || request.host() != null || request.port() != null || request.username() != null
                || request.authenticationType() != null || request.executionMode() != null
                || request.hostKeyFingerprint() != null
                || request.telnetPrompts() != null || request.serialParams() != null || request.serialPrompts() != null
                || request.connectTimeoutSeconds() != null
                || request.credentialId() != null || request.password() != null || request.privateKey() != null
                || request.passphrase() != null) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "saved connection reference must not contain direct connection fields");
        }
    }

    private static void rejectDirectSavedSelectors(Connection request) {
        if (request.savedConnectionId() != null || request.credentialNamespace() != null || request.savedConnectionVersion() != null) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "direct connection must not contain saved connection selectors");
        }
    }

    private static void rejectLegacyCredentialId(Connection request) {
        if (request.credentialId() != null) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "credentialId is not supported for execution");
        }
    }

    private static String requireNamespace(String namespace, Connection request) {
        if (request.credentialNamespace() != null && request.credentialNamespace().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "credential namespace is required");
        }
        String credentialNamespace = request.credentialNamespace() == null
                ? namespace : request.credentialNamespace();
        if (credentialNamespace == null || credentialNamespace.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "credential namespace is required");
        }
        if (namespace != null && !namespace.isBlank()
                && !namespace.strip().equals(credentialNamespace.strip())) {
            throw new ResponseStatusException(BAD_REQUEST, "credential namespace differs");
        }
        return credentialNamespace.strip();
    }

    private static ConnectionProtocol requireProtocol(Connection request) {
        if (request.protocol() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "protocol is required for a direct connection");
        }
        return request.protocol();
    }

    private static String requireHost(Connection request) {
        if (request.host() == null || request.host().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "host is required for a direct connection");
        }
        return request.host();
    }

    private static int requirePort(Connection request) {
        if (request.protocol() == ConnectionProtocol.SERIAL) {
            if (request.port() == null || request.port() != 0) {
                throw new ResponseStatusException(BAD_REQUEST, "port must be 0 for a serial connection");
            }
            return request.port();
        }
        if (request.port() == null || request.port() < 1 || request.port() > 65535) {
            throw new ResponseStatusException(BAD_REQUEST, "port must be between 1 and 65535 for a direct connection");
        }
        return request.port();
    }

    private static String requireUsername(Connection request) {
        if (request.username() == null || request.username().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "username is required for a direct connection");
        }
        return request.username();
    }

    private static CommandExecutionPort.AuthenticationType requireAuthenticationType(Connection request) {
        if (request.authenticationType() == null) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "authentication type is required for a direct connection");
        }
        return request.authenticationType();
    }

    private static CommandExecutionPort.ExecutionMode requireExecutionMode(Connection request) {
        if (request.executionMode() == null) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "execution mode is required for a direct connection");
        }
        return request.executionMode();
    }

    private static long requireConnectTimeout(Connection request) {
        if (request.connectTimeoutSeconds() == null || request.connectTimeoutSeconds() < 1) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "connect timeout is required for a direct connection");
        }
        return request.connectTimeoutSeconds();
    }

    private static boolean hasTransientCredential(Connection request) {
        return !empty(request.password()) || !empty(request.privateKey()) || !empty(request.passphrase());
    }

    private static char[] selectSecret(Connection request) {
        boolean password = request.authenticationType() == CommandExecutionPort.AuthenticationType.PASSWORD;
        if (password && (empty(request.password()) || request.privateKey() != null || request.passphrase() != null)) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid password credential");
        }
        if (!password && (empty(request.privateKey()) || request.password() != null)) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid private key credential");
        }
        return Arrays.copyOf(password ? request.password() : request.privateKey(),
                password ? request.password().length : request.privateKey().length);
    }

    private static boolean empty(char[] value) {
        return value == null || value.length == 0;
    }

    public record Connection(
            ConnectionProtocol protocol,
            @Size(max = 500) String host,
            Integer port,
            @Size(max = 500) String username,
            CommandExecutionPort.AuthenticationType authenticationType,
            CommandExecutionPort.ExecutionMode executionMode,
            @Size(max = 1000) String hostKeyFingerprint,
            @Valid TelnetPrompts telnetPrompts,
            @Valid SerialParams serialParams,
            @Valid SerialPrompts serialPrompts,
            Long connectTimeoutSeconds,
            @Size(max = 100) String credentialNamespace,
            @Size(max = 36) String savedConnectionId,
            @Size(max = 36) String credentialId,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) char[] password,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) char[] privateKey,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) char[] passphrase,
            Long savedConnectionVersion) {
        public Connection(ConnectionProtocol protocol, String host, Integer port, String username,
                          CommandExecutionPort.AuthenticationType authenticationType, CommandExecutionPort.ExecutionMode executionMode,
                          String hostKeyFingerprint, TelnetPrompts telnetPrompts, Long connectTimeoutSeconds,
                          String credentialNamespace, String savedConnectionId, String credentialId,
                          char[] password, char[] privateKey, char[] passphrase) {
            this(protocol, host, port, username, authenticationType, executionMode, hostKeyFingerprint, telnetPrompts,
                    null, null, connectTimeoutSeconds, credentialNamespace, savedConnectionId, credentialId,
                    password, privateKey, passphrase, null);
        }
        public Connection(ConnectionProtocol protocol, String host, int port, String username,
                          CommandExecutionPort.AuthenticationType authenticationType,
                          String hostKeyFingerprint, TelnetPrompts telnetPrompts,
                          long connectTimeoutSeconds, char[] password, char[] privateKey,
                          char[] passphrase) {
            this(protocol, host, port, username, authenticationType, CommandExecutionPort.ExecutionMode.SHELL,
                    hostKeyFingerprint, telnetPrompts, null, null,
                    connectTimeoutSeconds, null, null, null, password, privateKey, passphrase, null);
        }

        public void clearCredentials() {
            clear(password);
            clear(privateKey);
            clear(passphrase);
        }
    }

    public record TelnetPrompts(@Size(max = 500) String login,
                                @Size(max = 500) String password,
                                @Size(max = 500) String command,
                                CommandExecutionPort.TelnetLineEnding lineEnding) {
        public TelnetPrompts(String login, String password, String command) {
            this(login, password, command, CommandExecutionPort.TelnetLineEnding.AUTO);
        }

        CommandExecutionPort.TelnetPrompts toCore() {
            return new CommandExecutionPort.TelnetPrompts(login, password, command, lineEnding);
        }
    }

    public record SerialParams(Integer baudRate, Integer dataBits,
                               CommandExecutionPort.SerialParity parity,
                               Integer stopBits,
                               CommandExecutionPort.SerialFlowControl flowControl) {
        CommandExecutionPort.SerialParams toCore() {
            return new CommandExecutionPort.SerialParams(baudRate, dataBits, parity, stopBits, flowControl);
        }
    }

    public record SerialPrompts(@Size(max = 500) String login,
                                @Size(max = 500) String password,
                                @Size(max = 500) String command,
                                CommandExecutionPort.TelnetLineEnding lineEnding) {
        CommandExecutionPort.SerialPrompts toCore() {
            return new CommandExecutionPort.SerialPrompts(login, password, command, lineEnding);
        }
    }

    public record MappedConnection(CommandExecutionPort.ConnectionSpec spec,
                                   TransientCredential credential) implements AutoCloseable {
        public MappedConnection {
            if (spec == null || credential == null) {
                throw new IllegalArgumentException("mapped connection is incomplete");
            }
        }

        public ExecutionConnectionContext executionContext() {
            return new ExecutionConnectionContext(spec, credential);
        }

        @Override
        public void close() {
            credential.close();
        }
    }

    static void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }
}
