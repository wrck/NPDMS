-- 巡检专项关系完整性与不可变证据保护。仅关联同一Owner内部业务表。
-- 来源文件、项目、设备及客户等跨Owner对象仍通过正式API校验和锁定。
ALTER TABLE srv_inspection_attempt
  ADD UNIQUE KEY uk_ins_attempt_task (tenant_id,task_id,id),
  ADD CONSTRAINT fk_ins_attempt_task FOREIGN KEY (tenant_id,task_id) REFERENCES srv_inspection_task(tenant_id,id),
  ADD CONSTRAINT ck_ins_attempt_status CHECK (status_code IN ('ACTIVE','COMPLETED','SUPERSEDED','CANCELLED','FAILED'));
ALTER TABLE srv_inspection_task_device
  ADD CONSTRAINT fk_ins_device_task FOREIGN KEY (tenant_id,task_id) REFERENCES srv_inspection_task(tenant_id,id);
ALTER TABLE srv_inspection_rule_selection
  ADD CONSTRAINT fk_ins_selection_device FOREIGN KEY (tenant_id,task_id,device_id) REFERENCES srv_inspection_task_device(tenant_id,task_id,device_id),
  ADD CONSTRAINT fk_ins_selection_revision FOREIGN KEY (tenant_id,rule_revision_id) REFERENCES srv_inspection_rule_revision(tenant_id,id);
ALTER TABLE srv_inspection_task_rule_snapshot
  ADD UNIQUE KEY uk_ins_snapshot_scope (tenant_id,task_id,attempt_id,device_id,id,rule_revision_id),
  ADD CONSTRAINT fk_ins_snapshot_attempt FOREIGN KEY (tenant_id,task_id,attempt_id) REFERENCES srv_inspection_attempt(tenant_id,task_id,id),
  ADD CONSTRAINT fk_ins_snapshot_device FOREIGN KEY (tenant_id,task_id,device_id) REFERENCES srv_inspection_task_device(tenant_id,task_id,device_id),
  ADD CONSTRAINT fk_ins_snapshot_revision FOREIGN KEY (tenant_id,rule_revision_id) REFERENCES srv_inspection_rule_revision(tenant_id,id);
ALTER TABLE srv_inspection_execution
  ADD UNIQUE KEY uk_ins_execution_scope (tenant_id,task_id,attempt_id,device_id,id),
  ADD UNIQUE KEY uk_ins_execution_task (tenant_id,task_id,id);
-- MySQL在添加自引用外键之前必须已存在被引用组合索引，不能放在同一条ALTER内。
ALTER TABLE srv_inspection_execution
  ADD CONSTRAINT fk_ins_execution_attempt FOREIGN KEY (tenant_id,task_id,attempt_id) REFERENCES srv_inspection_attempt(tenant_id,task_id,id),
  ADD CONSTRAINT fk_ins_execution_device FOREIGN KEY (tenant_id,task_id,device_id) REFERENCES srv_inspection_task_device(tenant_id,task_id,device_id),
  ADD CONSTRAINT fk_ins_execution_precheck FOREIGN KEY (tenant_id,task_id,attempt_id,device_id,precheck_execution_id)
    REFERENCES srv_inspection_execution(tenant_id,task_id,attempt_id,device_id,id),
  ADD CONSTRAINT ck_ins_execution_status CHECK (status_code IN ('PENDING','RUNNING','COLLECTED','COMPLETE','PARTIAL','PASSED','FAILED','CANCEL_REQUESTED','CANCELLED')),
  ADD CONSTRAINT ck_ins_execution_connection CHECK (protocol IS NULL OR protocol IN ('SSH','TELNET')),
  ADD CONSTRAINT ck_ins_execution_precheck_ttl CHECK (precheck_ttl_seconds IS NULL OR precheck_ttl_seconds > 0),
  ADD CONSTRAINT ck_ins_execution_result CHECK (collection_result_version IS NULL OR
    (collection_result_version > 0 AND collection_file_version_id IS NOT NULL AND collection_task_id IS NOT NULL AND collection_execution_id IS NOT NULL));
ALTER TABLE srv_inspection_collection_receipt
  ADD CONSTRAINT fk_ins_receipt_execution FOREIGN KEY (tenant_id,task_id,execution_id) REFERENCES srv_inspection_execution(tenant_id,task_id,id);
