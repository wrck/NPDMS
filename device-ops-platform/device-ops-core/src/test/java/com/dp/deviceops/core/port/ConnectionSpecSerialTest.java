package com.dp.deviceops.core.port;

import com.dp.deviceops.core.model.ConnectionProtocol;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionSpecSerialTest {

    private static CommandExecutionPort.SerialParams params() {
        return new CommandExecutionPort.SerialParams(9600, 8,
                CommandExecutionPort.SerialParity.NONE, 1, CommandExecutionPort.SerialFlowControl.NONE);
    }

    private static CommandExecutionPort.SerialPrompts prompts() {
        return CommandExecutionPort.SerialPrompts.defaults();
    }

    private static CommandExecutionPort.ConnectionSpec serialSpec(int port,
            CommandExecutionPort.SerialParams params, CommandExecutionPort.SerialPrompts prompts,
            CommandExecutionPort.AuthenticationType authenticationType,
            CommandExecutionPort.ExecutionMode executionMode, String fingerprint) {
        return new CommandExecutionPort.ConnectionSpec(ConnectionProtocol.SERIAL, "COM3", port, "admin",
                authenticationType, executionMode, fingerprint, null, params, prompts, Duration.ofSeconds(15));
    }

    @Test
    void serialConnectionAcceptsZeroPortWithSerialConfiguration() {
        CommandExecutionPort.ConnectionSpec spec = serialSpec(0, params(), prompts(),
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null);
        assertEquals(ConnectionProtocol.SERIAL, spec.protocol());
        assertEquals(0, spec.port());
        assertEquals("COM3", spec.host());
    }

    @Test
    void serialConnectionRejectsNonZeroPort() {
        assertThrows(IllegalArgumentException.class, () -> serialSpec(9600, params(), prompts(),
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null));
    }

    @Test
    void serialConnectionRejectsMissingSerialParams() {
        assertThrows(IllegalArgumentException.class, () -> serialSpec(0, null, prompts(),
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null));
    }

    @Test
    void serialConnectionRejectsMissingSerialPrompts() {
        assertThrows(IllegalArgumentException.class, () -> serialSpec(0, params(), null,
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null));
    }

    @Test
    void serialConnectionRejectsPrivateKeyAuthentication() {
        assertThrows(IllegalArgumentException.class, () -> serialSpec(0, params(), prompts(),
                CommandExecutionPort.AuthenticationType.PRIVATE_KEY,
                CommandExecutionPort.ExecutionMode.SHELL, null));
    }

    @Test
    void serialConnectionRejectsExecMode() {
        assertThrows(IllegalArgumentException.class, () -> serialSpec(0, params(), prompts(),
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.EXEC, null));
    }

    @Test
    void serialConnectionRejectsHostKeyFingerprint() {
        assertThrows(IllegalArgumentException.class, () -> serialSpec(0, params(), prompts(),
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, "SHA256:abc"));
    }

    @Test
    void serialConnectionRejectsTelnetPrompts() {
        assertThrows(IllegalArgumentException.class, () -> new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.SERIAL, "COM3", 0, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null,
                CommandExecutionPort.TelnetPrompts.defaults(), params(), prompts(), Duration.ofSeconds(15)));
    }

    @Test
    void serialParamsDefaultsAre9600_8_None_1_None() {
        CommandExecutionPort.SerialParams defaults = CommandExecutionPort.SerialParams.defaults();
        assertEquals(9600, defaults.baudRate());
        assertEquals(8, defaults.dataBits());
        assertEquals(CommandExecutionPort.SerialParity.NONE, defaults.parity());
        assertEquals(1, defaults.stopBits());
        assertEquals(CommandExecutionPort.SerialFlowControl.NONE, defaults.flowControl());
    }

    @Test
    void serialParamsRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new CommandExecutionPort.SerialParams(0, 8,
                CommandExecutionPort.SerialParity.NONE, 1, CommandExecutionPort.SerialFlowControl.NONE));
        assertThrows(IllegalArgumentException.class, () -> new CommandExecutionPort.SerialParams(9600, 9,
                CommandExecutionPort.SerialParity.NONE, 1, CommandExecutionPort.SerialFlowControl.NONE));
        assertThrows(IllegalArgumentException.class, () -> new CommandExecutionPort.SerialParams(9600, 8,
                CommandExecutionPort.SerialParity.NONE, 3, CommandExecutionPort.SerialFlowControl.NONE));
    }

    @Test
    void serialPromptsDefaultsMirrorTelnetDefaults() {
        CommandExecutionPort.SerialPrompts defaults = CommandExecutionPort.SerialPrompts.defaults();
        assertEquals(CommandExecutionPort.TelnetPrompts.defaults().login(), defaults.login());
        assertEquals(CommandExecutionPort.TelnetPrompts.defaults().password(), defaults.password());
        assertEquals(CommandExecutionPort.TelnetPrompts.defaults().command(), defaults.command());
    }

    @Test
    void nonSerialProtocolsRejectSerialFields() {
        assertThrows(IllegalArgumentException.class, () -> new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.SSH2, "192.0.2.10", 22, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.EXEC, null, null, params(), prompts(), Duration.ofSeconds(15)));
    }

    @Test
    void nineArgConstructorStillBuildsTelnetSpecWithoutSerialFields() {
        CommandExecutionPort.ConnectionSpec spec = new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.TELNET, "192.0.2.10", 23, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null,
                CommandExecutionPort.TelnetPrompts.defaults(), Duration.ofSeconds(15));
        assertEquals(ConnectionProtocol.TELNET, spec.protocol());
    }
}
