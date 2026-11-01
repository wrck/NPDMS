CREATE TABLE device_ops_desktop_task (
    namespace VARCHAR(100) NOT NULL,
    platform_task_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    owner_id VARCHAR(200) NOT NULL,
    project_id VARCHAR(200) NOT NULL,
    execution_id VARCHAR(36) NOT NULL,
    token_ciphertext VARBINARY(1024) NOT NULL,
    token_nonce VARBINARY(12) NOT NULL,
    token_key_version VARCHAR(20) NOT NULL,
    collection_id VARCHAR(128),
    result_acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_polled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (namespace, platform_task_id),
    CONSTRAINT uk_desktop_execution_id UNIQUE (execution_id)
);
