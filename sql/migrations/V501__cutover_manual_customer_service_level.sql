-- Approved 2026-10-19: current CUS maintenance record or explicit CUT assessment selection.
-- Nullable additions preserve all existing historical facts without backfill.
ALTER TABLE cut_task
    ADD COLUMN service_level_source_record_id BIGINT NULL COMMENT 'CUS maintenance record ID, not revision ID';
ALTER TABLE cut_assessment
    ADD COLUMN service_level_source_record_id BIGINT NULL COMMENT 'CUS maintenance record ID, not revision ID',
    ADD COLUMN manual_customer_service_level_code VARCHAR(64) NULL COMMENT 'Explicit selection only when CUS is not configured',
    ADD COLUMN manual_customer_service_level_selected_by BIGINT NULL,
    ADD COLUMN manual_customer_service_level_selected_at DATETIME(3) NULL;

DELIMITER $$
CREATE TRIGGER trg_cut_task_level_source_u BEFORE UPDATE ON cut_task FOR EACH ROW
BEGIN
    IF NOT (NEW.service_level_source_record_id <=> OLD.service_level_source_record_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: task customer level source';
    END IF;
END$$
CREATE TRIGGER trg_cut_assessment_level_source_u BEFORE UPDATE ON cut_assessment FOR EACH ROW
BEGIN
    IF OLD.assessment_status_code <> 'DRAFT' AND (
        NOT (NEW.service_level_source_record_id <=> OLD.service_level_source_record_id)
        OR NOT (CAST(NEW.manual_customer_service_level_code AS BINARY) <=> CAST(OLD.manual_customer_service_level_code AS BINARY))
        OR NOT (NEW.manual_customer_service_level_selected_by <=> OLD.manual_customer_service_level_selected_by)
        OR NOT (NEW.manual_customer_service_level_selected_at <=> OLD.manual_customer_service_level_selected_at)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: assessment customer level selection';
    END IF;
END$$
DELIMITER ;
