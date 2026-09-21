package com.dp.deviceops.core.model;

import com.dp.deviceops.core.port.CommandExecutionPort;

import java.util.Objects;
import java.util.Optional;

/**
 * A single execution destination using only submission-time context and endpoint snapshots.
 */
public final class CollectionTarget {

    private final CollectionContextSnapshot contextSnapshot;
    private final EndpointSnapshot endpointSnapshot;
    private CollectionStatus status = CollectionStatus.QUEUED;
    private String standardOutput = "";
    private String standardError = "";
    private Integer exitCode;
    private String outcomeMessage;
    private boolean outputTruncated;

    private CollectionTarget(CollectionContextSnapshot contextSnapshot, EndpointSnapshot endpointSnapshot) {
        this.contextSnapshot = Objects.requireNonNull(contextSnapshot, "contextSnapshot must not be null");
        this.endpointSnapshot = Objects.requireNonNull(endpointSnapshot, "endpointSnapshot must not be null");
    }

    public static CollectionTarget forSnapshot(CollectionContextSnapshot contextSnapshot, String host, int port,
                                               String username, String hostKeyFingerprint) {
        return forSnapshot(contextSnapshot, ConnectionProtocol.SSH2, host, port, username, hostKeyFingerprint, null);
    }

    public static CollectionTarget forSnapshot(CollectionContextSnapshot contextSnapshot, ConnectionProtocol protocol,
                                               String host, int port, String username, String hostKeyFingerprint,
                                               CommandExecutionPort.TelnetPrompts telnetPrompts) {
        return new CollectionTarget(contextSnapshot,
                new EndpointSnapshot(protocol, host, port, username, hostKeyFingerprint, telnetPrompts));
    }

    /** Restores persisted evidence without replaying the live execution state machine. */
    public static CollectionTarget restore(CollectionContextSnapshot contextSnapshot, String host, int port,
                                           String username, String hostKeyFingerprint, CollectionStatus status,
                                           String standardOutput, String standardError, Integer exitCode,
                                           String outcomeMessage, boolean outputTruncated) {
        return restore(contextSnapshot, ConnectionProtocol.SSH2, host, port, username, hostKeyFingerprint, null,
                status, standardOutput, standardError, exitCode, outcomeMessage, outputTruncated);
    }

    /** Restores persisted evidence without replaying the live execution state machine. */
    public static CollectionTarget restore(CollectionContextSnapshot contextSnapshot, ConnectionProtocol protocol,
                                           String host, int port, String username, String hostKeyFingerprint,
                                           CommandExecutionPort.TelnetPrompts telnetPrompts, CollectionStatus status,
                                           String standardOutput, String standardError, Integer exitCode,
                                           String outcomeMessage, boolean outputTruncated) {
        CollectionTarget target = new CollectionTarget(contextSnapshot,
                new EndpointSnapshot(protocol, host, port, username, hostKeyFingerprint, telnetPrompts));
        target.status = Objects.requireNonNull(status, "status must not be null");
        target.standardOutput = Objects.requireNonNull(standardOutput, "standardOutput must not be null");
        target.standardError = Objects.requireNonNull(standardError, "standardError must not be null");
        target.exitCode = exitCode;
        target.outcomeMessage = outcomeMessage;
        target.outputTruncated = outputTruncated;
        return target;
    }

    public CollectionContextSnapshot contextSnapshot() {
        return contextSnapshot;
    }

    public EndpointSnapshot endpointSnapshot() {
        return endpointSnapshot;
    }

    public CollectionStatus status() {
        return status;
    }

    public String standardOutput() {
        return standardOutput;
    }

    public String standardError() {
        return standardError;
    }

    public Optional<Integer> exitCode() {
        return Optional.ofNullable(exitCode);
    }

    public Optional<String> outcomeMessage() {
        return Optional.ofNullable(outcomeMessage);
    }

    public boolean outputTruncated() {
        return outputTruncated;
    }

    public void startConnecting() {
        transition(CollectionStatus.QUEUED, CollectionStatus.CONNECTING);
    }

    public void startExecuting() {
        transition(CollectionStatus.CONNECTING, CollectionStatus.EXECUTING);
    }

    public void captureOutput(int exitCode, String standardOutput, String standardError, boolean outputTruncated) {
        requireStatus(CollectionStatus.EXECUTING);
        this.exitCode = exitCode;
        this.standardOutput = Objects.requireNonNull(standardOutput, "standardOutput must not be null");
        this.standardError = Objects.requireNonNull(standardError, "standardError must not be null");
        this.outputTruncated = outputTruncated;
    }

    public void startParsing() {
        transition(CollectionStatus.EXECUTING, CollectionStatus.PARSING);
    }

    public void succeed() {
        transition(CollectionStatus.PARSING, CollectionStatus.SUCCEEDED);
    }

    public void partiallySucceed(String message) {
        requireText(message, "message");
        transition(CollectionStatus.PARSING, CollectionStatus.PARTIAL_SUCCESS);
        outcomeMessage = message;
    }

    public void fail(String message) {
        requireText(message, "message");
        if (status != CollectionStatus.CONNECTING && status != CollectionStatus.EXECUTING && status != CollectionStatus.PARSING) {
            throw new IllegalStateException("failure is not allowed from " + status);
        }
        status = CollectionStatus.FAILED;
        outcomeMessage = message;
    }

    public void timeOut(String message) {
        terminate(CollectionStatus.TIMED_OUT, message);
    }

    public void timeOut() {
        timeOut("timed out");
    }

    public void cancel(String message) {
        terminate(CollectionStatus.CANCELLED, message);
    }

    public void cancel() {
        cancel("cancelled");
    }

    private void terminate(CollectionStatus terminalStatus, String message) {
        requireText(message, "message");
        if (isTerminal(status)) {
            throw new IllegalStateException("terminal status cannot transition");
        }
        status = terminalStatus;
        outcomeMessage = message;
    }

    private void transition(CollectionStatus expected, CollectionStatus next) {
        requireStatus(expected);
        status = next;
    }

    private void requireStatus(CollectionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("expected " + expected + " but was " + status);
        }
    }

    private static boolean isTerminal(CollectionStatus candidate) {
        return candidate == CollectionStatus.SUCCEEDED || candidate == CollectionStatus.PARTIAL_SUCCESS
                || candidate == CollectionStatus.FAILED || candidate == CollectionStatus.TIMED_OUT
                || candidate == CollectionStatus.CANCELLED;
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    public record EndpointSnapshot(ConnectionProtocol protocol, String host, int port, String username,
                                   String hostKeyFingerprint, CommandExecutionPort.TelnetPrompts telnetPrompts) {

        public EndpointSnapshot {
            protocol = Objects.requireNonNull(protocol, "protocol must not be null");
            requireText(host, "host");
            requireText(username, "username");
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("port must be between 1 and 65535");
            }
            hostKeyFingerprint = hostKeyFingerprint == null || hostKeyFingerprint.isBlank()
                    ? null : hostKeyFingerprint;
            if (protocol == ConnectionProtocol.SSH2 && telnetPrompts != null) {
                throw new IllegalArgumentException("SSH2 does not use Telnet prompts");
            }
            if (protocol == ConnectionProtocol.TELNET) {
                if (hostKeyFingerprint != null) {
                    throw new IllegalArgumentException("TELNET does not use a host key fingerprint");
                }
                telnetPrompts = Objects.requireNonNull(telnetPrompts, "telnetPrompts must not be null");
            }
        }
    }
}