ALTER TABLE srv_inspection_parse_part
  ADD CONSTRAINT fk_ins_parse_execution FOREIGN KEY (tenant_id,task_id,execution_id) REFERENCES srv_inspection_execution(tenant_id,task_id,id);
ALTER TABLE srv_inspection_detection_result
  ADD UNIQUE KEY uk_ins_result_task (tenant_id,task_id,id),
  ADD CONSTRAINT fk_ins_result_execution FOREIGN KEY (tenant_id,task_id,attempt_id,device_id,execution_id)
    REFERENCES srv_inspection_execution(tenant_id,task_id,attempt_id,device_id,id),
  ADD CONSTRAINT fk_ins_result_snapshot FOREIGN KEY (tenant_id,task_id,attempt_id,device_id,rule_snapshot_id,rule_revision_id)
    REFERENCES srv_inspection_task_rule_snapshot(tenant_id,task_id,attempt_id,device_id,id,rule_revision_id);
ALTER TABLE srv_inspection_report_revision
  ADD UNIQUE KEY uk_ins_report_task (tenant_id,task_id,id),
  ADD CONSTRAINT fk_ins_report_attempt FOREIGN KEY (tenant_id,task_id,attempt_id) REFERENCES srv_inspection_attempt(tenant_id,task_id,id),
  ADD CONSTRAINT ck_ins_report_source CHECK (source_code IN ('PLATFORM','MANUAL')),
  ADD CONSTRAINT ck_ins_report_status CHECK (status_code IN ('GENERATING','AVAILABLE','FAILED')),
  ADD CONSTRAINT ck_ins_report_revision CHECK (revision_no > 0 AND CHAR_LENGTH(input_digest)=64);
ALTER TABLE srv_inspection_report_result
  ADD UNIQUE KEY uk_ins_report_result_task (tenant_id,task_id,report_id,result_id),
  ADD CONSTRAINT fk_ins_report_result_report FOREIGN KEY (tenant_id,task_id,report_id) REFERENCES srv_inspection_report_revision(tenant_id,task_id,id),
  ADD CONSTRAINT fk_ins_report_result_result FOREIGN KEY (tenant_id,task_id,result_id) REFERENCES srv_inspection_detection_result(tenant_id,task_id,id);
ALTER TABLE srv_inspection_report_file
  ADD CONSTRAINT fk_ins_report_file_report FOREIGN KEY (tenant_id,task_id,report_id) REFERENCES srv_inspection_report_revision(tenant_id,task_id,id),
  ADD CONSTRAINT ck_ins_report_file_format CHECK (format_code IN ('PDF','DOCX','DOC','XML')),
  ADD CONSTRAINT ck_ins_report_file_version CHECK (file_version_no > 0 AND artifact_id > 0 AND CHAR_LENGTH(sha256)=64);
ALTER TABLE srv_inspection_annotation
  ADD UNIQUE KEY uk_ins_annotation_task (tenant_id,task_id,id),
  ADD UNIQUE KEY uk_ins_annotation_result (tenant_id,task_id,report_id,result_id,id),
  ADD CONSTRAINT fk_ins_annotation_input FOREIGN KEY (tenant_id,task_id,report_id,result_id) REFERENCES srv_inspection_report_result(tenant_id,task_id,report_id,result_id),
  ADD CONSTRAINT ck_ins_annotation_revision CHECK (revision_no > 0),
  ADD CONSTRAINT ck_ins_annotation_track CHECK (kind <> 'TRACK' OR (responsible_user_id IS NOT NULL AND due_at IS NOT NULL));
ALTER TABLE srv_service_issue
  ADD UNIQUE KEY uk_ins_issue_task (tenant_id,task_id,id),
  ADD CONSTRAINT fk_ins_issue_annotation FOREIGN KEY (tenant_id,task_id,report_id,result_id,annotation_id) REFERENCES srv_inspection_annotation(tenant_id,task_id,report_id,result_id,id),
  ADD CONSTRAINT ck_ins_issue_closed CHECK (status_code <> 'CLOSED' OR (closed_by IS NOT NULL AND closed_at IS NOT NULL));
