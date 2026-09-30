package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.model.SavedConnection;
import com.dp.deviceops.core.model.SavedConnectionDraft;
import com.dp.deviceops.core.model.TransientCredential;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.SavedConnectionCredentialStore;
import com.dp.deviceops.core.port.SavedConnectionStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** JDBC store for complete saved-connection aggregates and their exclusive credentials. */
public final class JdbcSavedConnectionStore implements SavedConnectionStore {
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final SavedConnectionCredentialStore credentials;
    private final Clock clock;

    public JdbcSavedConnectionStore(JdbcClient jdbc, TransactionTemplate transactions,
                                    SavedConnectionCredentialStore credentials, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean available() {
        return credentials.available();
    }

    @Override
    public List<SavedConnection> findAll(String ownerId, String namespace) {
        return jdbc.sql(selectSql() + " WHERE owner_id=:ownerId AND namespace=:namespace "
                        + "ORDER BY updated_at DESC, connection_id")
                .param("ownerId", requireText(ownerId, "ownerId"))
                .param("namespace", requireText(namespace, "namespace"))
                .query(JdbcSavedConnectionStore::mapConnection)
                .list();
    }

    @Override
    public Optional<SavedConnection> find(String ownerId, String namespace, String id) {
        return findRow(ownerId, namespace, id).map(ConnectionRow::connection);
    }

    @Override
    public SavedConnection create(String ownerId, String namespace, SavedConnectionDraft draft,
                                  char[] secret, char[] passphrase) {
        return createIdentified(ownerId, namespace, UUID.randomUUID().toString(), draft, secret, passphrase);
    }

    @Override
    public SavedConnection createIdentified(String ownerId, String namespace, String id, SavedConnectionDraft draft,
                                           char[] secret, char[] passphrase) {
        String normalizedOwnerId = requireText(ownerId, "ownerId");
        String normalizedNamespace = requireText(namespace, "namespace");
        Objects.requireNonNull(draft, "draft");
        String connectionId = requireText(id, "connectionId");
        Instant now = clock.instant();
        return Objects.requireNonNull(transactions.execute(status -> {
            var credential = credentials.createForConnection(normalizedOwnerId, normalizedNamespace, connectionId,
                    draft.connection().authenticationType(), secret, passphrase);
            insert(connectionId, normalizedOwnerId, normalizedNamespace, draft, credential.id(), 0, now);
            return find(normalizedOwnerId, normalizedNamespace, connectionId).orElseThrow();
        }));
    }

    @Override
    public SavedConnection replace(String ownerId, String namespace, String id, long expectedVersion,
                                   SavedConnectionDraft draft, char[] newSecret, char[] newPassphrase) {
        String normalizedOwnerId = requireText(ownerId, "ownerId");
        String normalizedNamespace = requireText(namespace, "namespace");
        String normalizedId = requireText(id, "id");
        requireVersion(expectedVersion);
        Objects.requireNonNull(draft, "draft");
        if (newSecret == null && newPassphrase != null) {
            throw new IllegalArgumentException("newPassphrase requires a replacement credential");
        }
        return Objects.requireNonNull(transactions.execute(status -> {
            ConnectionRow existing = findRow(normalizedOwnerId, normalizedNamespace, normalizedId)
                    .orElseThrow(() -> new SavedConnectionStore.NotFoundException(normalizedId));
            if (newSecret == null
                    && draft.connection().authenticationType() != existing.connection().connection().authenticationType()) {
                throw new IllegalArgumentException("authentication type requires a replacement credential");
            }
            Instant now = clock.instant();
            int updated = updateConnection(normalizedOwnerId, normalizedNamespace, normalizedId, expectedVersion, draft, now);
            if (updated != 1) {
                throw conflictOrNotFound(normalizedOwnerId, normalizedNamespace, normalizedId);
            }
            if (newSecret != null) {
                credentials.replaceForConnection(normalizedOwnerId, normalizedNamespace, existing.credentialId(), normalizedId,
                        draft.connection().authenticationType(), newSecret, newPassphrase);
            }
            return find(normalizedOwnerId, normalizedNamespace, normalizedId)
                    .orElseThrow(() -> new SavedConnectionStore.NotFoundException(normalizedId));
        }));
    }

    @Override
    public SavedConnection rename(String ownerId, String namespace, String id, long expectedVersion,
                                  String displayName, String description) {
        String normalizedOwnerId = requireText(ownerId, "ownerId");
        String normalizedNamespace = requireText(namespace, "namespace");
        String normalizedId = requireText(id, "id");
        requireVersion(expectedVersion);
        String normalizedDisplayName = requireText(displayName, "displayName");
        String normalizedDescription = description == null ? null : description.strip();
        return Objects.requireNonNull(transactions.execute(status -> {
            Instant now = clock.instant();
            int updated = jdbc.sql("UPDATE device_ops_saved_connection SET display_name=:displayName, "
                            + "description=:description, version=version+1, updated_at=:updatedAt "
                            + "WHERE owner_id=:ownerId AND namespace=:namespace AND connection_id=:id AND version=:version")
                    .param("displayName", normalizedDisplayName).param("description", normalizedDescription)
                    .param("updatedAt", now).param("ownerId", normalizedOwnerId)
                    .param("namespace", normalizedNamespace).param("id", normalizedId).param("version", expectedVersion)
                    .update();
            if (updated != 1) {
                throw conflictOrNotFound(normalizedOwnerId, normalizedNamespace, normalizedId);
            }
            return find(normalizedOwnerId, normalizedNamespace, normalizedId)
                    .orElseThrow(() -> new SavedConnectionStore.NotFoundException(normalizedId));
        }));
    }

    @Override
    public <T> T withConnection(String ownerId, String namespace, String id,
                                SavedConnectionOperation<T> operation) {
        String normalizedOwnerId = requireText(ownerId, "ownerId");
        String normalizedNamespace = requireText(namespace, "namespace");
        Objects.requireNonNull(operation, "operation");
        ConnectionSnapshot snapshot = Objects.requireNonNull(transactions.execute(status -> {
            ConnectionRow row = findRowForUpdate(normalizedOwnerId, normalizedNamespace, requireText(id, "id"))
                    .orElseThrow(() -> new SavedConnectionStore.NotFoundException(id));
            return new ConnectionSnapshot(row.connection(),
                    credentials.loadForConnection(normalizedOwnerId, normalizedNamespace, row.credentialId()));
        }));
        try (TransientCredential credential = snapshot.credential()) {
            return operation.apply(snapshot.connection(), credential);
        }
    }

    @Override
    public void delete(String ownerId, String namespace, String id, long expectedVersion) {
        String normalizedOwnerId = requireText(ownerId, "ownerId");
        String normalizedNamespace = requireText(namespace, "namespace");
        String normalizedId = requireText(id, "id");
        requireVersion(expectedVersion);
        transactions.executeWithoutResult(status -> {
            ConnectionRow existing = findRow(normalizedOwnerId, normalizedNamespace, normalizedId)
                    .orElseThrow(() -> new SavedConnectionStore.NotFoundException(normalizedId));
            int deleted = jdbc.sql("DELETE FROM device_ops_saved_connection WHERE owner_id=:ownerId "
                            + "AND namespace=:namespace AND connection_id=:id AND version=:version")
                    .param("ownerId", normalizedOwnerId).param("namespace", normalizedNamespace)
                    .param("id", normalizedId).param("version", expectedVersion).update();
            if (deleted != 1) {
                throw conflictOrNotFound(normalizedOwnerId, normalizedNamespace, normalizedId);
            }
            credentials.deleteForConnection(normalizedOwnerId, normalizedNamespace, existing.credentialId());
        });
    }

    private Optional<ConnectionRow> findRow(String ownerId, String namespace, String id) {
        return jdbc.sql(selectSql() + " WHERE owner_id=:ownerId AND namespace=:namespace AND connection_id=:id")
                .param("ownerId", requireText(ownerId, "ownerId"))
                .param("namespace", requireText(namespace, "namespace"))
                .param("id", requireText(id, "id"))
                .query(JdbcSavedConnectionStore::mapRow)
                .optional();
    }

    private Optional<ConnectionRow> findRowForUpdate(String ownerId, String namespace, String id) {
        return jdbc.sql(selectSql() + " WHERE owner_id=:ownerId AND namespace=:namespace AND connection_id=:id FOR UPDATE")
                .param("ownerId", requireText(ownerId, "ownerId"))
                .param("namespace", requireText(namespace, "namespace"))
                .param("id", requireText(id, "id"))
                .query(JdbcSavedConnectionStore::mapRow)
                .optional();
    }

    private SavedConnectionStore.VersionConflictException conflictOrNotFound(String ownerId, String namespace,
                                                                              String id) {
        if (findRowForUpdate(ownerId, namespace, id).isEmpty()) {
            throw new SavedConnectionStore.NotFoundException(id);
        }
        return new SavedConnectionStore.VersionConflictException(id);
    }

    private void insert(String id, String ownerId, String namespace, SavedConnectionDraft draft,
                        String credentialId, long version, Instant now) {
        CommandExecutionPort.ConnectionSpec connection = draft.connection();
        CommandExecutionPort.TelnetPrompts prompts = connection.telnetPrompts();
        CommandExecutionPort.SerialParams serial = connection.serialParams();
        CommandExecutionPort.SerialPrompts serialPrompts = connection.serialPrompts();
        jdbc.sql("INSERT INTO device_ops_saved_connection (connection_id, owner_id, namespace, display_name, description, "
                        + "protocol, host, port, username, authentication_type, execution_mode, expected_host_key_fingerprint, "
                        + "telnet_login_prompt, telnet_password_prompt, telnet_command_prompt, telnet_line_ending, "
                        + "serial_baud_rate, serial_data_bits, serial_parity, serial_stop_bits, serial_flow_control, "
                        + "serial_login_prompt, serial_password_prompt, serial_command_prompt, serial_line_ending, "
                        + "connect_timeout_millis, credential_id, version, created_at, updated_at) "
                        + "VALUES (:id, :ownerId, :namespace, :displayName, :description, :protocol, :host, :port, :username, "
                        + ":authenticationType, :executionMode, :fingerprint, :loginPrompt, :passwordPrompt, :commandPrompt, "
                        + ":lineEnding, :serialBaudRate, :serialDataBits, :serialParity, :serialStopBits, :serialFlowControl, "
                        + ":serialLoginPrompt, :serialPasswordPrompt, :serialCommandPrompt, :serialLineEnding, "
                        + ":connectTimeoutMillis, :credentialId, :version, :createdAt, :updatedAt)")
                .param("id", id).param("ownerId", ownerId).param("namespace", namespace)
                .param("displayName", draft.displayName()).param("description", draft.description())
                .param("protocol", connection.protocol().name()).param("host", connection.host()).param("port", connection.port())
                .param("username", connection.username()).param("authenticationType", connection.authenticationType().name())
                .param("executionMode", connection.executionMode().name())
                .param("fingerprint", connection.expectedHostKeyFingerprint())
                .param("loginPrompt", prompts == null ? null : prompts.login())
                .param("passwordPrompt", prompts == null ? null : prompts.password())
                .param("commandPrompt", prompts == null ? null : prompts.command())
                .param("lineEnding", prompts == null ? null : prompts.lineEnding().name())
                .param("serialBaudRate", serial == null ? null : serial.baudRate())
                .param("serialDataBits", serial == null ? null : serial.dataBits())
                .param("serialParity", serial == null ? null : serial.parity().name())
                .param("serialStopBits", serial == null ? null : serial.stopBits())
                .param("serialFlowControl", serial == null ? null : serial.flowControl().name())
                .param("serialLoginPrompt", serialPrompts == null ? null : serialPrompts.login())
                .param("serialPasswordPrompt", serialPrompts == null ? null : serialPrompts.password())
                .param("serialCommandPrompt", serialPrompts == null ? null : serialPrompts.command())
                .param("serialLineEnding", serialPrompts == null ? null : serialPrompts.lineEnding().name())
                .param("connectTimeoutMillis", connection.connectTimeout().toMillis())
                .param("credentialId", credentialId).param("version", version).param("createdAt", now).param("updatedAt", now)
                .update();
    }

    private int updateConnection(String ownerId, String namespace, String id, long expectedVersion,
                                 SavedConnectionDraft draft, Instant now) {
        CommandExecutionPort.ConnectionSpec connection = draft.connection();
        CommandExecutionPort.TelnetPrompts prompts = connection.telnetPrompts();
        CommandExecutionPort.SerialParams serial = connection.serialParams();
        CommandExecutionPort.SerialPrompts serialPrompts = connection.serialPrompts();
        return jdbc.sql("UPDATE device_ops_saved_connection SET display_name=:displayName, description=:description, "
                        + "protocol=:protocol, host=:host, port=:port, username=:username, authentication_type=:authenticationType, "
                        + "execution_mode=:executionMode, expected_host_key_fingerprint=:fingerprint, "
                        + "telnet_login_prompt=:loginPrompt, telnet_password_prompt=:passwordPrompt, "
                        + "telnet_command_prompt=:commandPrompt, telnet_line_ending=:lineEnding, "
                        + "serial_baud_rate=:serialBaudRate, serial_data_bits=:serialDataBits, serial_parity=:serialParity, "
                        + "serial_stop_bits=:serialStopBits, serial_flow_control=:serialFlowControl, "
                        + "serial_login_prompt=:serialLoginPrompt, serial_password_prompt=:serialPasswordPrompt, "
                        + "serial_command_prompt=:serialCommandPrompt, serial_line_ending=:serialLineEnding, "
                        + "connect_timeout_millis=:connectTimeoutMillis, version=version+1, updated_at=:updatedAt "
                        + "WHERE owner_id=:ownerId AND namespace=:namespace AND connection_id=:id AND version=:version")
                .param("displayName", draft.displayName()).param("description", draft.description())
                .param("protocol", connection.protocol().name()).param("host", connection.host()).param("port", connection.port())
                .param("username", connection.username()).param("authenticationType", connection.authenticationType().name())
                .param("executionMode", connection.executionMode().name())
                .param("fingerprint", connection.expectedHostKeyFingerprint())
                .param("loginPrompt", prompts == null ? null : prompts.login())
                .param("passwordPrompt", prompts == null ? null : prompts.password())
                .param("commandPrompt", prompts == null ? null : prompts.command())
                .param("lineEnding", prompts == null ? null : prompts.lineEnding().name())
                .param("serialBaudRate", serial == null ? null : serial.baudRate())
                .param("serialDataBits", serial == null ? null : serial.dataBits())
                .param("serialParity", serial == null ? null : serial.parity().name())
                .param("serialStopBits", serial == null ? null : serial.stopBits())
                .param("serialFlowControl", serial == null ? null : serial.flowControl().name())
                .param("serialLoginPrompt", serialPrompts == null ? null : serialPrompts.login())
                .param("serialPasswordPrompt", serialPrompts == null ? null : serialPrompts.password())
                .param("serialCommandPrompt", serialPrompts == null ? null : serialPrompts.command())
                .param("serialLineEnding", serialPrompts == null ? null : serialPrompts.lineEnding().name())
                .param("connectTimeoutMillis", connection.connectTimeout().toMillis()).param("updatedAt", now)
                .param("ownerId", ownerId).param("namespace", namespace).param("id", id).param("version", expectedVersion)
                .update();
    }

    private static ConnectionRow mapRow(ResultSet rs, int row) throws SQLException {
        CommandExecutionPort.TelnetPrompts prompts = rs.getString("telnet_login_prompt") == null ? null
                : new CommandExecutionPort.TelnetPrompts(rs.getString("telnet_login_prompt"),
                rs.getString("telnet_password_prompt"), rs.getString("telnet_command_prompt"),
                CommandExecutionPort.TelnetLineEnding.valueOf(rs.getString("telnet_line_ending")));
        CommandExecutionPort.SerialParams serial = rs.getString("serial_parity") == null ? null
                : new CommandExecutionPort.SerialParams(rs.getInt("serial_baud_rate"), rs.getInt("serial_data_bits"),
                CommandExecutionPort.SerialParity.valueOf(rs.getString("serial_parity")),
                rs.getInt("serial_stop_bits"),
                CommandExecutionPort.SerialFlowControl.valueOf(rs.getString("serial_flow_control")));
        CommandExecutionPort.SerialPrompts serialPrompts = rs.getString("serial_login_prompt") == null ? null
                : new CommandExecutionPort.SerialPrompts(rs.getString("serial_login_prompt"),
                rs.getString("serial_password_prompt"), rs.getString("serial_command_prompt"),
                CommandExecutionPort.TelnetLineEnding.valueOf(rs.getString("serial_line_ending")));
        CommandExecutionPort.ConnectionSpec connection = new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.valueOf(rs.getString("protocol")), rs.getString("host"), rs.getInt("port"),
                rs.getString("username"), CommandExecutionPort.AuthenticationType.valueOf(rs.getString("authentication_type")),
                CommandExecutionPort.ExecutionMode.valueOf(rs.getString("execution_mode")),
                rs.getString("expected_host_key_fingerprint"), prompts, serial, serialPrompts,
                Duration.ofMillis(rs.getLong("connect_timeout_millis")));
        return new ConnectionRow(new SavedConnection(rs.getString("connection_id"), rs.getString("namespace"),
                rs.getString("display_name"), rs.getString("description"), connection, true, rs.getLong("version"),
                rs.getObject("created_at", Instant.class), rs.getObject("updated_at", Instant.class)),
                rs.getString("credential_id"));
    }

    private static SavedConnection mapConnection(ResultSet rs, int row) throws SQLException {
        return mapRow(rs, row).connection();
    }

    private static String selectSql() {
        return "SELECT connection_id, namespace, display_name, description, protocol, host, port, username, "
                + "authentication_type, execution_mode, expected_host_key_fingerprint, telnet_login_prompt, "
                + "telnet_password_prompt, telnet_command_prompt, telnet_line_ending, "
                + "serial_baud_rate, serial_data_bits, serial_parity, serial_stop_bits, serial_flow_control, "
                + "serial_login_prompt, serial_password_prompt, serial_command_prompt, serial_line_ending, "
                + "connect_timeout_millis, credential_id, version, created_at, updated_at FROM device_ops_saved_connection";
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }

    private static void requireVersion(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    private record ConnectionRow(SavedConnection connection, String credentialId) {
    }

    private record ConnectionSnapshot(SavedConnection connection, TransientCredential credential) {
    }
}
