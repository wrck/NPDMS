-- EXE-03 / INT-12: user-authorized display of the exact manual commands dispatched.
-- Nullable for immutable historical executions; no guessed command text or credential backfill.
ALTER TABLE imp_configuration_collection
    ADD COLUMN command_text MEDIUMTEXT NULL COMMENT '下发命令快照，不含连接凭证' AFTER platform_task_id;