ALTER TABLE srv_service_issue_remediation
  ADD UNIQUE KEY uk_ins_remediation_task (tenant_id,task_id,id),
  ADD CONSTRAINT fk_ins_remediation_issue FOREIGN KEY (tenant_id,task_id,issue_id) REFERENCES srv_service_issue(tenant_id,task_id,id);
ALTER TABLE srv_inspection_evidence
  ADD CONSTRAINT fk_ins_evidence_remediation FOREIGN KEY (tenant_id,task_id,remediation_id) REFERENCES srv_service_issue_remediation(tenant_id,task_id,id),
  ADD CONSTRAINT ck_ins_evidence_file CHECK (artifact_id > 0 AND file_version_no > 0 AND CHAR_LENGTH(sha256)=64);
ALTER TABLE srv_inspection_feedback_confirmation
  ADD CONSTRAINT fk_ins_confirmation_annotation FOREIGN KEY (tenant_id,task_id,annotation_id) REFERENCES srv_inspection_annotation(tenant_id,task_id,id),
  ADD CONSTRAINT ck_ins_confirmation_revision CHECK (revision_no > 0);
ALTER TABLE srv_inspection_rule_feedback
  ADD CONSTRAINT fk_ins_feedback_task FOREIGN KEY (tenant_id,task_id) REFERENCES srv_inspection_task(tenant_id,id),
  ADD CONSTRAINT fk_ins_feedback_device FOREIGN KEY (tenant_id,task_id,device_id) REFERENCES srv_inspection_task_device(tenant_id,task_id,device_id);
ALTER TABLE srv_inspection_task_event
  ADD CONSTRAINT fk_ins_event_task FOREIGN KEY (tenant_id,task_id) REFERENCES srv_inspection_task(tenant_id,id);
ALTER TABLE srv_inspection_rule_optimization
  ADD CONSTRAINT fk_ins_optimization_source FOREIGN KEY (tenant_id,source_rule_revision_id) REFERENCES srv_inspection_rule_revision(tenant_id,id),
  ADD CONSTRAINT fk_ins_optimization_draft FOREIGN KEY (tenant_id,draft_rule_revision_id) REFERENCES srv_inspection_rule_revision(tenant_id,id);
ALTER TABLE srv_inspection_task
  ADD CONSTRAINT fk_ins_task_attempt FOREIGN KEY (tenant_id,id,current_attempt_id) REFERENCES srv_inspection_attempt(tenant_id,task_id,id),
  ADD CONSTRAINT fk_ins_task_report FOREIGN KEY (tenant_id,id,current_report_id) REFERENCES srv_inspection_report_revision(tenant_id,task_id,id),
  ADD CONSTRAINT ck_ins_task_archive CHECK (status_code <> 'ARCHIVED' OR (archive_requested_by IS NOT NULL AND archive_requested_at IS NOT NULL
    AND archived_by IS NOT NULL AND archived_at IS NOT NULL AND customer_archive_id IS NOT NULL AND current_report_id IS NOT NULL));
ALTER TABLE srv_inspection_feedback_export
  ADD CONSTRAINT ck_ins_export_result_count CHECK (result_count >= 0),
  ADD CONSTRAINT ck_ins_export_file CHECK (status_code <> 'AVAILABLE' OR (artifact_id IS NOT NULL AND file_version_no IS NOT NULL
    AND file_version_no > 0 AND reference_key IS NOT NULL AND reference_key <> '' AND file_name IS NOT NULL AND sha256 IS NOT NULL AND CHAR_LENGTH(sha256)=64));

