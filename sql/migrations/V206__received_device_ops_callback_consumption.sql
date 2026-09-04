-- Chronologically received from the ACC/INT/CUT source branches.
-- Original path: sql/migrations/V106__device_ops_callback_consumption.sql
-- Active Flyway version reassigned after master@220486237b9570ab3d2b0663df39c89be2a5ec69.

ALTER TABLE plt_collection_task
    ADD COLUMN last_callback_sequence BIGINT NULL AFTER consumed_result_version;

ALTER TABLE plt_collection_callback_record
    ADD COLUMN started_at DATETIME(3) NULL AFTER processing_result,
    ADD COLUMN completed_at DATETIME(3) NULL AFTER started_at,
    ADD COLUMN trace_id VARCHAR(128) NULL AFTER completed_at,
    DROP INDEX uk_plt_callback,
    ADD UNIQUE KEY uk_plt_callback_id (tenant_id, callback_id),
    ADD UNIQUE KEY uk_plt_callback_sequence (tenant_id, platform_task_id, sequence_no);

CREATE TABLE IF NOT EXISTS plt_collection_result_consumption (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    platform_task_id VARCHAR(64) NOT NULL,
    consumer_context VARCHAR(32) NOT NULL,
    consumer_object_type VARCHAR(64) NOT NULL,
    consumer_object_id VARCHAR(64) NOT NULL,
    result_version BIGINT NOT NULL,
    consumption_result VARCHAR(32) NOT NULL,
    consumed_at DATETIME(3) NOT NULL,
    trace_id VARCHAR(128) NULL,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plt_collection_consumption (
        tenant_id,
        platform_task_id,
        consumer_context,
        consumer_object_type,
        consumer_object_id,
        result_version
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
