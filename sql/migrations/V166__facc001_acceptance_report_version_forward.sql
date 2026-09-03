-- F-ACC-001 / ACC-03@V1 + ACC-04@V1(partial)
-- 初验/终验活动、不可变报告版本、应交来源索引及受管真实验收输入。

DROP PROCEDURE IF EXISTS `facc001_preflight_v166`;
DELIMITER $$
CREATE PROCEDURE `facc001_preflight_v166`()
BEGIN
  DECLARE partial_pair_count INT DEFAULT 0;
  DECLARE invalid_state_count INT DEFAULT 0;
  DECLARE invalid_contract_count INT DEFAULT 0;
  DECLARE target_id_conflict_count INT DEFAULT 0;
  DECLARE existing_permission_count INT DEFAULT 0;

  SELECT COUNT(*) INTO partial_pair_count
  FROM (
    SELECT p.`tenant_id`, p.`id`
    FROM `proj_project` p
    LEFT JOIN `proj_project_task` t ON t.`tenant_id`=p.`tenant_id` AND t.`project_id`=p.`id`
      AND t.`task_code` IN ('T-INITIAL-ACCEPT','T-FINAL-ACCEPT') AND t.`deleted`=b'0'
    LEFT JOIN `acc_project_deliverable` d ON d.`tenant_id`=p.`tenant_id` AND d.`project_id`=p.`id`
      AND d.`deliverable_code` IN ('D-INITIAL-REPORT','D-FINAL-REPORT') AND d.`deleted`=b'0'
    WHERE p.`deleted`=b'0'
    GROUP BY p.`tenant_id`,p.`id`
    HAVING (COUNT(DISTINCT t.`task_code`) + COUNT(DISTINCT d.`deliverable_code`)) NOT IN (0,4)
  ) partial_pairs;

  SELECT COUNT(*) INTO invalid_state_count
  FROM (
    SELECT p.`tenant_id`,p.`id`,
      SUM(t.`status` IN ('DONE','CLOSED')) terminal_count,
      SUM(t.`status` IN ('PENDING_ASSIGN','PENDING_START','IN_PROGRESS','PENDING_ACCEPT')) nonterminal_count
    FROM `proj_project` p
    JOIN `proj_project_task` t ON t.`tenant_id`=p.`tenant_id` AND t.`project_id`=p.`id`
      AND t.`task_code` IN ('T-INITIAL-ACCEPT','T-FINAL-ACCEPT') AND t.`deleted`=b'0'
    GROUP BY p.`tenant_id`,p.`id`
    HAVING COUNT(*)=2 AND NOT (terminal_count=2 OR nonterminal_count=2)
  ) invalid_states;

  SELECT COUNT(*) INTO invalid_contract_count
  FROM (
    SELECT t.`tenant_id`,t.`id`
    FROM `proj_project_task` t
    LEFT JOIN `proj_project_task_execution_contract` c
      ON c.`tenant_id`=t.`tenant_id` AND c.`project_task_id`=t.`id`
     AND c.`effective_to` IS NULL AND c.`deleted`=b'0'
    WHERE t.`task_code` IN ('T-INITIAL-ACCEPT','T-FINAL-ACCEPT')
      AND t.`status` IN ('PENDING_ASSIGN','PENDING_START','IN_PROGRESS','PENDING_ACCEPT')
      AND t.`deleted`=b'0'
    GROUP BY t.`tenant_id`,t.`id`
    HAVING COUNT(c.`id`)<>1 OR MAX(c.`work_binding_type_code`)<>'TASK_NATIVE'
  ) invalid_contracts;

  SELECT COUNT(*) INTO target_id_conflict_count
  FROM `proj_project_task` t
  JOIN `proj_project_task_execution_contract` c
    ON c.`tenant_id`=t.`tenant_id` AND c.`project_task_id`=t.`id`
   AND c.`effective_to` IS NULL AND c.`deleted`=b'0'
  WHERE t.`task_code` IN ('T-INITIAL-ACCEPT','T-FINAL-ACCEPT') AND t.`deleted`=b'0'
    AND (EXISTS (SELECT 1 FROM `proj_project_task_execution_contract` x WHERE x.`id`=-c.`id`)
      OR t.`id` IN (-992004500001,-992004500002)
      OR c.`id` IN (-992004400003,-992004400004));

  SELECT COUNT(*) INTO existing_permission_count
  FROM `system_menu`
  WHERE (`id`=198780 AND `permission`='pms:file:query'
      OR `id`=198781 AND `permission`='pms:file:upload'
      OR `id`=198782 AND `permission`='pms:file:download'
      OR `id`=198783 AND `permission`='pms:file:preview'
      OR `id`=198785 AND `permission`='pms:file:archive')
    AND `status`=0 AND `deleted`=b'0';

  IF partial_pair_count<>0 OR invalid_state_count<>0 OR invalid_contract_count<>0
      OR target_id_conflict_count<>0 OR existing_permission_count<>5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='F-ACC-001 V166 preflight found partial, invalid or conflicting legacy input';
  END IF;

  IF (SELECT COUNT(*) FROM `proj_project` WHERE `tenant_id`=0 AND (`id`=992004000001 OR `project_code`='FACC001-ACCEPTANCE-001'))<>0
    OR (SELECT COUNT(*) FROM `proj_project_task` WHERE `tenant_id`=0 AND `id` IN (992004100001,992004100002))<>0
    OR (SELECT COUNT(*) FROM `acc_project_deliverable` WHERE `tenant_id`=0 AND `id` IN (992004200001,992004200002))<>0
    OR (SELECT COUNT(*) FROM `proj_project_task_execution_contract` WHERE `id` IN (992004400001,992004400002,992004400003,992004400004))<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='F-ACC-001 V166 managed identities already occupied';
  END IF;
