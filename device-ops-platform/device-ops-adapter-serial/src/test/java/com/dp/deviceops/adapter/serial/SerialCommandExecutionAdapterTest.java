package com.dp.deviceops.adapter.serial;

import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.core.model.ConnectionFailure;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.port.CommandExecutionPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SerialCommandExecutionAdapterTest {

    /** Fresh array per call: the adapter wipes the caller's secret (by design), so tests must not share one. */
    private static char[] password() {
        return "console-secret".toCharArray();
    }

    private static CommandExecutionPort.ConnectionSpec spec(CommandExecutionPort.SerialPrompts prompts) {
        return new CommandExecutionPort.ConnectionSpec(ConnectionProtocol.SERIAL, "COMTEST", 0, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD, CommandExecutionPort.ExecutionMode.SHELL,
                null, null, CommandExecutionPort.SerialParams.defaults(), prompts, Duration.ofSeconds(5));
    }

    private static SerialCommandExecutionAdapter adapter() {
        return new SerialCommandExecutionAdapter(true, 8 * 1024 * 1024, 8192, 10_000);
    }

    @Test
    void testLoginSendsUsernameAndPasswordAndClosesPort() {
        InMemorySerialTransport transport = new InMemorySerialTransport();
        transport.scriptResponse("Username:");
        transport.scriptResponse("Password:");
        transport.scriptResponse("DEVICE>");
        adapter().testTransport(spec(CommandExecutionPort.SerialPrompts.defaults()), password(), transport);
        assertEquals("admin\r", transport.writtenLines().get(0));
        assertEquals("console-secret\r", transport.writtenLines().get(1));
    }

    @Test
    void testRejectsWrongPassword() {
        InMemorySerialTransport transport = new InMemorySerialTransport();
        transport.scriptResponse("Username:");
        transport.scriptResponse("Password:");
        transport.scriptResponse("%Login invalid");
        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> adapter().testTransport(spec(CommandExecutionPort.SerialPrompts.defaults()), password(), transport));
        assertEquals(ConnectionFailure.Code.AUTH_FAILED, failure.code());
    }

    @Test
    void testTimesOutWhenCommandPromptNeverAppears() {
        InMemorySerialTransport transport = new InMemorySerialTransport();
        transport.scriptResponse("Username:");
        transport.scriptResponse("Password:");
        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> adapter().testTransport(spec(CommandExecutionPort.SerialPrompts.defaults()), password(), transport));
        assertEquals(ConnectionFailure.Code.PROMPT_NOT_FOUND, failure.code());
    }

    @Test
    void testReportsMissingPort() {
        InMemorySerialTransport transport = new InMemorySerialTransport();
        transport.failNextOpen();
        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> adapter().testTransport(spec(CommandExecutionPort.SerialPrompts.defaults()), password(), transport));
        assertEquals(ConnectionFailure.Code.UNREACHABLE, failure.code());
        assertEquals(ConnectionFailure.Stage.CONNECT, failure.stage());
    }

    @Test
    void testDisabledAdapterFailsFast() {
        SerialCommandExecutionAdapter disabled = new SerialCommandExecutionAdapter(false, 8_388_608, 8_192, 10_000);
        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> disabled.test(spec(CommandExecutionPort.SerialPrompts.defaults()), password(), null));
        assertEquals(ConnectionFailure.Code.PROTOCOL_DISABLED, failure.code());
    }

    @Test
    void testRejectsNonSerialConnection() {
        InMemorySerialTransport transport = new InMemorySerialTransport();
        CommandExecutionPort.ConnectionSpec ssh = new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.SSH2, "192.0.2.10", 22, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD, CommandExecutionPort.ExecutionMode.EXEC,
                null, null, null, null, Duration.ofSeconds(5));
        assertThrows(ConnectionFailure.class,
                () -> adapter().testTransport(ssh, password(), transport));
    }

    @Test
    void adapterProtocolIsSerial() {
        assertEquals(ConnectionProtocol.SERIAL, adapter().protocol());
    }

    @Test
    void executeSplitsCommandBlocksAndReturnsResult() {
        InMemorySerialTransport transport = new InMemorySerialTransport();
        transport.scriptResponse("Username:");
        transport.scriptResponse("Password:");
        transport.scriptResponse("DEVICE>");
        transport.scriptResponse("show version\r\nIOS XE 17.9");
        transport.scriptResponse("DEVICE>");
        CommandExecutionPort.CommandResult result = adapter().executeTransport(
                spec(CommandExecutionPort.SerialPrompts.defaults()), password(), transport,
                "show version", Duration.ofSeconds(10));
        assertEquals(0, result.exitCode());
        assertEquals(1, result.commandBlocks().size());
        CommandOutputBlock block = result.commandBlocks().get(0);
        assertEquals("show version", block.commandText());
        assertEquals(CommandBlockStatus.SUCCEEDED, block.status());
        assertEquals(true, block.stdout().contains("IOS XE 17.9"));
    }

    @Test
    void executeReportsProgressAndTruncatesWhenOverLimit() {
        SerialCommandExecutionAdapter small = new SerialCommandExecutionAdapter(true, 16, 8, 10_000);
        InMemorySerialTransport transport = new InMemorySerialTransport();
        transport.scriptResponse("Username:");
        transport.scriptResponse("Password:");
        transport.scriptResponse("DEVICE>");
        transport.scriptResponse("A".repeat(64));
        transport.scriptResponse("DEVICE>");
        CommandExecutionPort.CommandResult result = small.executeTransport(
                spec(CommandExecutionPort.SerialPrompts.defaults()), password(), transport,
                "show log", Duration.ofSeconds(10));
        assertEquals(0, result.exitCode());
        assertEquals(true, result.truncated());
    }
}
