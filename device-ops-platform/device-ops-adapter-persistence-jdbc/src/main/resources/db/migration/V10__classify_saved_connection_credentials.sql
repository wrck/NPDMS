ALTER TABLE device_ops_credential
    ADD COLUMN credential_scope VARCHAR(32) NOT NULL DEFAULT 'USER_MANAGED';

UPDATE device_ops_credential
SET credential_scope = 'SAVED_CONNECTION_INTERNAL'
WHERE credential_id IN (
    SELECT credential_id FROM device_ops_saved_connection
);

ALTER TABLE device_ops_credential
    ADD CONSTRAINT ck_device_ops_credential_scope
    CHECK (credential_scope IN ('USER_MANAGED', 'SAVED_CONNECTION_INTERNAL'));

CREATE INDEX idx_device_ops_credential_scope_namespace
    ON device_ops_credential(credential_scope, owner_id, namespace, updated_at);
