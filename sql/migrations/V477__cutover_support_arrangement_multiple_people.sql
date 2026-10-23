-- Approved 2026-10-14: selectable support roles and multiple people per role.
-- Preserve existing values; customer references below are projections of the existing plan reference.
ALTER TABLE cut_cutover_support_arrangement
  ADD COLUMN customer_contact_id BIGINT NULL COMMENT '客户联系记录基线引用',
  ADD COLUMN customer_contact_version BIGINT NULL COMMENT '客户联系记录基线版本',
  ADD COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT '方案内填写顺序',
  MODIFY COLUMN arrive_time DATETIME(3) NULL COMMENT '到位时间；草稿未填时为空';

UPDATE cut_cutover_support_arrangement arrangement
JOIN cut_plan_revision plan ON plan.id=arrangement.plan_revision_id AND plan.tenant_id=arrangement.tenant_id
SET arrangement.customer_contact_id=IF(arrangement.role_code='CUSTOMER',plan.customer_contact_id,NULL),
    arrangement.customer_contact_version=IF(arrangement.role_code='CUSTOMER',plan.customer_contact_version,NULL),
    arrangement.sort_order=FIELD(arrangement.role_code,'CUSTOMER','DP_FIRST_LINE','DP_SECOND_LINE','DP_RND')-1;

ALTER TABLE cut_cutover_support_arrangement
  DROP INDEX uk_cut_support_role,
  DROP CHECK chk_cut_support_values,
  ADD CONSTRAINT chk_cut_support_values CHECK (version >= 0
    AND CHAR_LENGTH(TRIM(person_name)) BETWEEN 0 AND 128
    AND CHAR_LENGTH(TRIM(duty_description)) BETWEEN 0 AND 1000
    AND CHAR_LENGTH(TRIM(phone)) BETWEEN 0 AND 64
    AND sort_order >= 0),
  ADD CONSTRAINT chk_cut_support_customer_reference CHECK (
    (customer_contact_id IS NULL AND customer_contact_version IS NULL)
    OR (role_code='CUSTOMER' AND customer_contact_id > 0 AND customer_contact_version >= 0
      AND customer_contact_id IS NOT NULL AND customer_contact_version IS NOT NULL)),
  ADD INDEX idx_cut_support_order (tenant_id,plan_revision_id,sort_order,id);

DELIMITER $$

CREATE TRIGGER trg_cut_support_draft_insert BEFORE INSERT ON cut_cutover_support_arrangement FOR EACH ROW
BEGIN
  IF NOT EXISTS (SELECT 1 FROM cut_plan_revision plan
    WHERE plan.id=NEW.plan_revision_id AND plan.tenant_id=NEW.tenant_id
      AND plan.status_code='DRAFT' AND plan.current_marker=1 AND plan.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CUT_PARENT_FROZEN: cut_cutover_support_arrangement';
  END IF;
END$$

CREATE TRIGGER trg_cut_support_reference_update BEFORE UPDATE ON cut_cutover_support_arrangement FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM cut_plan_revision plan
    WHERE plan.id=OLD.plan_revision_id AND plan.tenant_id=OLD.tenant_id AND plan.status_code<>'DRAFT')
    AND (NOT (NEW.customer_contact_id <=> OLD.customer_contact_id)
      OR NOT (NEW.customer_contact_version <=> OLD.customer_contact_version)
      OR NOT (NEW.sort_order <=> OLD.sort_order)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CUT_IMMUTABLE_HISTORY: cut_cutover_support_arrangement';
  END IF;
  IF (NOT (NEW.plan_revision_id <=> OLD.plan_revision_id) OR NOT (NEW.tenant_id <=> OLD.tenant_id))
    AND NOT EXISTS (SELECT 1 FROM cut_plan_revision plan
      WHERE plan.id=NEW.plan_revision_id AND plan.tenant_id=NEW.tenant_id
        AND plan.status_code='DRAFT' AND plan.current_marker=1 AND plan.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CUT_PARENT_FROZEN: cut_cutover_support_arrangement';
  END IF;
END$$

-- Drafts may be incomplete. A transition to the immutable submitted baseline must remain complete.
CREATE TRIGGER trg_cut_plan_support_submit BEFORE UPDATE ON cut_plan_revision FOR EACH ROW
BEGIN
  IF NEW.status_code='SUBMITTED' AND OLD.status_code='DRAFT'
    AND NEW.edit_mode_code='ONLINE_TEMPLATE_STANDARD' THEN
    IF (SELECT COUNT(DISTINCT role_code) FROM cut_cutover_support_arrangement
        WHERE tenant_id=NEW.tenant_id AND plan_revision_id=NEW.id AND deleted=b'0') <> 4
      OR EXISTS (SELECT 1 FROM cut_cutover_support_arrangement
        WHERE tenant_id=NEW.tenant_id AND plan_revision_id=NEW.id AND deleted=b'0'
          AND (CHAR_LENGTH(TRIM(person_name))=0 OR CHAR_LENGTH(TRIM(duty_description))=0
            OR CHAR_LENGTH(TRIM(phone))=0 OR arrive_time IS NULL)) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CUT_SUPPORT_INCOMPLETE';
    END IF;
  END IF;
END$$

DELIMITER ;
