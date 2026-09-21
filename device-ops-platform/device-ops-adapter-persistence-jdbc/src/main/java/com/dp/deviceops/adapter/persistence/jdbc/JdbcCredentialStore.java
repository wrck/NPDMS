package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.SavedCredential;
import com.dp.deviceops.core.model.TransientCredential;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.CredentialStore;
import com.dp.deviceops.core.port.SavedConnectionCredentialStore;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** JDBC storage with separate user-managed and saved-connection credential boundaries. */
public final class JdbcCredentialStore implements CredentialStore, SavedConnectionCredentialStore {
    private static final String USER_MANAGED = "USER_MANAGED";
    private static final String SAVED_CONNECTION_INTERNAL = "SAVED_CONNECTION_INTERNAL";

    private final JdbcClient jdbc;
    private final AesGcmCredentialCipher cipher;
    private final Clock clock;

    public JdbcCredentialStore(JdbcClient jdbc, AesGcmCredentialCipher cipher, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean available() {
        return cipher.available();
    }

    @Override
    public SavedCredential save(String ownerId, String namespace, String name,
                                CommandExecutionPort.AuthenticationType authenticationType,
                                char[] secret, char[] passphrase) {
        return save(ownerId, namespace, name, authenticationType, secret, passphrase, USER_MANAGED);
    }

    @Override
    public SavedCredential createForConnection(String ownerId, String namespace, String connectionId,
                                               CommandExecutionPort.AuthenticationType authenticationType,
                                               char[] secret, char[] passphrase) {
        String normalizedConnectionId = requireText(connectionId, "connectionId");
        return save(ownerId, namespace, internalName(normalizedConnectionId), authenticationType,
                secret, passphrase, SAVED_CONNECTION_INTERNAL);
    }

    @Override
    public SavedCredential replace(String ownerId, String namespace, String id, String name,
                                   CommandExecutionPort.AuthenticationType authenticationType,
                                   char[] secret, char[] passphrase) {
        return replace(ownerId, namespace, id, name, authenticationType, secret, passphrase, USER_MANAGED);
    }

    @Override
    public SavedCredential replaceForConnection(String ownerId, String namespace, String credentialId, String connectionId,
                                                CommandExecutionPort.AuthenticationType authenticationType,
                                                char[] secret, char[] passphrase) {
        String normalizedConnectionId = requireText(connectionId, "connectionId");
        return replace(ownerId, namespace, credentialId, internalName(normalizedConnectionId), authenticationType,
                secret, passphrase, SAVED_CONNECTION_INTERNAL);
    }

    @Override
    public List<SavedCredential> findAll(String ownerId, String namespace) {
        return jdbc.sql("SELECT credential_id, namespace, display_name, authentication_type, key_version, created_at, updated_at "
                        + "FROM device_ops_credential WHERE owner_id=:ownerId AND namespace=:namespace "
                        + "AND credential_scope=:scope ORDER BY display_name, credential_id")
                .param("ownerId", requireText(ownerId, "ownerId"))
                .param("namespace", requireText(namespace, "namespace")).param("scope", USER_MANAGED)
                .query(JdbcCredentialStore::metadata).list();
    }

    @Override
    public Optional<SavedCredential> find(String ownerId, String namespace, String id) {
        return find(ownerId, namespace, id, USER_MANAGED);
    }

    @Override
    public <T> T withCredential(String ownerId, String namespace, String id,
                                TransientCredential.CredentialOperation<T> operation) {
        Objects.requireNonNull(operation, "operation");
        try (TransientCredential credential = load(ownerId, namespace, id, USER_MANAGED)) {
            return credential.withCredentials(operation);
        }
    }

    @Override
    public TransientCredential loadForConnection(String ownerId, String namespace, String credentialId) {
        return load(ownerId, namespace, credentialId, SAVED_CONNECTION_INTERNAL);
    }

    @Override
    public void delete(String ownerId, String namespace, String id) {
        delete(ownerId, namespace, id, USER_MANAGED);
    }

    @Override
    public void deleteForConnection(String ownerId, String namespace, String credentialId) {
        delete(ownerId, namespace, credentialId, SAVED_CONNECTION_INTERNAL);
    }

    private SavedCredential save(String ownerId, String namespace, String name,
                                 CommandExecutionPort.AuthenticationType authenticationType,
                                 char[] secret, char[] passphrase, String scope) {
        requireMaterial(authenticationType, secret, passphrase);
        String id = UUID.randomUUID().toString();
        Instant now = clock.instant();
        EncryptedMaterial material = encrypt(secret, passphrase);
        try {
            jdbc.sql("INSERT INTO device_ops_credential "
                            + "(credential_id, owner_id, namespace, display_name, credential_scope, authentication_type, "
                            + "secret_ciphertext, secret_nonce, passphrase_ciphertext, passphrase_nonce, key_version, "
                            + "created_at, updated_at) VALUES (:id, :ownerId, :namespace, :name, :scope, :type, :secret, "
                            + ":secretNonce, :passphrase, :passphraseNonce, :keyVersion, :createdAt, :updatedAt)")
                    .param("id", id).param("ownerId", requireText(ownerId, "ownerId"))
                    .param("namespace", requireText(namespace, "namespace"))
                    .param("name", requireText(name, "name")).param("scope", scope)
                    .param("type", authenticationType.name())
                    .param("secret", material.secret().ciphertext()).param("secretNonce", material.secret().nonce())
                    .param("passphrase", material.passphraseCiphertext()).param("passphraseNonce", material.passphraseNonce())
                    .param("keyVersion", AesGcmCredentialCipher.KEY_VERSION)
                    .param("createdAt", now).param("updatedAt", now).update();
            return new SavedCredential(id, namespace, name, authenticationType,
                    AesGcmCredentialCipher.KEY_VERSION, now, now);
        } finally {
            material.clear();
        }
    }

    private SavedCredential replace(String ownerId, String namespace, String id, String name,
                                    CommandExecutionPort.AuthenticationType authenticationType,
                                    char[] secret, char[] passphrase, String scope) {
        requireMaterial(authenticationType, secret, passphrase);
        Instant now = clock.instant();
        EncryptedMaterial material = encrypt(secret, passphrase);
        try {
            int updated = jdbc.sql("UPDATE device_ops_credential SET display_name=:name, authentication_type=:type, "
                            + "secret_ciphertext=:secret, secret_nonce=:secretNonce, passphrase_ciphertext=:passphrase, "
                            + "passphrase_nonce=:passphraseNonce, key_version=:keyVersion, updated_at=:updatedAt "
                            + "WHERE owner_id=:ownerId AND namespace=:namespace AND credential_id=:id AND credential_scope=:scope")
                    .param("name", requireText(name, "name")).param("type", authenticationType.name())
                    .param("secret", material.secret().ciphertext()).param("secretNonce", material.secret().nonce())
                    .param("passphrase", material.passphraseCiphertext()).param("passphraseNonce", material.passphraseNonce())
                    .param("keyVersion", AesGcmCredentialCipher.KEY_VERSION).param("updatedAt", now)
                    .param("ownerId", requireText(ownerId, "ownerId"))
                    .param("namespace", requireText(namespace, "namespace")).param("id", requireText(id, "id"))
                    .param("scope", scope).update();
            if (updated != 1) {
                throw new IllegalArgumentException("credential not found");
            }
            return find(ownerId, namespace, id, scope).orElseThrow();
        } finally {
            material.clear();
        }
    }

    private Optional<SavedCredential> find(String ownerId, String namespace, String id, String scope) {
        return jdbc.sql("SELECT credential_id, namespace, display_name, authentication_type, key_version, created_at, updated_at "
                        + "FROM device_ops_credential WHERE owner_id=:ownerId AND namespace=:namespace "
                        + "AND credential_id=:id AND credential_scope=:scope")
                .param("ownerId", requireText(ownerId, "ownerId"))
                .param("namespace", requireText(namespace, "namespace")).param("id", requireText(id, "id"))
                .param("scope", scope).query(JdbcCredentialStore::metadata).optional();
    }

    private TransientCredential load(String ownerId, String namespace, String id, String scope) {
        StoredMaterial stored = jdbc.sql("SELECT secret_ciphertext, secret_nonce, passphrase_ciphertext, "
                        + "passphrase_nonce, key_version FROM device_ops_credential WHERE owner_id=:ownerId "
                        + "AND namespace=:namespace AND credential_id=:id AND credential_scope=:scope")
                .param("ownerId", requireText(ownerId, "ownerId"))
                .param("namespace", requireText(namespace, "namespace")).param("id", requireText(id, "id"))
                .param("scope", scope)
                .query((rs, row) -> new StoredMaterial(rs.getBytes("secret_ciphertext"), rs.getBytes("secret_nonce"),
                        rs.getBytes("passphrase_ciphertext"), rs.getBytes("passphrase_nonce"),
                        rs.getString("key_version"))).optional()
                .orElseThrow(() -> new IllegalArgumentException("credential not found"));
        char[] secret = null;
        char[] passphrase = null;
        try {
            secret = cipher.decrypt(stored.secretCiphertext(), stored.secretNonce(), stored.keyVersion());
            passphrase = cipher.decrypt(stored.passphraseCiphertext(), stored.passphraseNonce(), stored.keyVersion());
            return new TransientCredential(secret, passphrase);
        } finally {
            clear(secret);
            clear(passphrase);
            stored.clear();
        }
    }

    private void delete(String ownerId, String namespace, String id, String scope) {
        int deleted = jdbc.sql("DELETE FROM device_ops_credential WHERE owner_id=:ownerId AND namespace=:namespace "
                        + "AND credential_id=:id AND credential_scope=:scope")
                .param("ownerId", requireText(ownerId, "ownerId"))
                .param("namespace", requireText(namespace, "namespace")).param("id", requireText(id, "id"))
                .param("scope", scope).update();
        if (deleted != 1) {
            throw new IllegalArgumentException("credential not found");
        }
    }

    private EncryptedMaterial encrypt(char[] secret, char[] passphrase) {
        AesGcmCredentialCipher.EncryptedValue encryptedSecret = cipher.encrypt(secret);
        AesGcmCredentialCipher.EncryptedValue encryptedPassphrase = cipher.encrypt(passphrase);
        return new EncryptedMaterial(encryptedSecret, encryptedPassphrase);
    }

    private static SavedCredential metadata(ResultSet rs, int row) throws SQLException {
        return new SavedCredential(rs.getString("credential_id"), rs.getString("namespace"),
                rs.getString("display_name"),
                CommandExecutionPort.AuthenticationType.valueOf(rs.getString("authentication_type")),
                rs.getString("key_version"), rs.getObject("created_at", Instant.class),
                rs.getObject("updated_at", Instant.class));
    }

    private static void requireMaterial(CommandExecutionPort.AuthenticationType type,
                                        char[] secret, char[] passphrase) {
        Objects.requireNonNull(type, "authenticationType");
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("credential secret is required");
        }
        if (type == CommandExecutionPort.AuthenticationType.PASSWORD
                && passphrase != null && passphrase.length > 0) {
            throw new IllegalArgumentException("password credential does not accept a passphrase");
        }
    }

