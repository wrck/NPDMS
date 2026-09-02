-- F-ACC-002 / ACC-02、ACC-04满意度来源切片前向结构、权限与调度配置。

DROP PROCEDURE IF EXISTS `facc002_preflight_v171`;

DELIMITER $$
CREATE PROCEDURE `facc002_preflight_v171`()
BEGIN
  DECLARE target_table_count INT DEFAULT 0;
  DECLARE exact_table_shape_count INT DEFAULT 0;
  DECLARE exact_unique_index_count INT DEFAULT 0;
  DECLARE exact_check_count INT DEFAULT 0;
  DECLARE target_task_column_count INT DEFAULT 0;
  DECLARE exact_task_column_count INT DEFAULT 0;
  DECLARE involved_menu_count INT DEFAULT 0;
  DECLARE exact_menu_count INT DEFAULT 0;
  DECLARE involved_grant_count INT DEFAULT 0;
  DECLARE exact_grant_count INT DEFAULT 0;
  DECLARE involved_job_count INT DEFAULT 0;
  DECLARE exact_job_count INT DEFAULT 0;
  DECLARE managed_role_count INT DEFAULT 0;
  DECLARE parent_menu_count INT DEFAULT 0;

  SELECT COUNT(*) INTO managed_role_count FROM `system_role`
  WHERE `id`=992004800002 AND `tenant_id`=0 AND `code`='facc001_acceptance_full'
    AND `status`=0 AND `deleted`=b'0';
  SELECT COUNT(*) INTO parent_menu_count FROM `system_menu`
  WHERE `id`=19260 AND `status`=0 AND `deleted`=b'0';
  IF managed_role_count <> 1 OR parent_menu_count <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='F-ACC-002 V171 managed role or menu parent prerequisite mismatch';
  END IF;

  SELECT COUNT(*) INTO target_table_count
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (
    'acc_satisfaction_questionnaire_template','acc_satisfaction_questionnaire_template_revision',
    'acc_satisfaction_collection_task','acc_satisfaction_questionnaire','acc_satisfaction_access_grant',
    'acc_satisfaction_response','acc_satisfaction_response_file','acc_satisfaction_result',
    'acc_satisfaction_result_file','acc_satisfaction_remediation_fact','plt_export_task','plt_export_audit');

  SELECT COUNT(*) INTO exact_table_shape_count
  FROM (
    SELECT TABLE_NAME, COUNT(*) AS column_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (
      'acc_satisfaction_questionnaire_template','acc_satisfaction_questionnaire_template_revision',
      'acc_satisfaction_collection_task','acc_satisfaction_questionnaire','acc_satisfaction_access_grant',
      'acc_satisfaction_response','acc_satisfaction_response_file','acc_satisfaction_result',
      'acc_satisfaction_result_file','acc_satisfaction_remediation_fact','plt_export_task','plt_export_audit')
    GROUP BY TABLE_NAME
  ) shapes
  WHERE (TABLE_NAME='acc_satisfaction_questionnaire_template' AND column_count=12)
     OR (TABLE_NAME='acc_satisfaction_questionnaire_template_revision' AND column_count=22)
     OR (TABLE_NAME='acc_satisfaction_collection_task' AND column_count=26)
     OR (TABLE_NAME='acc_satisfaction_questionnaire' AND column_count=17)
     OR (TABLE_NAME='acc_satisfaction_access_grant' AND column_count=14)
     OR (TABLE_NAME='acc_satisfaction_response' AND column_count=12)
     OR (TABLE_NAME='acc_satisfaction_response_file' AND column_count=15)
     OR (TABLE_NAME='acc_satisfaction_result' AND column_count=29)
     OR (TABLE_NAME='acc_satisfaction_result_file' AND column_count=15)
     OR (TABLE_NAME='acc_satisfaction_remediation_fact' AND column_count=12)
     OR (TABLE_NAME='plt_export_task' AND column_count=30)
     OR (TABLE_NAME='plt_export_audit' AND column_count=10);

  SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', INDEX_NAME)) INTO exact_unique_index_count
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND NON_UNIQUE=0
    AND TABLE_NAME IN (
      'acc_satisfaction_questionnaire_template','acc_satisfaction_questionnaire_template_revision',
      'acc_satisfaction_collection_task','acc_satisfaction_questionnaire','acc_satisfaction_access_grant',
      'acc_satisfaction_response','acc_satisfaction_response_file','acc_satisfaction_result',
      'acc_satisfaction_result_file','acc_satisfaction_remediation_fact','plt_export_task','plt_export_audit')
    AND INDEX_NAME IN (
    'uk_acc_sat_template_code','uk_acc_sat_template_revision','uk_acc_sat_task_revision',
    'uk_acc_sat_task_trigger','uk_acc_sat_questionnaire_task','uk_acc_sat_grant_digest',
    'uk_acc_sat_grant_version','uk_acc_sat_response_no','uk_acc_sat_response_request',
    'uk_acc_sat_response_file_seq','uk_acc_sat_result_version','uk_acc_sat_result_current',
    'uk_acc_sat_result_file_seq','uk_acc_sat_remediation_revision','uk_acc_sat_remediation_request',
    'uk_plt_export_operation','uk_plt_export_audit_sequence');

  SELECT COUNT(*) INTO exact_check_count
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_TYPE='CHECK' AND CONSTRAINT_NAME IN (
    'chk_acc_sat_response_file_role','chk_acc_sat_result_file_role','chk_plt_export_failure',
    'chk_plt_export_file_fact','chk_plt_export_audit_action');

  SELECT COUNT(*) INTO target_task_column_count
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='proj_project_task' AND COLUMN_NAME IN (
    'acc_satisfaction_template_id','template_revision_id','template_version',
    'satisfaction_rule_version','satisfaction_threshold');
  SELECT COUNT(*) INTO exact_task_column_count
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='proj_project_task'
    AND ((COLUMN_NAME='acc_satisfaction_template_id' AND COLUMN_TYPE='bigint' AND IS_NULLABLE='YES')
      OR (COLUMN_NAME='template_revision_id' AND COLUMN_TYPE='bigint' AND IS_NULLABLE='YES')
      OR (COLUMN_NAME='template_version' AND COLUMN_TYPE='int' AND IS_NULLABLE='YES')
      OR (COLUMN_NAME='satisfaction_rule_version' AND COLUMN_TYPE='varchar(64)' AND IS_NULLABLE='YES')
      OR (COLUMN_NAME='satisfaction_threshold' AND COLUMN_TYPE='decimal(7,2)' AND IS_NULLABLE='YES'));

  SELECT COUNT(*) INTO involved_menu_count FROM `system_menu`
  WHERE `id` BETWEEN 930930 AND 930935 OR `permission` IN (
    'pms:acceptance:satisfaction:query','pms:acceptance:satisfaction:manage',
    'pms:acceptance:satisfaction:collect','pms:acceptance:satisfaction:export',
    'pms:acceptance:satisfaction:download');
  SELECT COUNT(*) INTO exact_menu_count FROM `system_menu`
  WHERE `id` BETWEEN 930930 AND 930935 AND `status`=0 AND `deleted`=b'0'
    AND ((`id`=930930 AND `parent_id`=19260 AND `permission`='')
      OR (`id`=930931 AND `parent_id`=930930 AND `permission`='pms:acceptance:satisfaction:query')
      OR (`id`=930932 AND `parent_id`=930930 AND `permission`='pms:acceptance:satisfaction:manage')
      OR (`id`=930933 AND `parent_id`=930930 AND `permission`='pms:acceptance:satisfaction:collect')
      OR (`id`=930934 AND `parent_id`=930930 AND `permission`='pms:acceptance:satisfaction:export')
      OR (`id`=930935 AND `parent_id`=930930 AND `permission`='pms:acceptance:satisfaction:download'));

  SELECT COUNT(*) INTO involved_grant_count FROM `system_role_menu`
  WHERE `role_id`=992004800002 AND `menu_id` BETWEEN 930930 AND 930935;
  SELECT COUNT(*) INTO exact_grant_count FROM `system_role_menu`
  WHERE `role_id`=992004800002 AND `menu_id` BETWEEN 930930 AND 930935
    AND `tenant_id`=0 AND `deleted`=b'0';

  SELECT COUNT(*) INTO involved_job_count FROM `infra_job`
  WHERE `id` BETWEEN 992005900001 AND 992005900005 OR `handler_name` IN (
    'satisfactionTaskOutboxDeliveryJob','satisfactionResultOutboxDeliveryJob',
    'satisfactionResultArchiveCompensationJob','exportTaskExecutionJob','exportFileExpirationJob');
  SELECT COUNT(*) INTO exact_job_count FROM `infra_job`
  WHERE `id` BETWEEN 992005900001 AND 992005900005 AND `status`=1
    AND `handler_param`='' AND `retry_count`=0 AND `deleted`=b'0'
    AND ((`id`=992005900001 AND `handler_name`='satisfactionTaskOutboxDeliveryJob' AND `cron_expression`='0/30 * * * * ?')
      OR (`id`=992005900002 AND `handler_name`='satisfactionResultOutboxDeliveryJob' AND `cron_expression`='0/30 * * * * ?')
      OR (`id`=992005900003 AND `handler_name`='satisfactionResultArchiveCompensationJob' AND `cron_expression`='0/30 * * * * ?')
      OR (`id`=992005900004 AND `handler_name`='exportTaskExecutionJob' AND `cron_expression`='0/30 * * * * ?')
      OR (`id`=992005900005 AND `handler_name`='exportFileExpirationJob' AND `cron_expression`='0 0/5 * * * ?'));

  IF target_table_count=0 AND target_task_column_count=0
     AND involved_menu_count=0 AND involved_grant_count=0 AND involved_job_count=0 THEN
    SET @facc002_v171_apply=1;
  ELSEIF target_table_count=12 AND exact_table_shape_count=12
     AND exact_unique_index_count=17 AND exact_check_count=5
     AND target_task_column_count=5 AND exact_task_column_count=5
     AND involved_menu_count=6 AND exact_menu_count=6
     AND involved_grant_count=6 AND exact_grant_count=6
     AND involved_job_count=5 AND exact_job_count=5 THEN
    SET @facc002_v171_apply=0;
  ELSE
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='F-ACC-002 V171 target structures or managed configuration are partial or conflicting';
  END IF;