END$$
DELIMITER ;
CALL `facc001_preflight_v166`();
DROP PROCEDURE `facc001_preflight_v166`;

CREATE TABLE `acc_acceptance` (
  `id` BIGINT NOT NULL, `project_id` BIGINT NOT NULL, `project_task_id` BIGINT NOT NULL,
  `execution_contract_id` BIGINT NOT NULL, `acceptance_type` VARCHAR(16) NOT NULL,
  `activity_status` VARCHAR(16) NOT NULL DEFAULT 'PENDING', `current_report_version_id` BIGINT NULL,
  `version` INT UNSIGNED NOT NULL DEFAULT 0, `creator` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), `updater` VARCHAR(64) DEFAULT '',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` BIT(1) NOT NULL DEFAULT b'0', `tenant_id` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_acc_acceptance_project_type` (`tenant_id`,`project_id`,`acceptance_type`),
  UNIQUE KEY `uk_acc_acceptance_task` (`tenant_id`,`project_task_id`),
  CONSTRAINT `chk_acc_acceptance_type` CHECK (`acceptance_type` IN ('PRELIMINARY','FINAL')),
  CONSTRAINT `chk_acc_acceptance_status` CHECK (`activity_status` IN ('PENDING','COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='初验/终验活动根';

CREATE TABLE `acc_acceptance_report_version` (
  `id` BIGINT NOT NULL, `acceptance_id` BIGINT NOT NULL, `report_version_no` INT UNSIGNED NOT NULL,
  `report_status` VARCHAR(16) NOT NULL, `acceptance_time` DATETIME(3) NULL,
  `conclusion_code` VARCHAR(32) NULL, `conclusion_text` TEXT NULL, `acceptor_name` VARCHAR(128) NULL,
  `previous_version_id` BIGINT NULL, `effective_from` DATETIME(3) NULL, `effective_to` DATETIME(3) NULL,
  `current_marker` TINYINT GENERATED ALWAYS AS
    (CASE WHEN `report_status`='EFFECTIVE' AND `effective_to` IS NULL THEN 1 ELSE NULL END) STORED,
  `uploader_user_id` BIGINT NULL, `upload_time` DATETIME(3) NULL, `publisher_user_id` BIGINT NULL,
  `creator` VARCHAR(64) DEFAULT '', `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` VARCHAR(64) DEFAULT '', `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` BIT(1) NOT NULL DEFAULT b'0', `tenant_id` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_acc_report_version` (`tenant_id`,`acceptance_id`,`report_version_no`),
  UNIQUE KEY `uk_acc_report_current` (`tenant_id`,`acceptance_id`,`current_marker`),
  CONSTRAINT `chk_acc_report_status` CHECK (`report_status` IN ('DRAFT','EFFECTIVE','SUPERSEDED','REVOKED')),
  CONSTRAINT `chk_acc_report_dates` CHECK (`effective_to` IS NULL OR `effective_to`>=`effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变验收报告版本';

CREATE TABLE `acc_acceptance_report_attachment` (
  `id` BIGINT NOT NULL, `report_version_id` BIGINT NOT NULL, `attachment_sequence` INT UNSIGNED NOT NULL,
  `file_artifact_id` BIGINT NOT NULL, `file_version_no` INT UNSIGNED NOT NULL,
  `reference_key` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  `artifact_version` INT UNSIGNED NOT NULL, `reference_version` INT UNSIGNED NOT NULL,
  `availability_version` INT UNSIGNED NOT NULL, `scope_version` BIGINT NOT NULL,
  `file_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `creator` VARCHAR(64) DEFAULT '', `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` VARCHAR(64) DEFAULT '', `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` BIT(1) NOT NULL DEFAULT b'0', `tenant_id` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_acc_report_attachment_seq` (`tenant_id`,`report_version_id`,`attachment_sequence`),
  UNIQUE KEY `uk_acc_report_attachment_ref` (`tenant_id`,`report_version_id`,`reference_key`),
  UNIQUE KEY `uk_acc_report_attachment_file` (`tenant_id`,`report_version_id`,`file_artifact_id`,`file_version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验收报告有序附件公共事实';

ALTER TABLE `acc_project_deliverable`
  ADD COLUMN `current_source_version_id` BIGINT NULL AFTER `status`,
  ADD COLUMN `archive_status` VARCHAR(32) NULL AFTER `current_source_version_id`;

CREATE TABLE `acc_project_deliverable_source_version` (
  `id` BIGINT NOT NULL, `deliverable_id` BIGINT NOT NULL, `source_requirement_id` VARCHAR(32) NOT NULL,
  `source_object_type` VARCHAR(64) NOT NULL, `source_object_id` BIGINT NOT NULL,
  `source_version` INT UNSIGNED NOT NULL, `relation_status` VARCHAR(16) NOT NULL,
  `archive_status` VARCHAR(32) NOT NULL, `archive_failure_code` VARCHAR(64) NULL,
  `archive_retry_count` INT UNSIGNED NOT NULL DEFAULT 0, `archive_time` DATETIME(3) NULL,
  `current_marker` TINYINT GENERATED ALWAYS AS (CASE WHEN `relation_status`='CURRENT' THEN 1 ELSE NULL END) STORED,
  `creator` VARCHAR(64) DEFAULT '', `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` VARCHAR(64) DEFAULT '', `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` BIT(1) NOT NULL DEFAULT b'0', `tenant_id` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_acc_deliverable_source_version`
    (`tenant_id`,`deliverable_id`,`source_object_type`,`source_object_id`,`source_version`),
  UNIQUE KEY `uk_acc_deliverable_source_current` (`tenant_id`,`deliverable_id`,`current_marker`),
  CONSTRAINT `chk_acc_deliverable_source_relation` CHECK (`relation_status` IN ('CURRENT','SUPERSEDED','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应交件只追加来源版本';

CREATE TABLE `acc_project_deliverable_source_attachment` (
  `id` BIGINT NOT NULL, `deliverable_source_version_id` BIGINT NOT NULL,
  `attachment_sequence` INT UNSIGNED NOT NULL, `file_artifact_id` BIGINT NOT NULL,
  `file_version_no` INT UNSIGNED NOT NULL,
  `reference_key` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  `artifact_version` INT UNSIGNED NOT NULL, `reference_version` INT UNSIGNED NOT NULL,
  `availability_version` INT UNSIGNED NOT NULL, `scope_version` BIGINT NOT NULL,
  `file_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `creator` VARCHAR(64) DEFAULT '', `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` VARCHAR(64) DEFAULT '', `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` BIT(1) NOT NULL DEFAULT b'0', `tenant_id` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_acc_deliverable_source_attachment_seq`
    (`tenant_id`,`deliverable_source_version_id`,`attachment_sequence`),
  UNIQUE KEY `uk_acc_deliverable_source_attachment_ref`
    (`tenant_id`,`deliverable_source_version_id`,`reference_key`),
  UNIQUE KEY `uk_acc_deliverable_source_attachment_file`
    (`tenant_id`,`deliverable_source_version_id`,`file_artifact_id`,`file_version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应交件来源有序附件公共事实';

-- 受管正向输入：先建立完整PROJ范围、两项任务、应交根和V63 TASK_NATIVE契约。
INSERT INTO `system_role`
(`id`,`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,
 `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
VALUES
(992004800002,'FACC001验收角色','facc001_acceptance_full',940,1,'',0,2,
 '仅用于F-ACC-001正式验收配置，不定义业务角色模板',
 'facc001_seed',NOW(),'facc001_seed',NOW(),b'0',0);

INSERT INTO `system_users`
(`id`,`username`,`password`,`nickname`,`remark`,`dept_id`,`post_ids`,`email`,`mobile`,`sex`,
 `avatar`,`status`,`login_ip`,`login_date`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 992004800001,'facc001acceptance',source_user.`password`,'FACC001验收用户',
 '本地隔离验收账号；权限由FACC001正式角色配置',930851,'[]','','',0,'',0,'',NULL,
 'facc001_seed',NOW(),'facc001_seed',NOW(),b'0',0
FROM `system_users` source_user
WHERE source_user.`id`=107 AND source_user.`deleted`=b'0';

INSERT INTO `system_user_role`
(`user_id`,`role_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
VALUES (992004800001,992004800002,'facc001_seed',NOW(),'facc001_seed',NOW(),b'0',0);

INSERT INTO `proj_project`
(`id`,`project_code`,`code_root_id`,`project_sequence`,`code_rule_version`,`project_name`,
 `root_id`,`tree_path`,`tree_depth`,`tree_sort`,`manager_id`,`manager_employee_no`,`manager_name`,
 `company_id`,`company_code`,`company_name`,`department_id`,`department_code`,`department_name`,
 `project_type`,`creation_reason`,`source_type`,`status`,`version`,`creator`,`updater`,`deleted`,
 `tenant_id`,`lifecycle_status`,`current_stage`,`assignment_status`)
VALUES
(992004000001,'FACC001-ACCEPTANCE-001',992004000001,0,'V1','F-ACC-001初验终验正向夹具',
 992004000001,'/',0,0,992004800001,'FACC001-ACCEPTANCE','FACC001验收用户',
 930850,'DPTECH-DEMO','迪普科技示例公司',930851,'OFFICE-HZ-DEMO','杭州示例办事处',
 'STANDARD','F-ACC-001公开REST与报告版本验收','MIGRATION','S5',0,
 'facc001_seed','facc001_seed',b'0',0,'ACTIVE','S5','ASSIGNED');

INSERT INTO `proj_project_tree_version`
(`id`,`root_project_id`,`tree_version`,`status`,`change_batch_id`,`node_count`,`path_count`,
 `activated_at`,`version`,`creator`,`updater`,`deleted`,`tenant_id`)
VALUES
(992004300001,992004000001,1,'ACTIVE','FACC001-ACCEPTANCE-TREE-V1',1,1,NOW(3),0,
 'facc001_seed','facc001_seed',b'0',0);

INSERT INTO `proj_project_tree_path`
(`id`,`tree_version`,`root_project_id`,`ancestor_project_id`,`descendant_project_id`,`distance`,
 `version`,`creator`,`updater`,`deleted`,`tenant_id`)
VALUES
(992004300002,1,992004000001,992004000001,992004000001,0,0,
 'facc001_seed','facc001_seed',b'0',0);

INSERT INTO `proj_project_member_assignment`
(`id`,`project_id`,`user_id`,`employee_no`,`member_name`,`company_id`,`company_code`,`company_name`,
 `department_id`,`department_code`,`department_name`,`member_role`,`assignment_type`,`responsibility`,
 `effective_from`,`effective_to`,`status`,`version`,`creator`,`updater`,`deleted`,`tenant_id`)
VALUES
(992004300003,992004000001,992004800001,'FACC001-ACCEPTANCE','FACC001验收用户',
 930850,'DPTECH-DEMO','迪普科技示例公司',930851,'OFFICE-HZ-DEMO','杭州示例办事处',
 'PROJECT_MANAGER','PRIMARY','F-ACC-001正向闭环验收','2026-08-30 00:00:00.000',NULL,
 'ACTIVE',0,'facc001_seed','facc001_seed',b'0',0);

INSERT INTO `proj_project_task`
(`id`,`project_id`,`task_code`,`name`,`parent_task_code`,`parent_task_id`,`root_task_id`,`tree_depth`,
 `business_level_code`,`milestone_id`,`plan_start_time`,`plan_end_time`,`actual_start_time`,`actual_end_time`,
 `progress`,`state_machine_revision_id`,`stage_code`,`priority`,`sort_order`,`estimated_hours`,
 `satisfaction_timing`,`description`,`source_definition_id`,`status`,`version`,`creator`,`updater`,`deleted`,`tenant_id`)
SELECT seed.`id`,992004000001,seed.`task_code`,seed.`name`,NULL,NULL,seed.`id`,0,NULL,NULL,
 NULL,NULL,NULL,NULL,99,revision.`id`,'S5',2,seed.`sort_order`,NULL,NULL,
 'F-ACC-001受管验收活动任务',NULL,'PENDING_ACCEPT',0,'facc001_seed','facc001_seed',b'0',0
FROM (
 SELECT 992004100001 AS `id`,'T-INITIAL-ACCEPT' AS `task_code`,'初验活动' AS `name`,10 AS `sort_order`
 UNION ALL SELECT 992004100002,'T-FINAL-ACCEPT','终验活动',20
) seed
JOIN `proj_task_state_machine_revision` revision
  ON revision.`tenant_id`=0 AND revision.`revision_no`=1 AND revision.`status`='PUBLISHED';

INSERT INTO `proj_task_tree_path`
(`id`,`project_id`,`ancestor_task_id`,`descendant_task_id`,`distance`,`version`,
 `creator`,`updater`,`deleted`,`tenant_id`)
VALUES
(992004300004,992004000001,992004100001,992004100001,0,0,'facc001_seed','facc001_seed',b'0',0),
(992004300005,992004000001,992004100002,992004100002,0,0,'facc001_seed','facc001_seed',b'0',0);

INSERT INTO `acc_project_deliverable`
(`id`,`project_id`,`deliverable_code`,`name`,`stage_code`,`task_code`,`required`,
 `source_definition_id`,`status`,`version`,`creator`,`updater`,`deleted`,`tenant_id`)
VALUES
(992004200001,992004000001,'D-INITIAL-REPORT','初验报告','S5','T-INITIAL-ACCEPT',b'1',NULL,
 'PENDING',0,'facc001_seed','facc001_seed',b'0',0),
(992004200002,992004000001,'D-FINAL-REPORT','终验报告','S5','T-FINAL-ACCEPT',b'1',NULL,
 'PENDING',0,'facc001_seed','facc001_seed',b'0',0);

INSERT INTO `proj_project_task_execution_contract`
(`id`,`tenant_id`,`project_task_id`,`template_task_definition_id`,`work_binding_type_code`,
 `binding_parameter_snapshot`,`permission_policy_ref`,`completion_rule_type_code`,
 `completion_rule_snapshot`,`source_definition_version`,`contract_version`,`effective_from`,
 `version`,`creator`,`updater`,`deleted`)
VALUES
(992004400001,0,992004100001,NULL,'TASK_NATIVE',JSON_OBJECT('schemaVersion',1),
 'PROJECT_TASK_NATIVE_DEFAULT','TASK_NATIVE_STATUS',JSON_OBJECT('schemaVersion',1,'requiredStatus','COMPLETED'),
 1,1,NOW(3),0,'facc001_seed','facc001_seed',b'0'),
(992004400002,0,992004100002,NULL,'TASK_NATIVE',JSON_OBJECT('schemaVersion',1),
 'PROJECT_TASK_NATIVE_DEFAULT','TASK_NATIVE_STATUS',JSON_OBJECT('schemaVersion',1,'requiredStatus','COMPLETED'),
 1,1,NOW(3),0,'facc001_seed','facc001_seed',b'0');

-- 普通合格输入与受管输入使用同一成对转换集合。
DROP TEMPORARY TABLE IF EXISTS `_facc001_conversion_pair`;
CREATE TEMPORARY TABLE `_facc001_conversion_pair` (
  `tenant_id` BIGINT NOT NULL, `project_id` BIGINT NOT NULL,
  `initial_task_id` BIGINT NOT NULL, `final_task_id` BIGINT NOT NULL,
  `initial_deliverable_id` BIGINT NOT NULL, `final_deliverable_id` BIGINT NOT NULL,
  `initial_contract_id` BIGINT NOT NULL, `final_contract_id` BIGINT NOT NULL,
  PRIMARY KEY (`tenant_id`,`project_id`)
);

INSERT INTO `_facc001_conversion_pair`
SELECT initial_task.`tenant_id`,initial_task.`project_id`,initial_task.`id`,final_task.`id`,
 initial_deliverable.`id`,final_deliverable.`id`,initial_contract.`id`,final_contract.`id`
FROM `proj_project_task` initial_task
JOIN `proj_project_task` final_task
  ON final_task.`tenant_id`=initial_task.`tenant_id` AND final_task.`project_id`=initial_task.`project_id`
 AND final_task.`task_code`='T-FINAL-ACCEPT' AND final_task.`deleted`=b'0'
JOIN `acc_project_deliverable` initial_deliverable
  ON initial_deliverable.`tenant_id`=initial_task.`tenant_id`
 AND initial_deliverable.`project_id`=initial_task.`project_id`
 AND initial_deliverable.`deliverable_code`='D-INITIAL-REPORT' AND initial_deliverable.`deleted`=b'0'
JOIN `acc_project_deliverable` final_deliverable
  ON final_deliverable.`tenant_id`=initial_task.`tenant_id`
 AND final_deliverable.`project_id`=initial_task.`project_id`
 AND final_deliverable.`deliverable_code`='D-FINAL-REPORT' AND final_deliverable.`deleted`=b'0'
JOIN `proj_project_task_execution_contract` initial_contract
  ON initial_contract.`tenant_id`=initial_task.`tenant_id`
 AND initial_contract.`project_task_id`=initial_task.`id`
 AND initial_contract.`effective_to` IS NULL AND initial_contract.`deleted`=b'0'
JOIN `proj_project_task_execution_contract` final_contract
  ON final_contract.`tenant_id`=final_task.`tenant_id`
 AND final_contract.`project_task_id`=final_task.`id`
 AND final_contract.`effective_to` IS NULL AND final_contract.`deleted`=b'0'
WHERE initial_task.`task_code`='T-INITIAL-ACCEPT'
  AND initial_task.`status` IN ('PENDING_ASSIGN','PENDING_START','IN_PROGRESS','PENDING_ACCEPT')
  AND final_task.`status` IN ('PENDING_ASSIGN','PENDING_START','IN_PROGRESS','PENDING_ACCEPT')
  AND initial_task.`deleted`=b'0'
  AND initial_contract.`work_binding_type_code`='TASK_NATIVE'
  AND final_contract.`work_binding_type_code`='TASK_NATIVE';

INSERT INTO `acc_acceptance`
(`id`,`project_id`,`project_task_id`,`execution_contract_id`,`acceptance_type`,`activity_status`,
 `version`,`creator`,`updater`,`deleted`,`tenant_id`)
SELECT CASE WHEN pair.`project_id`=992004000001 THEN 992004500001 ELSE -pair.`initial_task_id` END,
 pair.`project_id`,pair.`initial_task_id`,pair.`initial_contract_id`,'PRELIMINARY','PENDING',0,
 'v166-facc001','v166-facc001',b'0',pair.`tenant_id`
FROM `_facc001_conversion_pair` pair;

INSERT INTO `acc_acceptance`
(`id`,`project_id`,`project_task_id`,`execution_contract_id`,`acceptance_type`,`activity_status`,
 `version`,`creator`,`updater`,`deleted`,`tenant_id`)
SELECT CASE WHEN pair.`project_id`=992004000001 THEN 992004500002 ELSE -pair.`final_task_id` END,
 pair.`project_id`,pair.`final_task_id`,pair.`final_contract_id`,'FINAL','PENDING',0,
 'v166-facc001','v166-facc001',b'0',pair.`tenant_id`
FROM `_facc001_conversion_pair` pair;

UPDATE `proj_project_task_execution_contract` contract
JOIN `_facc001_conversion_pair` pair
  ON contract.`tenant_id`=pair.`tenant_id`
 AND contract.`id` IN (pair.`initial_contract_id`,pair.`final_contract_id`)
SET contract.`effective_to`=NOW(3),contract.`updater`='v166-facc001';

INSERT INTO `proj_project_task_execution_contract`
(`id`,`tenant_id`,`project_task_id`,`template_task_definition_id`,`work_binding_type_code`,
 `target_context_code`,`target_object_type`,`target_object_key`,`binding_parameter_snapshot`,
 `permission_policy_ref`,`completion_rule_type_code`,`completion_rule_snapshot`,
 `source_definition_version`,`contract_version`,`effective_from`,`version`,`creator`,`updater`,`deleted`)
SELECT CASE WHEN pair.`project_id`=992004000001 THEN 992004400003 ELSE -pair.`initial_contract_id` END,
 pair.`tenant_id`,pair.`initial_task_id`,old_contract.`template_task_definition_id`,'BUSINESS_OBJECT',
 'ACC','AcceptanceActivity',CAST(CASE WHEN pair.`project_id`=992004000001 THEN 992004500001 ELSE -pair.`initial_task_id` END AS CHAR),
 JSON_OBJECT(),'ACC_ACCEPTANCE_ACTIVITY','ACC_REPORT_COMPLETE',JSON_OBJECT('requiredStatus','COMPLETED'),
 old_contract.`source_definition_version`,old_contract.`contract_version`+1,NOW(3),0,
 'v166-facc001','v166-facc001',b'0'
FROM `_facc001_conversion_pair` pair
JOIN `proj_project_task_execution_contract` old_contract ON old_contract.`id`=pair.`initial_contract_id`;

INSERT INTO `proj_project_task_execution_contract`
(`id`,`tenant_id`,`project_task_id`,`template_task_definition_id`,`work_binding_type_code`,
 `target_context_code`,`target_object_type`,`target_object_key`,`binding_parameter_snapshot`,
 `permission_policy_ref`,`completion_rule_type_code`,`completion_rule_snapshot`,
 `source_definition_version`,`contract_version`,`effective_from`,`version`,`creator`,`updater`,`deleted`)
SELECT CASE WHEN pair.`project_id`=992004000001 THEN 992004400004 ELSE -pair.`final_contract_id` END,
 pair.`tenant_id`,pair.`final_task_id`,old_contract.`template_task_definition_id`,'BUSINESS_OBJECT',
 'ACC','AcceptanceActivity',CAST(CASE WHEN pair.`project_id`=992004000001 THEN 992004500002 ELSE -pair.`final_task_id` END AS CHAR),
 JSON_OBJECT(),'ACC_ACCEPTANCE_ACTIVITY','ACC_REPORT_COMPLETE',JSON_OBJECT('requiredStatus','COMPLETED'),
 old_contract.`source_definition_version`,old_contract.`contract_version`+1,NOW(3),0,
 'v166-facc001','v166-facc001',b'0'
FROM `_facc001_conversion_pair` pair
JOIN `proj_project_task_execution_contract` old_contract ON old_contract.`id`=pair.`final_contract_id`;

-- 四个最小权限键；受管角色同时配置任务执行与文件正向闭环所需既有权限。
INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
 `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(930920,'验收报告','',2,7,19260,'acceptance-reports','ep:document-checked',
 'pms/project/acceptance-report/index','PmsAcceptanceReports',0,b'1',b'1',b'1','facc001_seed',NOW(),'facc001_seed',NOW(),b'0'),
(930921,'验收报告查询','pms:acceptance:report:query',3,10,930920,'','',NULL,NULL,0,b'1',b'1',b'1','facc001_seed',NOW(),'facc001_seed',NOW(),b'0'),
(930922,'验收报告维护','pms:acceptance:report:write',3,20,930920,'','',NULL,NULL,0,b'1',b'1',b'1','facc001_seed',NOW(),'facc001_seed',NOW(),b'0'),
(930923,'验收活动完成','pms:acceptance:report:complete',3,30,930920,'','',NULL,NULL,0,b'1',b'1',b'1','facc001_seed',NOW(),'facc001_seed',NOW(),b'0'),
(930924,'验收报告下载','pms:acceptance:report:download',3,40,930920,'','',NULL,NULL,0,b'1',b'1',b'1','facc001_seed',NOW(),'facc001_seed',NOW(),b'0');

-- V89的原菜单ID已被后续客户模块复用；新增权限载体，不覆盖客户菜单。
INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
 `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(930925,'验收任务执行','pms:project-task:execute',3,50,930920,'','',NULL,NULL,
 0,b'1',b'1',b'1','facc001_seed',NOW(),'facc001_seed',NOW(),b'0');

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 992004800002,grant_row.`menu_id`,'facc001_seed',NOW(),'facc001_seed',NOW(),b'0',0
FROM (
 SELECT 18014 AS `menu_id` UNION ALL SELECT 18030 UNION ALL SELECT 930925
 UNION ALL SELECT 198780 UNION ALL SELECT 198781 UNION ALL SELECT 198782
 UNION ALL SELECT 198783 UNION ALL SELECT 198785
 UNION ALL SELECT 930920 UNION ALL SELECT 930921 UNION ALL SELECT 930922
 UNION ALL SELECT 930923 UNION ALL SELECT 930924
) grant_row;

-- 迁移后唯一结果断言：受管输入已由统一算法转换，报告仍为空。
DROP PROCEDURE IF EXISTS `facc001_verify_v166`;
DELIMITER $$
CREATE PROCEDURE `facc001_verify_v166`()
BEGIN
  IF (SELECT COUNT(*) FROM `acc_acceptance`
      WHERE `tenant_id`=0 AND `project_id`=992004000001 AND `activity_status`='PENDING'
        AND `id` IN (992004500001,992004500002))<>2
    OR (SELECT COUNT(*) FROM `proj_project_task_execution_contract`
        WHERE `tenant_id`=0 AND `project_task_id` IN (992004100001,992004100002)
          AND `target_context_code`='ACC' AND `target_object_type`='AcceptanceActivity'
          AND `effective_to` IS NULL AND `id` IN (992004400003,992004400004))<>2
    OR (SELECT COUNT(*) FROM `proj_project_task_execution_contract`
        WHERE `id` IN (992004400001,992004400002) AND `effective_to` IS NOT NULL)<>2
    OR (SELECT COUNT(*) FROM `acc_acceptance_report_version`
        WHERE `tenant_id`=0 AND `acceptance_id` IN (992004500001,992004500002))<>0
    OR (SELECT COUNT(*) FROM `system_menu`
        WHERE `id` BETWEEN 930920 AND 930924 AND `deleted`=b'0')<>5
    OR (SELECT COUNT(*) FROM `system_role_menu`
        WHERE `tenant_id`=0 AND `role_id`=992004800002 AND `deleted`=b'0')<>13 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='F-ACC-001 V166 managed conversion or permission facts are incomplete';
  END IF;
END$$
DELIMITER ;
CALL `facc001_verify_v166`();
DROP PROCEDURE `facc001_verify_v166`;
DROP TEMPORARY TABLE `_facc001_conversion_pair`;
