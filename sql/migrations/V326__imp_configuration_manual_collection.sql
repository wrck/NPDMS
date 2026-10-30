-- EXE-03 / FR-ENG-023 / INT-12: user-authorized manual commands, no template publication prerequisite.
-- Immutable execution links; no password or command text is stored here.
CREATE TABLE imp_configuration_collection (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 configuration_id BIGINT NOT NULL,
 equipment_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 request_key VARCHAR(64) NOT NULL,
 request_digest CHAR(64) NOT NULL,
 platform_task_id VARCHAR(64) NOT NULL,
 consumed_result_version BIGINT NULL,
 creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted BIT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_imp_configuration_collection_request (tenant_id, request_key),
 UNIQUE KEY uk_imp_configuration_collection_task (tenant_id, platform_task_id),
 KEY idx_imp_configuration_collection_history (tenant_id, configuration_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Uses existing configuration query/update and file download permissions; no new role or menu.
