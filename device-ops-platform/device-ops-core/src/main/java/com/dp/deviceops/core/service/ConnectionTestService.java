package com.dp.deviceops.core.service;

import com.dp.deviceops.core.model.ConnectionFailure;
import com.dp.deviceops.core.model.TransientCredential;
import com.dp.deviceops.core.port.CommandExecutionPort;

import java.util.Objects;

/** Runs a credential-safe connectivity probe and exposes only stable diagnostics. */
public final class ConnectionTestService {
    private final CommandExecutionPort commands;

    public ConnectionTestService(CommandExecutionPort commands) {
        this.commands = Objects.requireNonNull(commands, "commands must not be null");
    }

    public TestResult test(CommandExecutionPort.ConnectionSpec connection, TransientCredential credential) {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(credential, "credential must not be null");
        long started = System.nanoTime();
        try {
            credential.withCredentials((secret, passphrase) -> {
                commands.test(connection, secret, passphrase);
                return null;
            });
            return new TestResult(true, null, elapsedMillis(started), null, "connection succeeded");
        } catch (ConnectionFailure failure) {
            return new TestResult(false, failure.stage(), elapsedMillis(started), failure.code(), failure.safeMessage());
        } catch (RuntimeException failure) {
            return new TestResult(false, ConnectionFailure.Stage.CONNECT, elapsedMillis(started),
                    ConnectionFailure.Code.UNREACHABLE, "connection test failed");
        }
    }

    private static long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    public record TestResult(boolean reachable, ConnectionFailure.Stage stage, long durationMillis,
                             ConnectionFailure.Code errorCode, String safeMessage) {
    }
}
