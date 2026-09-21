package com.dp.deviceops.core.port;

import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.model.CommandOutputBlock;

import java.time.Duration;
import java.util.Objects;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public interface CommandExecutionPort {

    void test(ConnectionSpec connection, char[] secret, char[] passphrase);

    CommandResult execute(
            ConnectionSpec connection,
            char[] secret,
            char[] passphrase,
            String script,
            Duration timeout);

    default CommandResult execute(
            ConnectionSpec connection,
            char[] secret,
            char[] passphrase,
            String script,
            Duration timeout,
            ProgressListener listener) {
        Objects.requireNonNull(listener, "listener");
        return execute(connection, secret, passphrase, script, timeout);
    }

    enum OutputStreamType {
        STDOUT,
        STDERR
    }

    record OutputProgress(
            int commandIndex,
            OutputStreamType streamType,
            String content,
            long receivedBytes,
            int pageCount,
            boolean truncated) {
        public OutputProgress {
            if (commandIndex < 1) {
                throw new IllegalArgumentException("commandIndex must be positive");
            }
        }
    }

    @FunctionalInterface
    interface ProgressListener {
        void onProgress(OutputProgress progress);

        /** Cooperative stop at transport boundaries, never by interrupting a persistence operation. */
        default void checkCancellation() { }

        static ProgressListener noop() {
            return progress -> {
            };
        }
    }

    enum AuthenticationType {
        PASSWORD,
        PRIVATE_KEY
    }

    enum ExecutionMode {
        EXEC,
        SHELL
    }

    enum TelnetLineEnding {
        AUTO,
        CRLF,
        CR,
        LF
    }

    record ConnectionSpec(
            ConnectionProtocol protocol,
            String host,
            int port,
            String username,
            AuthenticationType authenticationType,
            ExecutionMode executionMode,
            String expectedHostKeyFingerprint,
            TelnetPrompts telnetPrompts,
            Duration connectTimeout) {

        public ConnectionSpec {
            protocol = Objects.requireNonNull(protocol, "protocol");
            host = requireText(host, "host");
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("port is invalid");
            }
            username = requireText(username, "username");
            authenticationType = Objects.requireNonNull(authenticationType, "authenticationType");
            executionMode = Objects.requireNonNull(executionMode, "executionMode");
            connectTimeout = requirePositive(connectTimeout, "connectTimeout");
            if (protocol == ConnectionProtocol.SSH2) {
                expectedHostKeyFingerprint = expectedHostKeyFingerprint == null
                        || expectedHostKeyFingerprint.isBlank()
                        ? null : expectedHostKeyFingerprint.strip();
            } else {
                if (authenticationType != AuthenticationType.PASSWORD) {
                    throw new IllegalArgumentException("TELNET requires password authentication");
                }
                if (executionMode != ExecutionMode.SHELL) {
                    throw new IllegalArgumentException("TELNET requires shell execution mode");
                }
                if (expectedHostKeyFingerprint != null && !expectedHostKeyFingerprint.isBlank()) {
                    throw new IllegalArgumentException("TELNET does not use a host key fingerprint");
                }
                expectedHostKeyFingerprint = null;
                telnetPrompts = Objects.requireNonNull(telnetPrompts, "telnetPrompts");
            }
        }

        public ConnectionSpec(String host, int port, String username, AuthenticationType authenticationType,
                              ExecutionMode executionMode, String expectedHostKeyFingerprint,
                              Duration connectTimeout) {
            this(ConnectionProtocol.SSH2, host, port, username, authenticationType, executionMode,
                    expectedHostKeyFingerprint, null, connectTimeout);
        }
    }

    record TelnetPrompts(String login, String password, String command, TelnetLineEnding lineEnding) {

        public TelnetPrompts {
            login = requireRegex(login, "login");
            password = requireRegex(password, "password");
            command = requireRegex(command, "command");
            lineEnding = lineEnding == null ? TelnetLineEnding.AUTO : lineEnding;
        }

        public TelnetPrompts(String login, String password, String command) {
            this(login, password, command, TelnetLineEnding.AUTO);
        }

        public static TelnetPrompts defaults() {
            return new TelnetPrompts(
                    "(?i)(login|username)\\s*:\\s*$",
                    "(?i)password\\s*:\\s*$",
                    "[>#\\$]\\s*$",
                    TelnetLineEnding.AUTO);
        }
    }

    record CommandResult(
            int exitCode,
            String stdout,
            String stderr,
            boolean timedOut,
            boolean truncated,
            long durationMillis,
            List<CommandOutputBlock> commandBlocks) {

        public CommandResult {
            stdout = Objects.requireNonNull(stdout, "stdout");
            stderr = Objects.requireNonNull(stderr, "stderr");
            commandBlocks = List.copyOf(Objects.requireNonNull(commandBlocks, "commandBlocks"));
            if (commandBlocks.isEmpty()) {
                throw new IllegalArgumentException("commandBlocks must not be empty");
            }
            if (durationMillis < 0) {
                throw new IllegalArgumentException("durationMillis must not be negative");
            }
            if (timedOut && exitCode != -1) {
                throw new IllegalArgumentException("timed-out commands must use exit code -1");
            }
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static Duration requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static String requireRegex(String value, String field) {
        String normalized = requireText(value, field);
        if (normalized.length() > 500) {
            throw new IllegalArgumentException(field + " regex exceeds maximum length");
        }
        try {
            Pattern.compile(normalized);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException(field + " regex is invalid", exception);
        }
        return normalized;
    }
}