END$$
DELIMITER ;

CALL `facc002_preflight_v171`();
DROP PROCEDURE IF EXISTS `facc002_preflight_v171`;

SET @facc002_v171_alter_task = IF(@facc002_v171_apply=1,
  'ALTER TABLE `proj_project_task`
   ADD COLUMN `acc_satisfaction_template_id` BIGINT NULL COMMENT ''ACC满意度模板ID'' AFTER `satisfaction_timing`,
   ADD COLUMN `template_revision_id` BIGINT NULL COMMENT ''ACC模板修订ID'' AFTER `acc_satisfaction_template_id`,
   ADD COLUMN `template_version` INT NULL COMMENT ''ACC模板版本'' AFTER `template_revision_id`,
   ADD COLUMN `satisfaction_rule_version` VARCHAR(64) NULL COMMENT ''满意度规则版本'' AFTER `template_version`,
   ADD COLUMN `satisfaction_threshold` DECIMAL(7,2) NULL COMMENT ''满意度达标阈值'' AFTER `satisfaction_rule_version`',
  'DO 0');
PREPARE facc002_v171_alter_task_stmt FROM @facc002_v171_alter_task;
EXECUTE facc002_v171_alter_task_stmt;
DEALLOCATE PREPARE facc002_v171_alter_task_stmt;

