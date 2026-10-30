CREATE TABLE device_ops_credential (
    credential_id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(200) NOT NULL,
    namespace VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    authentication_type VARCHAR(32) NOT NULL,
    secret_ciphertext BLOB NOT NULL,
    secret_nonce VARBINARY(12) NOT NULL,
    passphrase_ciphertext BLOB,
    passphrase_nonce VARBINARY(12),
    key_version VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_device_ops_credential_name UNIQUE (owner_id, namespace, display_name),
    CONSTRAINT ck_device_ops_credential_passphrase CHECK (
        (passphrase_ciphertext IS NULL AND passphrase_nonce IS NULL)
        OR (passphrase_ciphertext IS NOT NULL AND passphrase_nonce IS NOT NULL)
    )
);

CREATE INDEX idx_device_ops_credential_namespace
    ON device_ops_credential(owner_id, namespace, updated_at);
