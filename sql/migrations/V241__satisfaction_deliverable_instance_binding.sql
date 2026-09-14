-- Collection revisions retain the deliverable selected from the project task's explicit association.
-- Historical rows are not inferred from current mutable codes.
ALTER TABLE acc_satisfaction_collection_task
    ADD COLUMN deliverable_id BIGINT NULL COMMENT 'Frozen deliverable instance identity',
    ADD INDEX idx_satisfaction_task_deliverable (tenant_id, deliverable_id);