DELIMITER $$
CREATE TRIGGER trg_inspection_task_device_bu BEFORE UPDATE ON srv_inspection_task_device FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_task_device'; END$$
CREATE TRIGGER trg_inspection_task_device_bd BEFORE DELETE ON srv_inspection_task_device FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_task_device'; END$$
CREATE TRIGGER trg_inspection_task_rule_snapshot_bu BEFORE UPDATE ON srv_inspection_task_rule_snapshot FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_task_rule_snapshot'; END$$
CREATE TRIGGER trg_inspection_task_rule_snapshot_bd BEFORE DELETE ON srv_inspection_task_rule_snapshot FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_task_rule_snapshot'; END$$
CREATE TRIGGER trg_inspection_collection_receipt_bu BEFORE UPDATE ON srv_inspection_collection_receipt FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_collection_receipt'; END$$
CREATE TRIGGER trg_inspection_collection_receipt_bd BEFORE DELETE ON srv_inspection_collection_receipt FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_collection_receipt'; END$$
CREATE TRIGGER trg_inspection_detection_result_bu BEFORE UPDATE ON srv_inspection_detection_result FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_detection_result'; END$$
CREATE TRIGGER trg_inspection_detection_result_bd BEFORE DELETE ON srv_inspection_detection_result FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_detection_result'; END$$
CREATE TRIGGER trg_inspection_report_result_bu BEFORE UPDATE ON srv_inspection_report_result FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_report_result'; END$$
CREATE TRIGGER trg_inspection_report_result_bd BEFORE DELETE ON srv_inspection_report_result FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_report_result'; END$$
CREATE TRIGGER trg_inspection_report_file_bu BEFORE UPDATE ON srv_inspection_report_file FOR EACH ROW
BEGIN
  IF NOT(NEW.id<=>OLD.id) OR NOT(NEW.tenant_id<=>OLD.tenant_id) OR NOT(NEW.task_id<=>OLD.task_id)
    OR NOT(NEW.report_id<=>OLD.report_id) OR NOT(NEW.format_code<=>OLD.format_code) OR NOT(NEW.artifact_id<=>OLD.artifact_id)
    OR NOT(NEW.file_version_no<=>OLD.file_version_no) OR NOT(NEW.reference_key<=>OLD.reference_key)
    OR NOT(NEW.file_name<=>OLD.file_name) OR NOT(NEW.sha256<=>OLD.sha256) OR NEW.deleted<>OLD.deleted
    OR OLD.material_id IS NOT NULL OR NEW.material_id IS NULL
    OR NOT EXISTS (SELECT 1 FROM srv_inspection_report_revision WHERE tenant_id=OLD.tenant_id AND id=OLD.report_id AND status_code='GENERATING') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_IMMUTABLE_HISTORY: srv_inspection_report_file';
  END IF;
END$$
CREATE TRIGGER trg_inspection_report_file_bd BEFORE DELETE ON srv_inspection_report_file FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_report_file'; END$$
CREATE TRIGGER trg_inspection_annotation_bu BEFORE UPDATE ON srv_inspection_annotation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_annotation'; END$$
CREATE TRIGGER trg_inspection_annotation_bd BEFORE DELETE ON srv_inspection_annotation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_annotation'; END$$
CREATE TRIGGER trg_service_issue_remediation_bu BEFORE UPDATE ON srv_service_issue_remediation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_service_issue_remediation'; END$$
CREATE TRIGGER trg_service_issue_remediation_bd BEFORE DELETE ON srv_service_issue_remediation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_service_issue_remediation'; END$$
CREATE TRIGGER trg_inspection_evidence_bu BEFORE UPDATE ON srv_inspection_evidence FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_evidence'; END$$
CREATE TRIGGER trg_inspection_evidence_bd BEFORE DELETE ON srv_inspection_evidence FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_evidence'; END$$
CREATE TRIGGER trg_inspection_feedback_confirmation_bu BEFORE UPDATE ON srv_inspection_feedback_confirmation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_feedback_confirmation'; END$$
CREATE TRIGGER trg_inspection_feedback_confirmation_bd BEFORE DELETE ON srv_inspection_feedback_confirmation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_feedback_confirmation'; END$$
CREATE TRIGGER trg_inspection_rule_feedback_bu BEFORE UPDATE ON srv_inspection_rule_feedback FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_rule_feedback'; END$$
CREATE TRIGGER trg_inspection_rule_feedback_bd BEFORE DELETE ON srv_inspection_rule_feedback FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_rule_feedback'; END$$
CREATE TRIGGER trg_inspection_task_event_bu BEFORE UPDATE ON srv_inspection_task_event FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_task_event'; END$$
CREATE TRIGGER trg_inspection_task_event_bd BEFORE DELETE ON srv_inspection_task_event FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_task_event'; END$$
CREATE TRIGGER trg_inspection_rule_optimization_bu BEFORE UPDATE ON srv_inspection_rule_optimization FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_rule_optimization'; END$$
CREATE TRIGGER trg_inspection_rule_optimization_bd BEFORE DELETE ON srv_inspection_rule_optimization FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_rule_optimization'; END$$
CREATE TRIGGER trg_inspection_feedback_export_item_bu BEFORE UPDATE ON srv_inspection_feedback_export_item FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_feedback_export_item'; END$$
CREATE TRIGGER trg_inspection_feedback_export_item_bd BEFORE DELETE ON srv_inspection_feedback_export_item FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INS_IMMUTABLE_HISTORY: srv_inspection_feedback_export_item'; END$$

