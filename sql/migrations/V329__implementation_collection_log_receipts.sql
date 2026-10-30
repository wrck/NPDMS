-- INT-12 / EXE-03 / EXE-04: business-owned, immutable collection log receipts.
-- Existing pending collection Outbox events backfill valid source-bound logs through the receiver.
CREATE TABLE imp_collection_log (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 entry VARCHAR(32) NOT NULL, object_id BIGINT NOT NULL, project_id BIGINT NOT NULL, device_id BIGINT NOT NULL,
 execution_id BIGINT NOT NULL, actor_id BIGINT NOT NULL, platform_task_id VARCHAR(64) NOT NULL,
 result_version BIGINT NOT NULL, file_version_id BIGINT NOT NULL,
 protocol VARCHAR(16) NOT NULL, external_status VARCHAR(32) NOT NULL, failure_category VARCHAR(128) NULL,
 command_text MEDIUMTEXT NULL, template_name VARCHAR(128) NULL, received_at DATETIME NOT NULL,
 creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted BIT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_imp_collection_log_result (tenant_id,platform_task_id,result_version),
 KEY idx_imp_collection_log_source (tenant_id,entry,object_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
