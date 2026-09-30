package com.dp.deviceops.core.model;

import com.dp.deviceops.core.port.CommandExecutionPort;

import java.time.Duration;
import java.util.Objects;

/** In-memory execution context; its projection intentionally contains no credential material. */
public final class ExecutionConnectionContext implements AutoCloseable {
    private final CommandExecutionPort.ConnectionSpec connection;
    private final TransientCredential credential;
    private volatile boolean cancellationRequested;

    public void requestCancellation() { cancellationRequested = true; }
    public boolean isCancellationRequested() { return cancellationRequested; }
    public void checkCancellation() {
        if (cancellationRequested) throw new java.util.concurrent.CancellationException("CALLER_CANCELLED");
    }

    public ExecutionConnectionContext(CommandExecutionPort.ConnectionSpec connection, TransientCredential credential) {
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
        this.credential = Objects.requireNonNull(credential, "credential must not be null");
    }

    public ConnectionProjection projection() {
        return new ConnectionProjection(connection.protocol(), connection.host(), connection.port(), connection.username(),
                connection.authenticationType(), connection.executionMode(), connection.expectedHostKeyFingerprint(),
                connection.telnetPrompts(), connection.serialParams(), connection.serialPrompts(),
                connection.connectTimeout());
    }

    public <T> T withCredentials(TransientCredential.CredentialOperation<T> operation) { return credential.withCredentials(operation); }
    /** Executes only with this context's frozen connection projection and transient credential. */
    public CommandExecutionPort.CommandResult execute(CommandExecutionPort command, String script, Duration timeout) {
        return execute(command, script, timeout, CommandExecutionPort.ProgressListener.noop());
    }
    /** Executes with an incremental output listener while credentials remain confined to this context. */
    public CommandExecutionPort.CommandResult execute(
            CommandExecutionPort command,
            String script,
            Duration timeout,
            CommandExecutionPort.ProgressListener listener) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(script, "script must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        checkCancellation();
        var cancellable = new CommandExecutionPort.ProgressListener() {
            @Override public void onProgress(CommandExecutionPort.OutputProgress progress) { listener.onProgress(progress); }
            @Override public void checkCancellation() {
                ExecutionConnectionContext.this.checkCancellation();
                listener.checkCancellation();
            }
        };
        return credential.withCredentials(
                (secret, passphrase) -> command.execute(connection, secret, passphrase, script, timeout, cancellable));
    }
    public void closeCredential() { close(); }
    @Override public void close() { credential.close(); }

    public record ConnectionProjection(ConnectionProtocol protocol, String host, int port, String username,
                                       CommandExecutionPort.AuthenticationType authenticationType,
                                       CommandExecutionPort.ExecutionMode executionMode,
                                       String expectedHostKeyFingerprint,
                                       CommandExecutionPort.TelnetPrompts telnetPrompts,
                                       CommandExecutionPort.SerialParams serialParams,
                                       CommandExecutionPort.SerialPrompts serialPrompts,
                                       Duration connectTimeout) { }
}
