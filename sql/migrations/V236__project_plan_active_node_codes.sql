-- Current node codes remain unique; archived instances keep their original codes and history.
-- Replacing/removing an unstarted node must not reserve its display code forever.
-- Native InnoDB virtual-column indexes: https://dev.mysql.com/doc/refman/8.4/en/create-table-secondary-indexes.html
ALTER TABLE proj_project_stage
    ADD COLUMN active_stage_code VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN stage_code ELSE NULL END) VIRTUAL,
    DROP INDEX uk_proj_stage,
    ADD UNIQUE KEY uk_proj_stage (tenant_id,project_id,active_stage_code);
ALTER TABLE proj_project_task
    ADD COLUMN active_task_code VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN task_code ELSE NULL END) VIRTUAL,
    DROP INDEX uk_proj_task,
    ADD UNIQUE KEY uk_proj_task (tenant_id,project_id,active_task_code);
ALTER TABLE proj_project_milestone
    ADD COLUMN active_milestone_code VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN milestone_code ELSE NULL END) VIRTUAL,
    DROP INDEX uk_proj_milestone,
    ADD UNIQUE KEY uk_proj_milestone (tenant_id,project_id,active_milestone_code);
ALTER TABLE proj_project_gate
    ADD COLUMN active_gate_code VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN gate_code ELSE NULL END) VIRTUAL,
    DROP INDEX uk_proj_gate,
    ADD UNIQUE KEY uk_proj_gate (tenant_id,project_id,active_gate_code);
ALTER TABLE acc_project_deliverable
    ADD COLUMN active_deliverable_code VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN deliverable_code ELSE NULL END) VIRTUAL,
    DROP INDEX uk_acc_project_deliverable,
    ADD UNIQUE KEY uk_acc_project_deliverable (tenant_id,project_id,active_deliverable_code);
