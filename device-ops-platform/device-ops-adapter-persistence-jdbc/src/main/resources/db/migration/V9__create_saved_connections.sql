CREATE TABLE device_ops_saved_connection (
    connection_id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(200) NOT NULL,
    namespace VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    protocol VARCHAR(20) NOT NULL,
    host VARCHAR(500) NOT NULL,
    port INTEGER NOT NULL,
    username VARCHAR(500) NOT NULL,
    authentication_type VARCHAR(32) NOT NULL,
    execution_mode VARCHAR(32) NOT NULL,
    expected_host_key_fingerprint VARCHAR(1000),
    telnet_login_prompt VARCHAR(500),
    telnet_password_prompt VARCHAR(500),
    telnet_command_prompt VARCHAR(500),
    telnet_line_ending VARCHAR(16),
    connect_timeout_millis BIGINT NOT NULL,
    credential_id VARCHAR(36) NOT NULL UNIQUE,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_device_ops_saved_connection_name UNIQUE (owner_id, namespace, display_name),
    CONSTRAINT fk_device_ops_saved_connection_credential
        FOREIGN KEY (credential_id) REFERENCES device_ops_credential (credential_id) ON DELETE RESTRICT
);

CREATE INDEX idx_device_ops_saved_connection_namespace
    ON device_ops_saved_connection(owner_id, namespace, updated_at);
