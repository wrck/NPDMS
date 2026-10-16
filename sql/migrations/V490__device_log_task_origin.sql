-- New uploads retain their explicit task origin. Immutable existing archives are not backfilled.
ALTER TABLE ast_device_log_import ADD COLUMN origin_task_id BIGINT NULL;
ALTER TABLE ast_device_log_file ADD COLUMN origin_task_id BIGINT NULL;
