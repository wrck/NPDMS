CREATE TABLE device_ops_script (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    namespace VARCHAR(100) NOT NULL,
    script_key VARCHAR(200) NOT NULL,
    source VARCHAR(40) NOT NULL,
    CONSTRAINT uk_device_ops_script_namespace_key UNIQUE (namespace, script_key)
) ENGINE=InnoDB;

CREATE TABLE device_ops_script_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL,
    version VARCHAR(100) NOT NULL,
    content LONGTEXT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    parser_type VARCHAR(100) NOT NULL,
    parser_config LONGTEXT,
    CONSTRAINT uk_device_ops_script_version UNIQUE (script_id, version),
    CONSTRAINT fk_device_ops_script_version_script FOREIGN KEY (script_id) REFERENCES device_ops_script(id)
) ENGINE=InnoDB;

CREATE TABLE device_ops_collection (
    task_id VARCHAR(100) PRIMARY KEY,
    namespace VARCHAR(100) NOT NULL,
    project_key VARCHAR(200),
    external_request_id VARCHAR(200),
    idempotency_key VARCHAR(200) NOT NULL,
    activity_type VARCHAR(100),
    callback_uri VARCHAR(2000),
    script_source VARCHAR(40) NOT NULL,
    script_key VARCHAR(200) NOT NULL,
    script_version VARCHAR(100) NOT NULL,
    script_content LONGTEXT NOT NULL,
    script_sha256 CHAR(64) NOT NULL,
    script_policy VARCHAR(40) NOT NULL,
    parser_type VARCHAR(100) NOT NULL,
    parser_config LONGTEXT,
    output_sequence BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_device_ops_collection_idempotency UNIQUE (namespace, idempotency_key),
    CONSTRAINT uk_device_ops_collection_external_request UNIQUE (namespace, external_request_id)
) ENGINE=InnoDB;

CREATE TABLE device_ops_collection_target (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    project_name VARCHAR(500),
    project_code VARCHAR(200),
    device_key VARCHAR(200),
    device_name VARCHAR(500),
    vendor VARCHAR(200),
    model VARCHAR(200),
    extensions_json LONGTEXT NOT NULL,
    protocol VARCHAR(20) NOT NULL DEFAULT 'SSH2',
    telnet_prompts_json LONGTEXT,
    host VARCHAR(500) NOT NULL,
    port INTEGER NOT NULL,
    username VARCHAR(500) NOT NULL,
    host_key_fingerprint VARCHAR(1000),
    status VARCHAR(40) NOT NULL,
    standard_output LONGTEXT NOT NULL,
    standard_error LONGTEXT NOT NULL,
    exit_code INTEGER,
    outcome_message LONGTEXT,
    output_truncated BOOLEAN NOT NULL,
    lease_owner VARCHAR(200),
    lease_until TIMESTAMP(6),
    parsed_facts_json LONGTEXT,
    queued_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_device_ops_collection_target_task FOREIGN KEY (task_id) REFERENCES device_ops_collection(task_id)
) ENGINE=InnoDB;

CREATE INDEX idx_device_ops_collection_target_unclaimed_recovery
    ON device_ops_collection_target(status, lease_owner, queued_at, id);
CREATE INDEX idx_device_ops_collection_target_lease_recovery
    ON device_ops_collection_target(status, lease_until, id);

CREATE TABLE device_ops_collection_attempt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_id BIGINT NOT NULL,
    attempt_no INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL,
    lease_owner VARCHAR(200),
    lease_until TIMESTAMP(6),
    started_at TIMESTAMP(6),
    finished_at TIMESTAMP(6),
    failure_reason LONGTEXT,
    CONSTRAINT uk_device_ops_attempt_number UNIQUE (target_id, attempt_no),
    CONSTRAINT fk_device_ops_attempt_target FOREIGN KEY (target_id) REFERENCES device_ops_collection_target(id)
) ENGINE=InnoDB;