CREATE TRIGGER trg_ins_report_result_bi BEFORE INSERT ON srv_inspection_report_result FOR EACH ROW
BEGIN
  IF NOT EXISTS (SELECT 1 FROM srv_inspection_report_revision p
    JOIN srv_inspection_detection_result r ON r.tenant_id=p.tenant_id AND r.task_id=p.task_id AND r.attempt_id=p.attempt_id
    WHERE p.tenant_id=NEW.tenant_id AND p.task_id=NEW.task_id AND p.id=NEW.report_id AND r.id=NEW.result_id
      AND r.outcome IN ('PASS','ABNORMAL') AND r.deleted=b'0' AND p.deleted=b'0' AND p.status_code='GENERATING') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_REPORT_INPUT_INVALID';
  END IF;
END$$
CREATE TRIGGER trg_ins_report_file_bi BEFORE INSERT ON srv_inspection_report_file FOR EACH ROW
BEGIN
  IF NOT EXISTS (SELECT 1 FROM srv_inspection_report_revision WHERE tenant_id=NEW.tenant_id AND id=NEW.report_id
    AND task_id=NEW.task_id AND status_code='GENERATING' AND deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_REPORT_FROZEN';
  END IF;
END$$
CREATE TRIGGER trg_ins_annotation_bi BEFORE INSERT ON srv_inspection_annotation FOR EACH ROW
BEGIN
  IF NOT EXISTS (SELECT 1 FROM srv_inspection_detection_result WHERE tenant_id=NEW.tenant_id AND task_id=NEW.task_id
    AND id=NEW.result_id AND outcome='ABNORMAL' AND deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_ANNOTATION_RESULT_INVALID';
  END IF;
END$$
CREATE TRIGGER trg_ins_task_bu BEFORE UPDATE ON srv_inspection_task FOR EACH ROW
BEGIN
  IF OLD.status_code='ARCHIVED' OR NEW.deleted<>OLD.deleted OR NOT(NEW.tenant_id<=>OLD.tenant_id) OR NOT(NEW.id<=>OLD.id)
    OR NOT(NEW.task_code<=>OLD.task_code) OR NOT(NEW.project_id<=>OLD.project_id) OR NOT(NEW.customer_id<=>OLD.customer_id)
    OR (OLD.status_code='CANCELLED' AND NEW.status_code<>'CANCELLED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_TASK_HISTORY_PROTECTED';
  END IF;
END$$
CREATE TRIGGER trg_ins_attempt_bu BEFORE UPDATE ON srv_inspection_attempt FOR EACH ROW
BEGIN
  IF NEW.deleted<>OLD.deleted OR NOT(NEW.tenant_id<=>OLD.tenant_id) OR NOT(NEW.id<=>OLD.id) OR NOT(NEW.task_id<=>OLD.task_id)
    OR NOT(NEW.attempt_no<=>OLD.attempt_no) OR NOT(NEW.mode<=>OLD.mode) OR NOT(NEW.reason<=>OLD.reason)
    OR NOT(NEW.started_by<=>OLD.started_by) OR NOT(NEW.started_at<=>OLD.started_at)
    OR OLD.status_code<>'ACTIVE' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_ATTEMPT_HISTORY_PROTECTED';
  END IF;
END$$
CREATE TRIGGER trg_ins_execution_bu BEFORE UPDATE ON srv_inspection_execution FOR EACH ROW
BEGIN
  IF NEW.deleted<>OLD.deleted OR NOT(NEW.tenant_id<=>OLD.tenant_id) OR NOT(NEW.id<=>OLD.id)
    OR NOT(NEW.task_id<=>OLD.task_id) OR NOT(NEW.attempt_id<=>OLD.attempt_id) OR NOT(NEW.device_id<=>OLD.device_id)
    OR NOT(NEW.purpose_code<=>OLD.purpose_code) OR NOT(NEW.submitted_by<=>OLD.submitted_by) OR NOT(NEW.submitted_at<=>OLD.submitted_at)
    OR NOT(NEW.collection_request_key<=>OLD.collection_request_key) OR NOT(NEW.protocol<=>OLD.protocol)
    OR NOT(NEW.credential_id<=>OLD.credential_id) OR NOT(NEW.precheck_execution_id<=>OLD.precheck_execution_id)
    OR NOT(NEW.precheck_ttl_seconds<=>OLD.precheck_ttl_seconds) OR NOT(NEW.rule_digest<=>OLD.rule_digest)
    OR (OLD.credential_snapshot_recorded=b'1' AND (NEW.credential_snapshot_recorded IS NULL OR NEW.credential_snapshot_recorded<>b'1'
      OR NOT(NEW.host<=>OLD.host) OR NOT(NEW.port<=>OLD.port) OR NOT(NEW.username<=>OLD.username)
      OR NOT(NEW.credential_version<=>OLD.credential_version) OR NOT(NEW.credential_grant_id<=>OLD.credential_grant_id)))
    OR (OLD.collection_execution_id IS NOT NULL AND NOT(NEW.collection_execution_id<=>OLD.collection_execution_id))
    OR (OLD.collection_task_id IS NOT NULL AND NOT(NEW.collection_task_id<=>OLD.collection_task_id))
    OR (OLD.collection_result_version IS NOT NULL AND (NOT(NEW.collection_result_version<=>OLD.collection_result_version)
      OR NOT(NEW.collection_file_version_id<=>OLD.collection_file_version_id) OR NOT(NEW.collection_status<=>OLD.collection_status)
      OR NOT(NEW.collection_failure_category<=>OLD.collection_failure_category)))
    OR (OLD.artifact_id IS NOT NULL AND (NOT(NEW.artifact_id<=>OLD.artifact_id) OR NOT(NEW.file_version_no<=>OLD.file_version_no)
      OR NOT(NEW.file_reference_key<=>OLD.file_reference_key) OR NOT(NEW.file_name<=>OLD.file_name) OR NOT(NEW.file_sha256<=>OLD.file_sha256)))
    OR (OLD.original_file_sha256 IS NOT NULL AND NOT(NEW.original_file_sha256<=>OLD.original_file_sha256))
    OR (OLD.status_code IN ('COMPLETE','PARTIAL','PASSED','FAILED','CANCELLED') AND (NOT(NEW.status_code<=>OLD.status_code)
      OR NOT(NEW.completed_at<=>OLD.completed_at) OR NOT(NEW.precheck_expires_at<=>OLD.precheck_expires_at)
      OR NOT(NEW.device_started_at<=>OLD.device_started_at) OR NOT(NEW.device_completed_at<=>OLD.device_completed_at)
      OR NOT(NEW.failure_code<=>OLD.failure_code) OR NOT(NEW.failure_message<=>OLD.failure_message))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_EXECUTION_HISTORY_PROTECTED';
  END IF;
END$$
CREATE TRIGGER trg_ins_report_bu BEFORE UPDATE ON srv_inspection_report_revision FOR EACH ROW
BEGIN
  IF NEW.deleted<>OLD.deleted OR NOT(NEW.tenant_id<=>OLD.tenant_id) OR NOT(NEW.id<=>OLD.id)
    OR NOT(NEW.task_id<=>OLD.task_id) OR NOT(NEW.attempt_id<=>OLD.attempt_id) OR NOT(NEW.revision_no<=>OLD.revision_no)
    OR NOT(NEW.source_code<=>OLD.source_code) OR NOT(NEW.input_digest<=>OLD.input_digest)
    OR NOT(NEW.template_version<=>OLD.template_version) OR NOT(NEW.explanation<=>OLD.explanation)
    OR NOT(NEW.generated_by<=>OLD.generated_by) OR NOT(NEW.generated_at<=>OLD.generated_at) OR OLD.status_code<>'GENERATING' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_REPORT_HISTORY_PROTECTED';
  END IF;
END$$
CREATE TRIGGER trg_ins_issue_bu BEFORE UPDATE ON srv_service_issue FOR EACH ROW
BEGIN
  IF NEW.deleted<>OLD.deleted OR NOT(NEW.tenant_id<=>OLD.tenant_id) OR NOT(NEW.id<=>OLD.id) OR NOT(NEW.task_id<=>OLD.task_id)
    OR NOT(NEW.report_id<=>OLD.report_id) OR NOT(NEW.result_id<=>OLD.result_id) OR OLD.status_code='CLOSED' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_ISSUE_HISTORY_PROTECTED';
  END IF;
END$$
CREATE TRIGGER trg_ins_parse_part_bu BEFORE UPDATE ON srv_inspection_parse_part FOR EACH ROW
BEGIN
  IF NEW.deleted<>OLD.deleted OR NOT(NEW.tenant_id<=>OLD.tenant_id) OR NOT(NEW.id<=>OLD.id) OR NOT(NEW.task_id<=>OLD.task_id)
    OR NOT(NEW.execution_id<=>OLD.execution_id) OR NOT(NEW.part_no<=>OLD.part_no) OR NOT(NEW.first_command_index<=>OLD.first_command_index)
    OR NOT(NEW.last_command_index<=>OLD.last_command_index) OR NOT(NEW.request_id<=>OLD.request_id)
    OR OLD.status_code IN ('SUCCEEDED','FAILED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_PARSE_HISTORY_PROTECTED';
  END IF;
END$$
CREATE TRIGGER trg_ins_export_bu BEFORE UPDATE ON srv_inspection_feedback_export FOR EACH ROW
BEGIN
  IF NEW.deleted<>OLD.deleted OR NOT(NEW.tenant_id<=>OLD.tenant_id) OR NOT(NEW.id<=>OLD.id) OR OLD.status_code='AVAILABLE'
    OR NOT(NEW.exported_by<=>OLD.exported_by) OR NOT(NEW.format_code<=>OLD.format_code) OR NOT(NEW.data_as_of<=>OLD.data_as_of)
    OR NOT(NEW.rule_revision_id<=>OLD.rule_revision_id) OR NOT(NEW.detection_id<=>OLD.detection_id) OR NOT(NEW.category_code<=>OLD.category_code)
    OR NOT(NEW.severity_code<=>OLD.severity_code) OR NOT(NEW.product_model<=>OLD.product_model) OR NOT(NEW.confirmation<=>OLD.confirmation)
    OR NOT(NEW.from_at<=>OLD.from_at) OR NOT(NEW.to_at<=>OLD.to_at) OR NOT(NEW.current_only<=>OLD.current_only)
    OR NOT(NEW.include_details<=>OLD.include_details) OR NOT(NEW.result_count<=>OLD.result_count) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_EXPORT_HISTORY_PROTECTED';
  END IF;
END$$
CREATE TRIGGER trg_inspection_task_bd BEFORE DELETE ON srv_inspection_task FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_HISTORY_DELETE_FORBIDDEN'; END$$
CREATE TRIGGER trg_inspection_attempt_bd BEFORE DELETE ON srv_inspection_attempt FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_HISTORY_DELETE_FORBIDDEN'; END$$
CREATE TRIGGER trg_inspection_execution_bd BEFORE DELETE ON srv_inspection_execution FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_HISTORY_DELETE_FORBIDDEN'; END$$
CREATE TRIGGER trg_inspection_report_revision_bd BEFORE DELETE ON srv_inspection_report_revision FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_HISTORY_DELETE_FORBIDDEN'; END$$
CREATE TRIGGER trg_service_issue_bd BEFORE DELETE ON srv_service_issue FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_HISTORY_DELETE_FORBIDDEN'; END$$
CREATE TRIGGER trg_inspection_parse_part_bd BEFORE DELETE ON srv_inspection_parse_part FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_HISTORY_DELETE_FORBIDDEN'; END$$
CREATE TRIGGER trg_inspection_feedback_export_bd BEFORE DELETE ON srv_inspection_feedback_export FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_HISTORY_DELETE_FORBIDDEN'; END$$
DELIMITER ;