    private static String internalName(String connectionId) {
        return "saved-connection:" + connectionId;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }

    private static void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    private record EncryptedMaterial(AesGcmCredentialCipher.EncryptedValue secret,
                                     AesGcmCredentialCipher.EncryptedValue passphrase) {
        byte[] passphraseCiphertext() { return passphrase == null ? null : passphrase.ciphertext(); }
        byte[] passphraseNonce() { return passphrase == null ? null : passphrase.nonce(); }
        void clear() {
            Arrays.fill(secret.ciphertext(), (byte) 0);
            Arrays.fill(secret.nonce(), (byte) 0);
            if (passphrase != null) {
                Arrays.fill(passphrase.ciphertext(), (byte) 0);
                Arrays.fill(passphrase.nonce(), (byte) 0);
            }
        }
    }

    private record StoredMaterial(byte[] secretCiphertext, byte[] secretNonce,
                                  byte[] passphraseCiphertext, byte[] passphraseNonce,
                                  String keyVersion) {
        void clear() {
            Arrays.fill(secretCiphertext, (byte) 0);
            Arrays.fill(secretNonce, (byte) 0);
            if (passphraseCiphertext != null) Arrays.fill(passphraseCiphertext, (byte) 0);
            if (passphraseNonce != null) Arrays.fill(passphraseNonce, (byte) 0);
        }
    }
}
