-- FB-02: append completion-time facts alongside existing stage/task state updates.
-- No existing node, approved plan, snapshot, or audit record is rewritten/backfilled.
CREATE TABLE proj_node_overdue_completion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    node_kind VARCHAR(8) NOT NULL,
    node_id BIGINT NOT NULL,
    node_version INT NOT NULL,
    node_name VARCHAR(255) NULL,
    plan_end_time DATETIME(3) NULL,
    acceptance_time DATETIME(3) NULL,
    completed_at DATETIME(3) NOT NULL,
    work_overdue BIT(1) NOT NULL,
    acceptance_overdue BIT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_node_completion (tenant_id, node_kind, node_id, node_version),
    KEY idx_project_completion (tenant_id, project_id, id)
) COMMENT='PROJ阶段任务完成时的不可变超期事实，仅追加，不回填旧历史';

DELIMITER $$
CREATE TRIGGER trg_proj_stage_overdue_completion
AFTER UPDATE ON proj_project_stage FOR EACH ROW
BEGIN
    IF NEW.deleted = b'0' AND NEW.status = 'DONE' AND NOT (OLD.status <=> 'DONE')
       AND NEW.actual_end_time IS NOT NULL THEN
        INSERT INTO proj_node_overdue_completion
            (tenant_id, project_id, node_kind, node_id, node_version, node_name,
             plan_end_time, acceptance_time, completed_at, work_overdue, acceptance_overdue)
        VALUES (NEW.tenant_id, NEW.project_id, 'STAGE', NEW.id, NEW.version, NEW.name,
            NEW.plan_end_time, NEW.acceptance_time, NEW.actual_end_time,
            IFNULL(NEW.plan_end_time < DATE(NEW.actual_end_time), 0),
            IFNULL(NEW.acceptance_time < DATE(NEW.actual_end_time), 0));
    END IF;
END$$

CREATE TRIGGER trg_proj_task_overdue_completion
AFTER UPDATE ON proj_project_task FOR EACH ROW
BEGIN
    IF NEW.deleted = b'0' AND NEW.status = 'DONE' AND NOT (OLD.status <=> 'DONE')
       AND NEW.actual_end_time IS NOT NULL THEN
        INSERT INTO proj_node_overdue_completion
            (tenant_id, project_id, node_kind, node_id, node_version, node_name,
             plan_end_time, acceptance_time, completed_at, work_overdue, acceptance_overdue)
        VALUES (NEW.tenant_id, NEW.project_id, 'TASK', NEW.id, NEW.version, NEW.name,
            NEW.plan_end_time, NEW.acceptance_time, NEW.actual_end_time,
            IFNULL(NEW.plan_end_time < DATE(NEW.actual_end_time), 0),
            IFNULL(NEW.acceptance_time < DATE(NEW.actual_end_time), 0));
    END IF;
END$$
DELIMITER ;
