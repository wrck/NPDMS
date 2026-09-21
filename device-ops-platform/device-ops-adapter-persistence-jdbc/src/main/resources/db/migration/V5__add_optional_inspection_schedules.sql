CREATE TABLE device_ops_collection_schedule (
    namespace VARCHAR(100) NOT NULL, project_key VARCHAR(200) NOT NULL, schedule_key VARCHAR(200) NOT NULL,
    project_hint CLOB NOT NULL, device_key_hints CLOB NOT NULL, script_key VARCHAR(200) NOT NULL, script_version VARCHAR(100) NOT NULL,
    cron VARCHAR(200) NOT NULL, timezone VARCHAR(100) NOT NULL, callback_uri VARCHAR(2000), enabled BOOLEAN NOT NULL DEFAULT FALSE,
    next_run_at TIMESTAMP, last_run_at TIMESTAMP, last_status VARCHAR(100), revision BIGINT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(200), lease_until TIMESTAMP,
    PRIMARY KEY (namespace, project_key, schedule_key)
);
CREATE INDEX ix_device_ops_schedule_due ON device_ops_collection_schedule(enabled, next_run_at, lease_until);
