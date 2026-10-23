-- Complete sanitized parser evidence. Existing task/file identities and history remain unchanged.
CREATE TABLE IF NOT EXISTS ast_device_log_parse_snapshot (
 id BIGINT NOT NULL PRIMARY KEY COMMENT 'Immutable archived per-device log ID',
 tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 parser_task_id VARCHAR(128) NOT NULL, parser_version VARCHAR(128),
 file_reference_id BIGINT NOT NULL, artifact_id BIGINT NOT NULL, artifact_version INT NOT NULL,
 source_sha256 VARCHAR(64) NOT NULL,
 result_json LONGTEXT NOT NULL COMMENT 'Original sanitized structured result envelope, insert only',
 creator VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updater VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 deleted BIT NOT NULL DEFAULT b'0',
 KEY idx_device_parse_snapshot_project (tenant_id,project_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
