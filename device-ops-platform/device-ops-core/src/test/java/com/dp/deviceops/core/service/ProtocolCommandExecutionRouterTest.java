package com.dp.deviceops.core.service;

import com.dp.deviceops.core.model.ConnectionFailure;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.ProtocolCommandExecutionAdapter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolCommandExecutionRouterTest {

    @Test
    void telnetRequiresPasswordShellNoFingerprintAndValidPrompts() {
        CommandExecutionPort.TelnetPrompts prompts = CommandExecutionPort.TelnetPrompts.defaults();
        CommandExecutionPort.ConnectionSpec spec = telnetSpec(
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null, prompts);

        assertEquals(ConnectionProtocol.TELNET, spec.protocol());
        assertSame(prompts, spec.telnetPrompts());
        assertEquals(null, spec.expectedHostKeyFingerprint());
        assertThrows(IllegalArgumentException.class, () -> telnetSpec(
                CommandExecutionPort.AuthenticationType.PRIVATE_KEY,
                CommandExecutionPort.ExecutionMode.SHELL, null, prompts));
        assertThrows(IllegalArgumentException.class, () -> telnetSpec(
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.EXEC, null, prompts));
        assertThrows(IllegalArgumentException.class, () -> telnetSpec(
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, "SHA256:not-used", prompts));
        assertThrows(NullPointerException.class, () -> telnetSpec(
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CommandExecutionPort.TelnetPrompts("[", "password:", "[>#]"));
        assertThrows(IllegalArgumentException.class,
                () -> new CommandExecutionPort.TelnetPrompts("x".repeat(501), "password:", "[>#]"));
    }

    @Test
    void sshAllowsMissingHostKeyFingerprintAndNormalizesBlankValues() {
        CommandExecutionPort.ConnectionSpec missing = new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.SSH2, "host", 22, "operator",
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.EXEC, null, null, Duration.ofSeconds(3));
        CommandExecutionPort.ConnectionSpec blank = new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.SSH2, "host", 22, "operator",
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.EXEC, "   ", null, Duration.ofSeconds(3));

        assertEquals(null, missing.expectedHostKeyFingerprint());
        assertEquals(null, blank.expectedHostKeyFingerprint());
    }

    @Test
    void missingAdapterReportsProtocolDisabled() {
        ProtocolCommandExecutionRouter router = new ProtocolCommandExecutionRouter(List.of());

        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> router.test(telnetSpec(CommandExecutionPort.AuthenticationType.PASSWORD,
                        CommandExecutionPort.ExecutionMode.SHELL, null,
                        CommandExecutionPort.TelnetPrompts.defaults()), new char[0], null));

        assertEquals(ConnectionFailure.Code.PROTOCOL_DISABLED, failure.code());
        assertEquals(ConnectionFailure.Stage.CONNECT, failure.stage());
    }

    @Test
    void rejectsDuplicateProtocolsAndDelegatesToTheMatchingAdapter() {
        AtomicBoolean tested = new AtomicBoolean();
        CommandExecutionPort.CommandResult expected =
                new CommandExecutionPort.CommandResult(0, "ok", "", false, false, 1,
                        List.of(com.dp.deviceops.core.model.CommandOutputBlock.legacy(
                                "ok", "", 0, false, java.util.Map.of(), null)));
        ProtocolCommandExecutionAdapter adapter = new StubAdapter(ConnectionProtocol.TELNET, tested, expected);

        assertThrows(IllegalArgumentException.class,
                () -> new ProtocolCommandExecutionRouter(List.of(adapter, adapter)));

        ProtocolCommandExecutionRouter router = new ProtocolCommandExecutionRouter(List.of(adapter));
        CommandExecutionPort.ConnectionSpec connection = telnetSpec(
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null,
                CommandExecutionPort.TelnetPrompts.defaults());
        router.test(connection, new char[0], null);
        CommandExecutionPort.CommandResult actual =
                router.execute(connection, new char[0], null, "show version", Duration.ofSeconds(2));

        assertTrue(tested.get());
        assertSame(expected, actual);
    }

    @Test
    void connectionFailureDoesNotAppendCauseMessage() {
        ConnectionFailure failure = new ConnectionFailure(ConnectionFailure.Code.AUTH_FAILED,
                ConnectionFailure.Stage.AUTHENTICATE, "authentication rejected",
                new IllegalStateException("sensitive remote response"));

        assertEquals("authentication rejected", failure.safeMessage());
        assertEquals("authentication rejected", failure.getMessage());
        assertFalse(failure.getMessage().contains("sensitive remote response"));
    }

    private static CommandExecutionPort.ConnectionSpec telnetSpec(
            CommandExecutionPort.AuthenticationType authenticationType,
            CommandExecutionPort.ExecutionMode executionMode,
            String fingerprint,
            CommandExecutionPort.TelnetPrompts prompts) {
        return new CommandExecutionPort.ConnectionSpec(ConnectionProtocol.TELNET, "host", 23, "operator",
                authenticationType, executionMode, fingerprint, prompts, Duration.ofSeconds(3));
    }

    private record StubAdapter(ConnectionProtocol protocol, AtomicBoolean tested,
                               CommandExecutionPort.CommandResult result)
            implements ProtocolCommandExecutionAdapter {

        @Override
        public void test(ConnectionSpec connection, char[] secret, char[] passphrase) {
            tested.set(true);
        }

        @Override
        public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase, String script,
                                     Duration timeout) {
            return result;
        }
    }
}
