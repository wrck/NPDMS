-- Parsed facts are immutable. Current network version remains a projection owned by AST.
CREATE TABLE IF NOT EXISTS ast_device_log_parsed_fact (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL, device_id BIGINT, request_key VARCHAR(180) NOT NULL,
 log_key VARCHAR(180) NOT NULL, log_received_at DATETIME(3) NOT NULL,
 parser_task_id VARCHAR(128) NOT NULL, parser_version VARCHAR(128), source_file_id BIGINT, log_version BIGINT,
 serial_number VARCHAR(128), conp_version VARCHAR(255), boot_version VARCHAR(255),
 cpld_version VARCHAR(255), pcb_version VARCHAR(255), status VARCHAR(40) NOT NULL, version_status VARCHAR(40) NOT NULL,
 creator VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updater VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), deleted BIT NOT NULL DEFAULT b'0',
 UNIQUE KEY uk_device_log_parse_request(tenant_id,request_key),
 KEY idx_device_log_parse_history(tenant_id,device_id,log_received_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One technical parser job per immutable per-device archive file. No log body is duplicated here.
CREATE TABLE IF NOT EXISTS ast_device_log_parse_job (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
 log_received_at DATETIME(3) NOT NULL, state VARCHAR(32) NOT NULL, external_task_id VARCHAR(128), parser_version VARCHAR(128), projected BIT NOT NULL DEFAULT b'0',
 creator VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updater VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), deleted BIT NOT NULL DEFAULT b'0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