CREATE TABLE device_ops_outbox (
    event_id VARCHAR(100) PRIMARY KEY,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload LONGTEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    delivered_at TIMESTAMP(6),
    last_error LONGTEXT,
    destination VARCHAR(2000),
    lease_owner VARCHAR(200),
    lease_until TIMESTAMP(6),
    dead_lettered_at TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE TABLE device_ops_collection_schedule (
    namespace VARCHAR(100) NOT NULL,
    project_key VARCHAR(200) NOT NULL,
    schedule_key VARCHAR(200) NOT NULL,
    project_hint LONGTEXT NOT NULL,
    device_key_hints LONGTEXT NOT NULL,
    script_key VARCHAR(200) NOT NULL,
    script_version VARCHAR(100) NOT NULL,
    cron VARCHAR(200) NOT NULL,
    timezone VARCHAR(100) NOT NULL,
    callback_uri VARCHAR(2000),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    next_run_at TIMESTAMP(6),
    last_run_at TIMESTAMP(6),
    last_status VARCHAR(100),
    revision BIGINT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(200),
    lease_until TIMESTAMP(6),
    PRIMARY KEY (namespace, project_key, schedule_key)
) ENGINE=InnoDB;

CREATE INDEX ix_device_ops_schedule_due
    ON device_ops_collection_schedule(enabled, next_run_at, lease_until);

CREATE TABLE device_ops_collection_output_event (
    task_id VARCHAR(80) NOT NULL,
    target_id BIGINT NOT NULL,
    sequence_no BIGINT NOT NULL,
    stream_type VARCHAR(16) NOT NULL,
    content LONGTEXT NOT NULL,
    received_bytes BIGINT NOT NULL,
    page_count INTEGER NOT NULL,
    output_truncated BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    command_index INTEGER,
    PRIMARY KEY (task_id, sequence_no),
    CONSTRAINT fk_device_ops_output_collection FOREIGN KEY (task_id) REFERENCES device_ops_collection(task_id),
    CONSTRAINT fk_device_ops_output_target FOREIGN KEY (target_id) REFERENCES device_ops_collection_target(id)
) ENGINE=InnoDB;

CREATE INDEX idx_device_ops_output_target_sequence
    ON device_ops_collection_output_event(target_id, sequence_no);
CREATE INDEX idx_device_ops_output_target_command_sequence
    ON device_ops_collection_output_event(target_id, command_index, sequence_no);

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
    credential_scope VARCHAR(32) NOT NULL DEFAULT 'USER_MANAGED',
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_device_ops_credential_name UNIQUE (owner_id, namespace, display_name),
    CONSTRAINT ck_device_ops_credential_passphrase CHECK (
        (passphrase_ciphertext IS NULL AND passphrase_nonce IS NULL)
        OR (passphrase_ciphertext IS NOT NULL AND passphrase_nonce IS NOT NULL)),
    CONSTRAINT ck_device_ops_credential_scope CHECK (
        credential_scope IN ('USER_MANAGED', 'SAVED_CONNECTION_INTERNAL'))
) ENGINE=InnoDB;

CREATE INDEX idx_device_ops_credential_namespace
    ON device_ops_credential(owner_id, namespace, updated_at);
CREATE INDEX idx_device_ops_credential_scope_namespace
    ON device_ops_credential(credential_scope, owner_id, namespace, updated_at);

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
    serial_baud_rate INTEGER,
    serial_data_bits INTEGER,
    serial_parity VARCHAR(16),
    serial_stop_bits INTEGER,
    serial_flow_control VARCHAR(16),
    serial_login_prompt VARCHAR(500),
    serial_password_prompt VARCHAR(500),
    serial_command_prompt VARCHAR(500),
    serial_line_ending VARCHAR(16),
    connect_timeout_millis BIGINT NOT NULL,
    credential_id VARCHAR(36) NOT NULL UNIQUE,
    version BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_device_ops_saved_connection_name UNIQUE (owner_id, namespace, display_name),
    CONSTRAINT fk_device_ops_saved_connection_credential
        FOREIGN KEY (credential_id) REFERENCES device_ops_credential(credential_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE INDEX idx_device_ops_saved_connection_namespace
    ON device_ops_saved_connection(owner_id, namespace, updated_at);

CREATE TABLE device_ops_collection_command_output (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_id BIGINT NOT NULL,
    command_index INTEGER NOT NULL,
    command_text LONGTEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    standard_output LONGTEXT NOT NULL,
    standard_error LONGTEXT NOT NULL,
    received_bytes BIGINT NOT NULL DEFAULT 0,
    page_count INTEGER NOT NULL DEFAULT 0,
    output_truncated BOOLEAN NOT NULL DEFAULT FALSE,
    exit_code INTEGER,
    outcome_message VARCHAR(500),
    parsed_facts_json LONGTEXT NOT NULL,
    parse_warnings_json LONGTEXT NOT NULL,
    started_at TIMESTAMP(6),
    completed_at TIMESTAMP(6),
    legacy_record BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_device_ops_command_output UNIQUE (target_id, command_index),
    CONSTRAINT fk_device_ops_command_output_target FOREIGN KEY (target_id) REFERENCES device_ops_collection_target(id),
    CONSTRAINT ck_device_ops_command_output_index CHECK (command_index > 0)
) ENGINE=InnoDB;

CREATE INDEX idx_device_ops_command_output_target
    ON device_ops_collection_command_output(target_id, command_index);
