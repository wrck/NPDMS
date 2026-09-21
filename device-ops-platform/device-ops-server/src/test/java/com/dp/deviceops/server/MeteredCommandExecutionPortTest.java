package com.dp.deviceops.server;

import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.port.CommandExecutionPort;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeteredCommandExecutionPortTest {

    @Test
    void delegatesTestAndBothExecuteVariantsWithoutChangingArgumentsOrResults() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RecordingPort delegate = new RecordingPort();
        MeteredCommandExecutionPort metered = new MeteredCommandExecutionPort(delegate, registry);
        CommandExecutionPort.ConnectionSpec connection = connection(ConnectionProtocol.SSH2);
        char[] secret = "secret-value".toCharArray();
        char[] passphrase = "passphrase-value".toCharArray();
        String script = "show running-config";
        Duration timeout = Duration.ofSeconds(17);
        CommandExecutionPort.ProgressListener listener = progress -> { };
        CommandExecutionPort.CommandResult expected = new CommandExecutionPort.CommandResult(
                7, "stdout", "stderr", false, false, 12,
                List.of(com.dp.deviceops.core.model.CommandOutputBlock.legacy(
                        "stdout", "stderr", 7, false, Map.of(), null)));
        delegate.result = expected;

        metered.test(connection, secret, passphrase);
        assertSame(connection, delegate.connection);
        assertSame(secret, delegate.secret);
        assertSame(passphrase, delegate.passphrase);
        assertTrue(registry.getMeters().isEmpty());

        assertSame(expected, metered.execute(connection, secret, passphrase, script, timeout));
        assertSame(connection, delegate.connection);
        assertSame(secret, delegate.secret);
        assertSame(passphrase, delegate.passphrase);
        assertSame(script, delegate.script);
        assertSame(timeout, delegate.timeout);

        assertSame(expected, metered.execute(connection, secret, passphrase, script, timeout, listener));
        assertSame(listener, delegate.listener);
        assertEquals(1, delegate.listenerExecuteCount);
    }

    @Test
    void recordsOnlyProtocolAndBoundedOutcomeForSuccessTimeoutAndFailure() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RuntimeException failure = new IllegalStateException("connector failed");
        OutcomePort delegate = new OutcomePort(failure);
        MeteredCommandExecutionPort metered = new MeteredCommandExecutionPort(delegate, registry);

        CommandExecutionPort.CommandResult completedWithNonZeroExit = metered.execute(
                connection(ConnectionProtocol.SSH2), new char[0], null,
                "success-command", Duration.ofSeconds(1));
        assertEquals(9, completedWithNonZeroExit.exitCode());

        CommandExecutionPort.CommandResult timedOut = metered.execute(
                connection(ConnectionProtocol.TELNET), new char[0], null,
                "timeout-command", Duration.ofSeconds(1), CommandExecutionPort.ProgressListener.noop());
        assertTrue(timedOut.timedOut());

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> metered.execute(
                connection(ConnectionProtocol.SSH2), new char[0], null,
                "failure-command", Duration.ofSeconds(1)));
        assertSame(failure, thrown);

        assertCommandMeters(registry, "SSH2", "success");
        assertCommandMeters(registry, "TELNET", "timeout");
        assertCommandMeters(registry, "SSH2", "failure");

        Set<String> protocols = registry.getMeters().stream()
                .map(Meter::getId)
                .map(id -> id.getTag("protocol"))
                .collect(java.util.stream.Collectors.toSet());
        Set<String> outcomes = registry.getMeters().stream()
                .map(Meter::getId)
                .map(id -> id.getTag("outcome"))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("SSH2", "TELNET"), protocols);
        assertEquals(Set.of("success", "timeout", "failure"), outcomes);
        registry.getMeters().forEach(meter -> assertEquals(
                Set.of("protocol", "outcome"),
                meter.getId().getTags().stream()
                        .map(tag -> tag.getKey())
                        .collect(java.util.stream.Collectors.toSet())));
    }

    private static void assertCommandMeters(SimpleMeterRegistry registry, String protocol, String outcome) {
        assertEquals(1.0, registry.get("device_ops_command_executions_total")
                .tags("protocol", protocol, "outcome", outcome)
                .counter().count());
        assertEquals(1, registry.get("device_ops_command_execution_duration")
                .tags("protocol", protocol, "outcome", outcome)
                .timer().count());
    }

    private static CommandExecutionPort.ConnectionSpec connection(ConnectionProtocol protocol) {
        if (protocol == ConnectionProtocol.TELNET) {
            return new CommandExecutionPort.ConnectionSpec(
                    protocol, "telnet.example", 23, "operator",
                    CommandExecutionPort.AuthenticationType.PASSWORD,
                    CommandExecutionPort.ExecutionMode.SHELL,
                    null, CommandExecutionPort.TelnetPrompts.defaults(), Duration.ofSeconds(1));
        }
        return new CommandExecutionPort.ConnectionSpec(
                protocol, "ssh.example", 22, "operator",
                CommandExecutionPort.AuthenticationType.PRIVATE_KEY,
                CommandExecutionPort.ExecutionMode.EXEC,
                "SHA256:example", null, Duration.ofSeconds(1));
    }

    private static final class RecordingPort implements CommandExecutionPort {
        private ConnectionSpec connection;
        private char[] secret;
        private char[] passphrase;
        private String script;
        private Duration timeout;
        private ProgressListener listener;
        private CommandResult result;
        private int listenerExecuteCount;

        @Override
        public void test(ConnectionSpec connection, char[] secret, char[] passphrase) {
            capture(connection, secret, passphrase, null, null);
        }

        @Override
        public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase,
                                     String script, Duration timeout) {
            capture(connection, secret, passphrase, script, timeout);
            return result;
        }

        @Override
        public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase,
                                     String script, Duration timeout, ProgressListener listener) {
            capture(connection, secret, passphrase, script, timeout);
            this.listener = listener;
            listenerExecuteCount++;
            return result;
        }

        private void capture(ConnectionSpec connection, char[] secret, char[] passphrase,
                             String script, Duration timeout) {
            this.connection = connection;
            this.secret = secret;
            this.passphrase = passphrase;
            this.script = script;
            this.timeout = timeout;
        }
    }

    private static final class OutcomePort implements CommandExecutionPort {
        private final RuntimeException failure;

        private OutcomePort(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void test(ConnectionSpec connection, char[] secret, char[] passphrase) {
        }

        @Override
        public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase,
                                     String script, Duration timeout) {
            return executeOutcome(script);
        }

        @Override
        public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase,
                                     String script, Duration timeout, ProgressListener listener) {
            return executeOutcome(script);
        }

        private CommandResult executeOutcome(String script) {
            return switch (script) {
                case "success-command" -> new CommandResult(9, "done", "warning", false, false, 5,
                        List.of(com.dp.deviceops.core.model.CommandOutputBlock.legacy(
                                "done", "warning", 9, false, Map.of(), null)));
                case "timeout-command" -> new CommandResult(-1, "partial", "", true, false, 1_000,
                        List.of(com.dp.deviceops.core.model.CommandOutputBlock.legacy(
                                "partial", "", -1, false, Map.of(), "timed out")));
                case "failure-command" -> throw failure;
                default -> throw new AssertionError("unexpected test script");
            };
        }
    }
}
