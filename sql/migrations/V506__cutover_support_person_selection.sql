-- Approved 2026-10-20: optional second line/R&D; explicit member/user selection.
-- Nullable columns preserve every existing submitted baseline and contact audit unchanged.
ALTER TABLE cut_cutover_support_arrangement
  ADD COLUMN person_source_code VARCHAR(32) NULL COMMENT '选择来源 PROJECT_MEMBER/SYSTEM_USER',
  ADD COLUMN person_source_id BIGINT NULL COMMENT '来源成员区间或系统用户ID',
  ADD COLUMN person_source_version BIGINT NULL COMMENT '来源版本',
  ADD CONSTRAINT chk_cut_support_person_source CHECK (
    (person_source_code IS NULL AND person_source_id IS NULL AND person_source_version IS NULL)
    OR (person_source_code IS NOT NULL AND person_source_id IS NOT NULL AND person_source_version IS NOT NULL AND person_source_id>0 AND person_source_version>=0
      AND ((role_code='CUSTOMER' AND person_source_code='PROJECT_MEMBER')
        OR (role_code<>'CUSTOMER' AND person_source_code='SYSTEM_USER')) AND customer_contact_id IS NULL));

ALTER TABLE cut_plan_support_contact_change
  ADD COLUMN before_person_source_code VARCHAR(32) NULL,
  ADD COLUMN before_person_source_id BIGINT NULL,
  ADD COLUMN before_person_source_version BIGINT NULL,
  ADD COLUMN after_person_source_code VARCHAR(32) NULL,
  ADD COLUMN after_person_source_id BIGINT NULL,
  ADD COLUMN after_person_source_version BIGINT NULL;

DROP TRIGGER IF EXISTS trg_cut_plan_support_submit;
DELIMITER $$
CREATE TRIGGER trg_cut_plan_support_submit BEFORE UPDATE ON cut_plan_revision FOR EACH ROW
BEGIN
  IF NEW.status_code='SUBMITTED' AND OLD.status_code='DRAFT' AND NEW.edit_mode_code='ONLINE_TEMPLATE_STANDARD' THEN
    IF (SELECT COUNT(DISTINCT role_code) FROM cut_cutover_support_arrangement
        WHERE tenant_id=NEW.tenant_id AND plan_revision_id=NEW.id AND deleted=b'0'
          AND role_code IN ('CUSTOMER','DP_FIRST_LINE')) <> 2
      OR EXISTS (SELECT 1 FROM cut_cutover_support_arrangement
        WHERE tenant_id=NEW.tenant_id AND plan_revision_id=NEW.id AND deleted=b'0'
          AND (CHAR_LENGTH(TRIM(person_name))=0 OR CHAR_LENGTH(TRIM(duty_description))=0
            OR CHAR_LENGTH(TRIM(phone))=0 OR arrive_time IS NULL
            OR (role_code<>'CUSTOMER' AND (person_source_code IS NULL OR person_source_code<>'SYSTEM_USER' OR person_source_id IS NULL)))) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CUT_SUPPORT_INCOMPLETE';
    END IF;
  END IF;
END$$
CREATE TRIGGER trg_cut_support_person_history BEFORE UPDATE ON cut_cutover_support_arrangement FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM cut_plan_revision p WHERE p.id=OLD.plan_revision_id AND p.tenant_id=OLD.tenant_id AND p.status_code<>'DRAFT')
    AND (NOT (NEW.person_source_code <=> OLD.person_source_code) OR NOT (NEW.person_source_id <=> OLD.person_source_id)
      OR NOT (NEW.person_source_version <=> OLD.person_source_version)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CUT_IMMUTABLE_HISTORY: support person source';
  END IF;
END$$
DELIMITER ;
