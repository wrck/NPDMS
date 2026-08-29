-- F-ACC-001 / ACC-03：纠正V128活动与当前ACC执行契约的身份关系。
-- V128、V129保持不可变；本迁移不改变活动、任务、报告或契约的状态与版本。

DROP PROCEDURE IF EXISTS `facc001_apply_v130_activity_contract_identity_fix`;

DELIMITER $$
CREATE PROCEDURE `facc001_apply_v130_activity_contract_identity_fix`()
BEGIN
  DECLARE target_activity_count INT DEFAULT 0;
  DECLARE mapping_row_count INT DEFAULT 0;
  DECLARE mapping_activity_count INT DEFAULT 0;
  DECLARE allowed_input_count INT DEFAULT 0;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;

  DROP TEMPORARY TABLE IF EXISTS `_facc001_v130_contract_mapping`;
  CREATE TEMPORARY TABLE `_facc001_v130_contract_mapping` (
    `activity_id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `project_task_id` BIGINT NOT NULL,
    `existing_contract_id` BIGINT NOT NULL,
    `current_contract_id` BIGINT NOT NULL
  );

  SELECT COUNT(*) INTO target_activity_count
  FROM `acc_acceptance`
  WHERE `creator` = 'v128-facc001'
    AND `deleted` = b'0';

  IF target_activity_count = 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-001 V130 V128 activity input is missing';
  END IF;

  INSERT INTO `_facc001_v130_contract_mapping`
  (`activity_id`,`tenant_id`,`project_task_id`,`existing_contract_id`,`current_contract_id`)
  SELECT activity.`id`,activity.`tenant_id`,activity.`project_task_id`,
         activity.`execution_contract_id`,current_contract.`id`
  FROM `acc_acceptance` activity
  JOIN `proj_project_task` task
    ON task.`tenant_id` = activity.`tenant_id`
   AND task.`id` = activity.`project_task_id`
   AND task.`project_id` = activity.`project_id`
   AND task.`deleted` = b'0'
  JOIN `proj_project_task_execution_contract` current_contract
    ON current_contract.`tenant_id` = activity.`tenant_id`
   AND current_contract.`project_task_id` = activity.`project_task_id`
   AND current_contract.`target_context_code` = 'ACC'
   AND current_contract.`target_object_type` = 'AcceptanceActivity'
   AND CAST(current_contract.`target_object_key` AS BINARY) = CAST(activity.`id` AS BINARY)
   AND current_contract.`effective_to` IS NULL
   AND current_contract.`deleted` = b'0'
  WHERE activity.`creator` = 'v128-facc001'
    AND activity.`deleted` = b'0';

  SELECT COUNT(*),COUNT(DISTINCT `activity_id`)
    INTO mapping_row_count,mapping_activity_count
  FROM `_facc001_v130_contract_mapping`;

  IF mapping_row_count <> target_activity_count
      OR mapping_activity_count <> target_activity_count THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-001 V130 current ACC contract mapping is missing or duplicated';
  END IF;

  SELECT COUNT(*) INTO allowed_input_count
  FROM `_facc001_v130_contract_mapping` mapping
  LEFT JOIN `proj_project_task_execution_contract` old_contract
    ON old_contract.`id` = mapping.`existing_contract_id`
   AND old_contract.`tenant_id` = mapping.`tenant_id`
   AND old_contract.`project_task_id` = mapping.`project_task_id`
   AND old_contract.`work_binding_type_code` = 'TASK_NATIVE'
   AND old_contract.`effective_to` IS NOT NULL
   AND old_contract.`deleted` = b'0'
  WHERE mapping.`existing_contract_id` = mapping.`current_contract_id`
     OR old_contract.`id` IS NOT NULL;

  IF allowed_input_count <> target_activity_count THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-001 V130 activity contract input is partial or conflicting';
  END IF;

  UPDATE `acc_acceptance` activity
  JOIN `_facc001_v130_contract_mapping` mapping
    ON mapping.`tenant_id` = activity.`tenant_id`
   AND mapping.`activity_id` = activity.`id`
  SET activity.`execution_contract_id` = mapping.`current_contract_id`,
      activity.`updater` = 'facc001_v130',
      activity.`update_time` = NOW(3)
  WHERE mapping.`existing_contract_id` <> mapping.`current_contract_id`;

  IF (SELECT COUNT(*)
      FROM `acc_acceptance` activity
      JOIN `_facc001_v130_contract_mapping` mapping
        ON mapping.`tenant_id` = activity.`tenant_id`
       AND mapping.`activity_id` = activity.`id`
       AND mapping.`current_contract_id` = activity.`execution_contract_id`
      WHERE activity.`creator` = 'v128-facc001'
        AND activity.`deleted` = b'0') <> target_activity_count THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-001 V130 activity contract identity verification failed';
  END IF;

  DROP TEMPORARY TABLE `_facc001_v130_contract_mapping`;
  COMMIT;
END$$
DELIMITER ;

CALL `facc001_apply_v130_activity_contract_identity_fix`();
DROP PROCEDURE IF EXISTS `facc001_apply_v130_activity_contract_identity_fix`;
