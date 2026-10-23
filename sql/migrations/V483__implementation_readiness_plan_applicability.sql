-- 仅新增结构化适用性引用；历史快照为空并保留原四项前置。
ALTER TABLE imp_cutover_readiness_snapshot
    ADD COLUMN project_plan_version_id BIGINT NULL COMMENT 'PROJ显式适用性的冻结计划ID',
    ADD COLUMN project_plan_version BIGINT NULL COMMENT 'PROJ冻结计划版本';

ALTER TABLE imp_cutover_readiness_snapshot
    ADD CONSTRAINT chk_imp_readiness_project_plan CHECK (
        (project_plan_version_id IS NULL AND project_plan_version IS NULL)
        OR (project_plan_version_id IS NOT NULL AND project_plan_version IS NOT NULL
            AND project_plan_version_id > 0 AND project_plan_version >= 0));

-- 与原不可变快照触发器并存，只补新字段，保留原保护。
DELIMITER $$
CREATE TRIGGER trg_imp_readiness_plan_immutable
BEFORE UPDATE ON imp_cutover_readiness_snapshot FOR EACH ROW
BEGIN
    IF NOT (NEW.project_plan_version_id <=> OLD.project_plan_version_id)
       OR NOT (NEW.project_plan_version <=> OLD.project_plan_version) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IMP readiness frozen plan is immutable';
    END IF;
END$$
DELIMITER ;
