package com.dp.deviceops.adapter.web;

import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.port.CommandExecutionPort;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionRequestMapperSerialTest {

    private final ConnectionRequestMapper mapper = new ConnectionRequestMapper(null);

    private static ConnectionRequestMapper.SerialParams serialParams() {
        return new ConnectionRequestMapper.SerialParams(9600, 8,
                CommandExecutionPort.SerialParity.NONE, 1, CommandExecutionPort.SerialFlowControl.NONE);
    }

    private static ConnectionRequestMapper.SerialPrompts serialPrompts() {
        return new ConnectionRequestMapper.SerialPrompts("(?i)(login|username)\\s*:\\s*$",
                "(?i)password\\s*:\\s*$", "[>#\\$]\\s*$", CommandExecutionPort.TelnetLineEnding.AUTO);
    }

    @Test
    void serialDirectRequestBuildsSpecWithZeroPort() {
        CommandExecutionPort.ConnectionSpec spec = mapper.directSpec(new ConnectionRequestMapper.Connection(
                ConnectionProtocol.SERIAL, "COM3", 0, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null, null, serialParams(), serialPrompts(),
                15L, null, null, null, "pw".toCharArray(), null, null, null));
        assertEquals(ConnectionProtocol.SERIAL, spec.protocol());
        assertEquals(0, spec.port());
        assertEquals("COM3", spec.host());
        assertEquals(9600, spec.serialParams().baudRate());
        assertEquals("[>#\\$]\\s*$", spec.serialPrompts().command());
        assertEquals(Duration.ofSeconds(15), spec.connectTimeout());
    }

    @Test
    void serialDirectRequestRejectsNonZeroPort() {
        assertThrows(ResponseStatusException.class, () -> mapper.directSpec(new ConnectionRequestMapper.Connection(
                ConnectionProtocol.SERIAL, "COM3", 23, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null, null, serialParams(), serialPrompts(),
                15L, null, null, null, "pw".toCharArray(), null, null, null)));
    }

    @Test
    void nonSerialDirectRequestStillRequiresPositivePort() {
        assertThrows(ResponseStatusException.class, () -> mapper.directSpec(new ConnectionRequestMapper.Connection(
                ConnectionProtocol.SSH2, "192.0.2.10", 0, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.EXEC, null, null, null, null,
                15L, null, null, null, "pw".toCharArray(), null, null, null)));
    }

    @Test
    void savedReferenceMustNotCarrySerialFields() {
        ConnectionRequestMapper.Connection request = new ConnectionRequestMapper.Connection(
                null, null, null, null, null, null, null, null, serialParams(), serialPrompts(),
                null, "standalone", "conn-1", null, null, null, null, null);
        assertThrows(ResponseStatusException.class, () -> mapper.map("owner-1", "standalone", request));
    }
}
