-- F-PROJ-007 / F-ACC-002：恢复被V108菜单ID冲突覆盖的项目任务指派权限载体。
-- 只新增既有权限键的按钮菜单，不修改历史菜单，不在迁移中授予任何角色。

DROP PROCEDURE IF EXISTS `facc002_apply_v172_project_task_assign_permission`;

DELIMITER $$
CREATE PROCEDURE `facc002_apply_v172_project_task_assign_permission`()
BEGIN
  DECLARE customer_menu_count INT DEFAULT 0;
  DECLARE task_menu_count INT DEFAULT 0;
  DECLARE target_id_count INT DEFAULT 0;
  DECLARE active_assign_count INT DEFAULT 0;
  DECLARE customer_role_relations_before INT DEFAULT 0;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;

  SELECT COUNT(*) INTO customer_menu_count
  FROM `system_menu`
  WHERE `id` = 198761
    AND `permission` = 'pms:customer:query'
    AND `type` = 3
    AND `parent_id` = 198760
    AND `status` = 0
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO task_menu_count
  FROM `system_menu`
  WHERE `id` = 18014
    AND `permission` = 'pms:project-task:query'
    AND `type` = 2
    AND `status` = 0
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO target_id_count
  FROM `system_menu`
  WHERE `id` = 930926;

  SELECT COUNT(*) INTO active_assign_count
  FROM `system_menu`
  WHERE `permission` = 'pms:project-task:assign'
    AND `status` = 0
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO customer_role_relations_before
  FROM `system_role_menu`
  WHERE `menu_id` = 198761;

  IF customer_menu_count <> 1 OR task_menu_count <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-002 V172 customer or project task menu prerequisite mismatch';
  END IF;

  IF target_id_count <> 0 OR active_assign_count <> 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-002 V172 project task assign menu identity conflict';
  END IF;

  INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
   `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
  VALUES
  (930926, '项目任务指派', 'pms:project-task:assign', 3, 50, 18014, '', '',
   NULL, NULL, 0, b'1', b'1', b'1', 'facc002_v172', NOW(), 'facc002_v172', NOW(), b'0');

  IF (SELECT COUNT(*) FROM `system_menu`
      WHERE `id` = 930926
        AND `name` = '项目任务指派'
        AND `permission` = 'pms:project-task:assign'
        AND `type` = 3
        AND `parent_id` = 18014
        AND `status` = 0
        AND `deleted` = b'0') <> 1
    OR (SELECT COUNT(*) FROM `system_menu`
        WHERE `permission` = 'pms:project-task:assign'
          AND `status` = 0
          AND `deleted` = b'0') <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-002 V172 project task assign menu verification failed';
  END IF;

  IF (SELECT COUNT(*) FROM `system_menu`
      WHERE `id` = 198761
        AND `permission` = 'pms:customer:query'
        AND `parent_id` = 198760
        AND `status` = 0
        AND `deleted` = b'0') <> 1
    OR (SELECT COUNT(*) FROM `system_role_menu` WHERE `menu_id` = 198761)
       <> customer_role_relations_before THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-002 V172 customer menu history changed unexpectedly';
  END IF;

  COMMIT;
END$$
DELIMITER ;

CALL `facc002_apply_v172_project_task_assign_permission`();
DROP PROCEDURE IF EXISTS `facc002_apply_v172_project_task_assign_permission`;
