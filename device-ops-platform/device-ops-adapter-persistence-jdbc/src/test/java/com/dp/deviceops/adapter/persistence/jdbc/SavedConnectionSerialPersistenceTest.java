package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.model.SavedConnection;
import com.dp.deviceops.core.model.SavedConnectionDraft;
import com.dp.deviceops.core.model.TransientCredential;
import com.dp.deviceops.core.port.CommandExecutionPort;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SavedConnectionSerialPersistenceTest {

    private static DataSource migrated() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:serial-saved-" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        return source;
    }

    private static JdbcSavedConnectionStore store(DataSource source, JdbcCredentialStore credentials) {
        return new JdbcSavedConnectionStore(JdbcClient.create(source),
                new TransactionTemplate(new DataSourceTransactionManager(source)), credentials, fixedClock());
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC);
    }

    private static JdbcCredentialStore credentialStore(DataSource source) {
        return new JdbcCredentialStore(JdbcClient.create(source),
                new AesGcmCredentialCipher("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE="), fixedClock());
    }

    private static CommandExecutionPort.ConnectionSpec serialSpec() {
        return new CommandExecutionPort.ConnectionSpec(ConnectionProtocol.SERIAL, "COM4", 0, "console-admin",
                CommandExecutionPort.AuthenticationType.PASSWORD, CommandExecutionPort.ExecutionMode.SHELL,
                null, null, CommandExecutionPort.SerialParams.defaults(),
                CommandExecutionPort.SerialPrompts.defaults(), Duration.ofSeconds(20));
    }

    @Test
    void serialSavedConnectionRoundTripsSerialParamsAndPrompts() {
        DataSource source = migrated();
        JdbcCredentialStore credentials = credentialStore(source);
        JdbcSavedConnectionStore store = store(source, credentials);
        SavedConnection saved = store.create("owner-1", "standalone",
                new SavedConnectionDraft("console", null, serialSpec()),
                "console-secret".toCharArray(), null);
        SavedConnection reloaded = store.find("owner-1", "standalone", saved.id()).orElseThrow();
        CommandExecutionPort.ConnectionSpec connection = reloaded.connection();
        assertEquals(ConnectionProtocol.SERIAL, connection.protocol());
        assertEquals("COM4", connection.host());
        assertEquals(0, connection.port());
        assertEquals(9600, connection.serialParams().baudRate());
        assertEquals(CommandExecutionPort.SerialParity.NONE, connection.serialParams().parity());
        assertEquals("(?i)password\\s*:\\s*$", connection.serialPrompts().password());
        assertEquals(Duration.ofSeconds(20), connection.connectTimeout());
    }

    @Test
    void serialSavedConnectionReplaceUpdatesSerialColumns() {
        DataSource source = migrated();
        JdbcCredentialStore credentials = credentialStore(source);
        JdbcSavedConnectionStore store = store(source, credentials);
        SavedConnection saved = store.create("owner-1", "standalone",
                new SavedConnectionDraft("console", null, serialSpec()),
                "console-secret".toCharArray(), null);
        CommandExecutionPort.ConnectionSpec updated = new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.SERIAL, "COM5", 0, "console-admin",
                CommandExecutionPort.AuthenticationType.PASSWORD, CommandExecutionPort.ExecutionMode.SHELL,
                null, null, new CommandExecutionPort.SerialParams(115200, 8,
                CommandExecutionPort.SerialParity.NONE, 1, CommandExecutionPort.SerialFlowControl.NONE),
                CommandExecutionPort.SerialPrompts.defaults(), Duration.ofSeconds(20));
        store.replace("owner-1", "standalone", saved.id(), saved.version(),
                new SavedConnectionDraft("console", null, updated), null, null);
        SavedConnection reloaded = store.find("owner-1", "standalone", saved.id()).orElseThrow();
        assertEquals("COM5", reloaded.connection().host());
        assertEquals(115200, reloaded.connection().serialParams().baudRate());
    }
}
