-- 割接仅保存平台结果身份与不可变回执，设备连接和 Secret 仍由采集平台管理。
CREATE TABLE IF NOT EXISTS cut_collection_log (
    id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    entry VARCHAR(64) NOT NULL,
    object_id BIGINT NOT NULL,
    execution_id BIGINT NOT NULL,
    platform_task_id VARCHAR(128) NOT NULL,
    result_version BIGINT NOT NULL,
    file_version_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    result_snapshot JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cut_collection_result (tenant_id, platform_task_id, result_version),
    KEY idx_cut_collection_source (tenant_id, entry, object_id, execution_id),
    KEY idx_cut_collection_task (tenant_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='割接采集回传证据';