CREATE TABLE IF NOT EXISTS `acc_satisfaction_questionnaire_template` (
  `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL DEFAULT 0,
  `template_code` VARCHAR(64) NOT NULL, `name` VARCHAR(128) NOT NULL,
  `status` VARCHAR(32) NOT NULL, `current_revision_id` BIGINT NULL, `version` INT NOT NULL DEFAULT 0,
  `creator` VARCHAR(64) NULL, `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` VARCHAR(64) NULL, `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` BIT(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_acc_sat_template_code` (`tenant_id`,`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满意度问卷模板根';

CREATE TABLE IF NOT EXISTS `acc_satisfaction_questionnaire_template_revision` (
  `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL DEFAULT 0, `template_id` BIGINT NOT NULL,
  `revision_no` INT NOT NULL, `project_type` VARCHAR(64) NOT NULL,
  `signing_mode` VARCHAR(64) NOT NULL, `implementation_mode` VARCHAR(64) NOT NULL,
  `business_purpose_code` VARCHAR(64) NOT NULL, `applicable_timing_code` VARCHAR(64) NOT NULL,
  `priority` INT NOT NULL DEFAULT 0, `frozen_question_json` JSON NOT NULL,
  `frozen_threshold` DECIMAL(7,2) NOT NULL, `rule_version` VARCHAR(64) NOT NULL,
  `revision_status` VARCHAR(32) NOT NULL, `effective_from` DATETIME(3) NULL, `effective_to` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0, `creator` VARCHAR(64) NULL,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` VARCHAR(64) NULL, `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` BIT(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_acc_sat_template_revision` (`tenant_id`,`template_id`,`revision_no`),
  KEY `idx_acc_sat_template_match` (`tenant_id`,`project_type`,`signing_mode`,`implementation_mode`,`business_purpose_code`,`applicable_timing_code`,`revision_status`,`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满意度问卷模板不可变修订';

CREATE TABLE IF NOT EXISTS `acc_satisfaction_collection_task` (
  `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL DEFAULT 0, `project_id` BIGINT NOT NULL,
  `project_task_id` BIGINT NOT NULL, `source_owner_context` VARCHAR(32) NOT NULL,
  `source_object_type` VARCHAR(64) NOT NULL, `source_object_id` VARCHAR(128) NOT NULL,
  `source_object_version` BIGINT NOT NULL, `trigger_owner_context` VARCHAR(32) NOT NULL,
  `trigger_object_type` VARCHAR(64) NOT NULL, `trigger_fact_id` VARCHAR(128) NOT NULL,
  `trigger_fact_version` BIGINT NOT NULL, `collection_key` VARCHAR(128) NOT NULL,
  `task_revision_no` INT NOT NULL, `prior_task_id` BIGINT NULL, `assigned_to_user_id` BIGINT NULL,
  `assigned_by_user_id` BIGINT NULL, `task_status` VARCHAR(32) NOT NULL,
  `questionnaire_id` BIGINT NULL, `result_id` BIGINT NULL, `version` INT NOT NULL DEFAULT 0,
  `creator` VARCHAR(64) NULL, `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` VARCHAR(64) NULL, `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` BIT(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_acc_sat_task_revision` (`tenant_id`,`collection_key`,`task_revision_no`),
  UNIQUE KEY `uk_acc_sat_task_trigger` (`tenant_id`,`project_task_id`,`trigger_owner_context`,`trigger_object_type`,`trigger_fact_id`,`trigger_fact_version`),
  KEY `idx_acc_sat_task_project` (`tenant_id`,`project_id`,`task_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满意度采集任务';

CREATE TABLE IF NOT EXISTS `acc_satisfaction_questionnaire` (
  `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL DEFAULT 0, `collection_task_id` BIGINT NOT NULL,
  `template_id` BIGINT NOT NULL, `template_revision_id` BIGINT NOT NULL, `template_version` INT NOT NULL,
  `frozen_question_json` JSON NOT NULL, `frozen_threshold` DECIMAL(7,2) NOT NULL,
  `rule_version` VARCHAR(64) NOT NULL, `questionnaire_status` VARCHAR(32) NOT NULL,
  `access_scope_version` BIGINT NOT NULL, `version` INT NOT NULL DEFAULT 0,
  `creator` VARCHAR(64) NULL, `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` VARCHAR(64) NULL, `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` BIT(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_acc_sat_questionnaire_task` (`tenant_id`,`collection_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满意度冻结问卷';

CREATE TABLE IF NOT EXISTS `acc_satisfaction_access_grant` (
  `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL DEFAULT 0, `questionnaire_id` BIGINT NOT NULL,
  `grant_version` INT NOT NULL, `token_digest` CHAR(64) NOT NULL,
  `effective_from` DATETIME(3) NOT NULL, `expires_at` DATETIME(3) NOT NULL,
  `grant_status` VARCHAR(32) NOT NULL, `consumed_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0, `creator` VARCHAR(64) NULL,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` VARCHAR(64) NULL, `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_acc_sat_grant_digest` (`tenant_id`,`token_digest`),
  UNIQUE KEY `uk_acc_sat_grant_version` (`tenant_id`,`questionnaire_id`,`grant_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满意度受控访问授权';

CREATE TABLE IF NOT EXISTS `acc_satisfaction_response` (
  `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL DEFAULT 0, `questionnaire_id` BIGINT NOT NULL,
  `response_no` INT NOT NULL, `request_id` VARCHAR(128) NOT NULL, `submit_channel` VARCHAR(32) NOT NULL,
  `customer_contact_ref` VARCHAR(128) NOT NULL, `assisted_by_user_id` BIGINT NULL,
  `answer_snapshot` JSON NOT NULL, `submitted_at` DATETIME(3) NOT NULL,
  `creator` VARCHAR(64) NULL, `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_acc_sat_response_no` (`tenant_id`,`questionnaire_id`,`response_no`),
  UNIQUE KEY `uk_acc_sat_response_request` (`tenant_id`,`questionnaire_id`,`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满意度不可变答卷';

CREATE TABLE IF NOT EXISTS `acc_satisfaction_response_file` (
  `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL DEFAULT 0, `response_id` BIGINT NOT NULL,
  `file_role` VARCHAR(32) NOT NULL, `file_sequence` INT NOT NULL, `artifact_id` BIGINT NOT NULL,
  `version_no` INT NOT NULL, `reference_key` VARCHAR(128) NOT NULL, `artifact_version` INT NOT NULL,
  `reference_version` INT NOT NULL, `availability_version` INT NOT NULL, `scope_version` BIGINT NOT NULL,
  `file_hash` CHAR(64) NOT NULL, `creator` VARCHAR(64) NULL,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (`id`),
  UNIQUE KEY `uk_acc_sat_response_file_seq` (`tenant_id`,`response_id`,`file_role`,`file_sequence`),
  CONSTRAINT `chk_acc_sat_response_file_role` CHECK (`file_role` IN ('SIGNATURE','ATTACHMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满意度答卷公共文件事实';

CREATE TABLE IF NOT EXISTS `acc_satisfaction_result` (
  `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL DEFAULT 0, `collection_task_id` BIGINT NOT NULL,
  `questionnaire_id` BIGINT NOT NULL, `response_id` BIGINT NOT NULL, `collection_key` VARCHAR(128) NOT NULL,
  `result_version` INT NOT NULL, `score` DECIMAL(7,2) NOT NULL, `threshold` DECIMAL(7,2) NOT NULL,
  `passed` BIT(1) NOT NULL, `rule_version` VARCHAR(64) NOT NULL, `result_status` VARCHAR(32) NOT NULL,
  `effective_from` DATETIME(3) NOT NULL, `effective_to` DATETIME(3) NULL,
  `current_marker` TINYINT GENERATED ALWAYS AS (CASE WHEN `result_status`='EFFECTIVE' AND `passed`=b'1' AND `effective_to` IS NULL THEN 1 ELSE NULL END) STORED,
  `archive_status` VARCHAR(32) NOT NULL, `archive_actor_user_id` BIGINT NOT NULL,
  `deliverable_source_version_id` BIGINT NULL, `archive_failure_code` VARCHAR(64) NULL,
  `archive_retry_count` INT NOT NULL DEFAULT 0, `invalidated_by_user_id` BIGINT NULL,
  `invalidated_at` DATETIME(3) NULL, `invalidation_reason_code` VARCHAR(64) NULL,
  `invalidation_reason_summary` VARCHAR(500) NULL, `version` INT NOT NULL DEFAULT 0,
  `creator` VARCHAR(64) NULL, `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` VARCHAR(64) NULL, `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_acc_sat_result_version` (`tenant_id`,`collection_key`,`result_version`),
  UNIQUE KEY `uk_acc_sat_result_current` (`tenant_id`,`collection_key`,`current_marker`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满意度不可变判定结果';

CREATE TABLE IF NOT EXISTS `acc_satisfaction_result_file` (
  `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL DEFAULT 0, `result_id` BIGINT NOT NULL,
  `file_role` VARCHAR(32) NOT NULL, `file_sequence` INT NOT NULL, `artifact_id` BIGINT NOT NULL,
  `version_no` INT NOT NULL, `reference_key` VARCHAR(128) NOT NULL, `artifact_version` INT NOT NULL,
  `reference_version` INT NOT NULL, `availability_version` INT NOT NULL, `scope_version` BIGINT NOT NULL,
  `file_hash` CHAR(64) NOT NULL, `creator` VARCHAR(64) NULL,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (`id`),
  UNIQUE KEY `uk_acc_sat_result_file_seq` (`tenant_id`,`result_id`,`file_role`,`file_sequence`),
  CONSTRAINT `chk_acc_sat_result_file_role` CHECK (`file_role` IN ('RESULT_DOCUMENT','SIGNATURE','ATTACHMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满意度Result公共文件事实';

CREATE TABLE IF NOT EXISTS `acc_satisfaction_remediation_fact` (
  `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL DEFAULT 0, `prior_result_id` BIGINT NOT NULL,
  `remediation_revision_no` INT NOT NULL, `remediation_request_id` VARCHAR(128) NOT NULL,
  `evidence_summary` VARCHAR(1000) NOT NULL, `evidence_file_fact_version` VARCHAR(256) NULL,
  `completed_by` BIGINT NOT NULL, `completed_at` DATETIME(3) NOT NULL, `fact_version` BIGINT NOT NULL,
  `creator` VARCHAR(64) NULL, `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_acc_sat_remediation_revision` (`tenant_id`,`prior_result_id`,`remediation_revision_no`),
  UNIQUE KEY `uk_acc_sat_remediation_request` (`tenant_id`,`prior_result_id`,`remediation_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满意度整改不可变事实';

CREATE TABLE IF NOT EXISTS `plt_export_task` (
  `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL DEFAULT 0, `owner_context` VARCHAR(32) NOT NULL,
  `export_type` VARCHAR(64) NOT NULL, `operation_id` VARCHAR(128) NOT NULL, `request_digest` CHAR(64) NOT NULL,
  `actor_user_id` BIGINT NOT NULL, `filter_snapshot` JSON NOT NULL, `scope_snapshot` JSON NOT NULL,
  `requested_fields_snapshot` JSON NOT NULL, `include_files` BIT(1) NOT NULL,
  `scope_version` BIGINT NOT NULL, `task_status` VARCHAR(32) NOT NULL, `result_count` BIGINT NULL,
  `artifact_id` BIGINT NULL, `file_version_no` INT NULL, `reference_key` VARCHAR(128) NULL,
  `artifact_version` INT NULL, `reference_version` INT NULL, `availability_version` INT NULL,
  `file_hash` CHAR(64) NULL, `expires_at` DATETIME(3) NULL, `failure_code` VARCHAR(64) NULL,
  `failure_retryable` BIT(1) NULL, `retry_count` INT NOT NULL DEFAULT 0, `version` INT NOT NULL DEFAULT 0,
  `creator` VARCHAR(64) NULL, `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` VARCHAR(64) NULL, `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_plt_export_operation` (`tenant_id`,`owner_context`,`export_type`,`actor_user_id`,`operation_id`),
  KEY `idx_plt_export_due` (`tenant_id`,`task_status`,`create_time`),
  CONSTRAINT `chk_plt_export_failure` CHECK ((`task_status`='FAILED' AND `failure_code` IS NOT NULL AND `failure_retryable` IS NOT NULL) OR (`task_status`<>'FAILED' AND `failure_code` IS NULL AND `failure_retryable` IS NULL)),
  CONSTRAINT `chk_plt_export_file_fact` CHECK ((`artifact_id` IS NULL AND `file_version_no` IS NULL AND `reference_key` IS NULL AND `artifact_version` IS NULL AND `reference_version` IS NULL AND `availability_version` IS NULL AND `file_hash` IS NULL) OR (`artifact_id` IS NOT NULL AND `file_version_no` IS NOT NULL AND `reference_key` IS NOT NULL AND `artifact_version` IS NOT NULL AND `reference_version` IS NOT NULL AND `availability_version` IS NOT NULL AND `file_hash` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PLT统一业务导出任务';

CREATE TABLE IF NOT EXISTS `plt_export_audit` (
  `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL DEFAULT 0, `export_task_id` BIGINT NOT NULL,
  `audit_sequence` INT NOT NULL, `action_code` VARCHAR(32) NOT NULL, `actor_user_id` BIGINT NOT NULL,
  `detail_snapshot` JSON NOT NULL, `occurred_at` DATETIME(3) NOT NULL,
  `creator` VARCHAR(64) NULL, `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_plt_export_audit_sequence` (`tenant_id`,`export_task_id`,`audit_sequence`),
  CONSTRAINT `chk_plt_export_audit_action` CHECK (`action_code` IN ('REQUESTED','GENERATION_STARTED','SUCCEEDED','FAILED','REJECTED','RETRY_REQUESTED','DOWNLOADED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PLT统一业务导出永久审计';

DROP PROCEDURE IF EXISTS `facc002_apply_v171_template_candidates`;

DELIMITER $$
CREATE PROCEDURE `facc002_apply_v171_template_candidates`()
BEGIN
  DECLARE involved_root_count INT DEFAULT 0;
  DECLARE exact_root_count INT DEFAULT 0;
  DECLARE involved_revision_count INT DEFAULT 0;
  DECLARE exact_revision_count INT DEFAULT 0;

  SELECT COUNT(*) INTO involved_root_count
  FROM `acc_satisfaction_questionnaire_template`
  WHERE `id` BETWEEN 992005100001 AND 992005100004
     OR `template_code` IN ('FACC002-SEED-EXACT','FACC002-SEED-AMB-A',
                            'FACC002-SEED-AMB-B','FACC002-SEED-DISABLED');
  SELECT COUNT(*) INTO exact_root_count
  FROM `acc_satisfaction_questionnaire_template`
  WHERE `tenant_id`=0 AND `deleted`=b'0'
    AND ((`id`=992005100001 AND `template_code`='FACC002-SEED-EXACT'
          AND `name`='F-ACC-002精确候选' AND `status`='PUBLISHED'
          AND `current_revision_id`=992005110001 AND `version`=0)
      OR (`id`=992005100002 AND `template_code`='FACC002-SEED-AMB-A'
          AND `name`='F-ACC-002并列候选A' AND `status`='PUBLISHED'
          AND `current_revision_id`=992005110002 AND `version`=0)
      OR (`id`=992005100003 AND `template_code`='FACC002-SEED-AMB-B'
          AND `name`='F-ACC-002并列候选B' AND `status`='PUBLISHED'
          AND `current_revision_id`=992005110003 AND `version`=0)
      OR (`id`=992005100004 AND `template_code`='FACC002-SEED-DISABLED'
          AND `name`='F-ACC-002停用候选' AND `status`='DISABLED'
          AND `current_revision_id` IS NULL AND `version`=0));

  SELECT COUNT(*) INTO involved_revision_count
  FROM `acc_satisfaction_questionnaire_template_revision`
  WHERE `id` BETWEEN 992005110001 AND 992005110004
     OR (`template_id` BETWEEN 992005100001 AND 992005100004 AND `revision_no`=1);
  SELECT COUNT(*) INTO exact_revision_count
  FROM `acc_satisfaction_questionnaire_template_revision`
  WHERE `tenant_id`=0 AND `revision_no`=1 AND `deleted`=b'0'
    AND ((`id`=992005110001 AND `template_id`=992005100001
          AND `project_type`='FACC002_EXACT' AND `priority`=100 AND `revision_status`='PUBLISHED'
          AND `signing_mode`='STANDARD' AND `implementation_mode`='ON_SITE'
          AND `business_purpose_code`='ACCEPTANCE' AND `applicable_timing_code`='AFTER_INITIAL_ACCEPTANCE'
          AND `frozen_threshold`=80.00 AND `rule_version`='FACC002-RULE-V1' AND `version`=0)
      OR (`id`=992005110002 AND `template_id`=992005100002
          AND `project_type`='FACC002_AMBIGUOUS' AND `priority`=100 AND `revision_status`='PUBLISHED'
          AND `signing_mode`='STANDARD' AND `implementation_mode`='ON_SITE'
          AND `business_purpose_code`='ACCEPTANCE' AND `applicable_timing_code`='AFTER_INITIAL_ACCEPTANCE'
          AND `frozen_threshold`=80.00 AND `rule_version`='FACC002-RULE-V1' AND `version`=0)
      OR (`id`=992005110003 AND `template_id`=992005100003
          AND `project_type`='FACC002_AMBIGUOUS' AND `priority`=100 AND `revision_status`='PUBLISHED'
          AND `signing_mode`='STANDARD' AND `implementation_mode`='ON_SITE'
          AND `business_purpose_code`='ACCEPTANCE' AND `applicable_timing_code`='AFTER_INITIAL_ACCEPTANCE'
          AND `frozen_threshold`=80.00 AND `rule_version`='FACC002-RULE-V1' AND `version`=0)
      OR (`id`=992005110004 AND `template_id`=992005100004
          AND `project_type`='FACC002_DISABLED' AND `priority`=100 AND `revision_status`='DISABLED'
          AND `signing_mode`='STANDARD' AND `implementation_mode`='ON_SITE'
          AND `business_purpose_code`='ACCEPTANCE' AND `applicable_timing_code`='AFTER_INITIAL_ACCEPTANCE'
          AND `frozen_threshold`=80.00 AND `rule_version`='FACC002-RULE-V1' AND `version`=0));

  IF involved_root_count=0 AND involved_revision_count=0 AND @facc002_v171_apply=1 THEN
    INSERT INTO `acc_satisfaction_questionnaire_template`
      (`id`,`tenant_id`,`template_code`,`name`,`status`,`current_revision_id`,`version`,
       `creator`,`create_time`,`updater`,`update_time`,`deleted`)
    VALUES
      (992005100001,0,'FACC002-SEED-EXACT','F-ACC-002精确候选','PUBLISHED',992005110001,0,
       'facc002_v171',NOW(3),'facc002_v171',NOW(3),b'0'),
      (992005100002,0,'FACC002-SEED-AMB-A','F-ACC-002并列候选A','PUBLISHED',992005110002,0,
       'facc002_v171',NOW(3),'facc002_v171',NOW(3),b'0'),
      (992005100003,0,'FACC002-SEED-AMB-B','F-ACC-002并列候选B','PUBLISHED',992005110003,0,
       'facc002_v171',NOW(3),'facc002_v171',NOW(3),b'0'),
      (992005100004,0,'FACC002-SEED-DISABLED','F-ACC-002停用候选','DISABLED',NULL,0,
       'facc002_v171',NOW(3),'facc002_v171',NOW(3),b'0');

    INSERT INTO `acc_satisfaction_questionnaire_template_revision`
      (`id`,`tenant_id`,`template_id`,`revision_no`,`project_type`,`signing_mode`,`implementation_mode`,
       `business_purpose_code`,`applicable_timing_code`,`priority`,`frozen_question_json`,
       `frozen_threshold`,`rule_version`,`revision_status`,`effective_from`,`effective_to`,`version`,
       `creator`,`create_time`,`updater`,`update_time`,`deleted`)
    VALUES
      (992005110001,0,992005100001,1,'FACC002_EXACT','STANDARD','ON_SITE','ACCEPTANCE',
       'AFTER_INITIAL_ACCEPTANCE',100,JSON_ARRAY(JSON_OBJECT('code','Q1','required',true,'weight',100)),
       80.00,'FACC002-RULE-V1','PUBLISHED','2026-08-30 00:00:00.000',NULL,0,
       'facc002_v171',NOW(3),'facc002_v171',NOW(3),b'0'),
      (992005110002,0,992005100002,1,'FACC002_AMBIGUOUS','STANDARD','ON_SITE','ACCEPTANCE',
       'AFTER_INITIAL_ACCEPTANCE',100,JSON_ARRAY(JSON_OBJECT('code','Q1','required',true,'weight',100)),
       80.00,'FACC002-RULE-V1','PUBLISHED','2026-08-30 00:00:00.000',NULL,0,
       'facc002_v171',NOW(3),'facc002_v171',NOW(3),b'0'),
      (992005110003,0,992005100003,1,'FACC002_AMBIGUOUS','STANDARD','ON_SITE','ACCEPTANCE',
       'AFTER_INITIAL_ACCEPTANCE',100,JSON_ARRAY(JSON_OBJECT('code','Q1','required',true,'weight',100)),
       80.00,'FACC002-RULE-V1','PUBLISHED','2026-08-30 00:00:00.000',NULL,0,
       'facc002_v171',NOW(3),'facc002_v171',NOW(3),b'0'),
      (992005110004,0,992005100004,1,'FACC002_DISABLED','STANDARD','ON_SITE','ACCEPTANCE',
       'AFTER_INITIAL_ACCEPTANCE',100,JSON_ARRAY(JSON_OBJECT('code','Q1','required',true,'weight',100)),
       80.00,'FACC002-RULE-V1','DISABLED',NULL,NULL,0,
       'facc002_v171',NOW(3),'facc002_v171',NOW(3),b'0');
  ELSEIF NOT (involved_root_count=4 AND exact_root_count=4
              AND involved_revision_count=4 AND exact_revision_count=4) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='F-ACC-002 V171 managed template candidates are partial or conflicting';
  END IF;
END$$
DELIMITER ;

CALL `facc002_apply_v171_template_candidates`();
DROP PROCEDURE IF EXISTS `facc002_apply_v171_template_candidates`;

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
 `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(930930,'满意度管理','',1,20,19260,'satisfaction','ep:chat-line-square','',NULL,0,b'1',b'1',b'1','facc002_v171',NOW(),'facc002_v171',NOW(),b'0'),
(930931,'满意度查询','pms:acceptance:satisfaction:query',3,10,930930,'','',NULL,NULL,0,b'1',b'1',b'1','facc002_v171',NOW(),'facc002_v171',NOW(),b'0'),
(930932,'满意度管理','pms:acceptance:satisfaction:manage',3,20,930930,'','',NULL,NULL,0,b'1',b'1',b'1','facc002_v171',NOW(),'facc002_v171',NOW(),b'0'),
(930933,'满意度采集','pms:acceptance:satisfaction:collect',3,30,930930,'','',NULL,NULL,0,b'1',b'1',b'1','facc002_v171',NOW(),'facc002_v171',NOW(),b'0'),
(930934,'满意度导出','pms:acceptance:satisfaction:export',3,40,930930,'','',NULL,NULL,0,b'1',b'1',b'1','facc002_v171',NOW(),'facc002_v171',NOW(),b'0'),
(930935,'满意度下载','pms:acceptance:satisfaction:download',3,50,930930,'','',NULL,NULL,0,b'1',b'1',b'1','facc002_v171',NOW(),'facc002_v171',NOW(),b'0');

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 992004800002, menu_id, 'facc002_v171', NOW(), 'facc002_v171', NOW(), b'0', 0
FROM (SELECT 930930 menu_id UNION ALL SELECT 930931 UNION ALL SELECT 930932 UNION ALL
      SELECT 930933 UNION ALL SELECT 930934 UNION ALL SELECT 930935) granted
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.`tenant_id`=0 AND existing.`role_id`=992004800002
                    AND existing.`menu_id`=granted.menu_id AND existing.`deleted`=b'0');

INSERT IGNORE INTO `infra_job`
(`id`,`name`,`status`,`handler_name`,`handler_param`,`cron_expression`,`retry_count`,`retry_interval`,
 `monitor_timeout`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(992005900001,'满意度任务事件投递',1,'satisfactionTaskOutboxDeliveryJob','','0/30 * * * * ?',0,0,0,'facc002_v171',NOW(3),'facc002_v171',NOW(3),b'0'),
(992005900002,'满意度结果事件投递',1,'satisfactionResultOutboxDeliveryJob','','0/30 * * * * ?',0,0,0,'facc002_v171',NOW(3),'facc002_v171',NOW(3),b'0'),
(992005900003,'满意度归档补偿',1,'satisfactionResultArchiveCompensationJob','','0/30 * * * * ?',0,0,0,'facc002_v171',NOW(3),'facc002_v171',NOW(3),b'0'),
(992005900004,'统一导出任务执行',1,'exportTaskExecutionJob','','0/30 * * * * ?',0,0,0,'facc002_v171',NOW(3),'facc002_v171',NOW(3),b'0'),
(992005900005,'统一导出文件到期',1,'exportFileExpirationJob','','0 0/5 * * * ?',0,0,0,'facc002_v171',NOW(3),'facc002_v171',NOW(3),b'0');
