-- Stages and tasks share automatic association/history; never store a stage as a task.
-- Existing task references, receiving executions and closed intervals remain unchanged.
ALTER TABLE proj_task_business_link
    MODIFY COLUMN task_id BIGINT NULL,
    ADD COLUMN stage_id BIGINT NULL COMMENT '阶段实例；与任务实例身份互斥',
    ADD UNIQUE KEY uk_stage_business_active (tenant_id, stage_id, owner_context, object_type, object_id, active_marker),
    ADD KEY idx_stage_business_execution (tenant_id, project_id, stage_id, node_execution_id),
    ADD CONSTRAINT ck_business_link_node CHECK (
        (task_id IS NOT NULL AND stage_id IS NULL) OR
        (task_id IS NULL AND stage_id IS NOT NULL));
