-- INT-12: offline and collected log parsing; no raw log, device secret or business state is stored here.
CREATE TABLE plt_device_log_parse (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 project_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 request_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 request_digest CHAR(64) NOT NULL,
 input_name VARCHAR(200) NOT NULL,
 source_entry VARCHAR(32) NULL,
 source_object_id BIGINT NULL,
 source_execution_id BIGINT NULL,
 source_collection_id VARCHAR(64) NULL,
 source_file_version_id BIGINT NULL,
 state VARCHAR(32) NOT NULL DEFAULT 'SUBMITTING',
 external_task_id VARCHAR(64) NULL,
 release_version VARCHAR(100) NULL,
 wait_reason VARCHAR(100) NULL,
 creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted BIT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_device_log_parse_request (tenant_id,request_key),
 KEY idx_device_log_parse_project (tenant_id,project_id,source_execution_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
