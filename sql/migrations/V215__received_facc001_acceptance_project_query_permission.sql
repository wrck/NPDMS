-- Chronologically received from the ACC/INT/CUT source branches.
-- Original path: sql/migrations/V132__facc001_acceptance_project_query_permission.sql
-- Active Flyway version reassigned after master@220486237b9570ab3d2b0663df39c89be2a5ec69.

-- F-ACC-001 / ACC-03：允许受管正式验收身份加载项目选择器。
-- 复用V57现役项目列表菜单18067；不新增权限键、菜单、角色或业务角色模板。

DROP PROCEDURE IF EXISTS `facc001_apply_v132_project_query_permission`;

DELIMITER $$
CREATE PROCEDURE `facc001_apply_v132_project_query_permission`()
BEGIN
  DECLARE managed_role_count INT DEFAULT 0;
  DECLARE managed_menu_count INT DEFAULT 0;
  DECLARE exact_grant_count INT DEFAULT 0;
  DECLARE involved_grant_count INT DEFAULT 0;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;

  SELECT COUNT(*) INTO managed_role_count
  FROM `system_role`
  WHERE `id` = 992004800002
    AND `tenant_id` = 0
    AND `code` = 'facc001_acceptance_full'
    AND `status` = 0
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO managed_menu_count
  FROM `system_menu`
  WHERE `id` = 18067
    AND `permission` = 'pms:project:query'
    AND `parent_id` = 19261
    AND `status` = 0
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO exact_grant_count
  FROM `system_role_menu`
  WHERE `role_id` = 992004800002
    AND `menu_id` = 18067
    AND `tenant_id` = 0
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO involved_grant_count
  FROM `system_role_menu`
  WHERE `role_id` = 992004800002
    AND `menu_id` = 18067;

  IF managed_role_count <> 1 OR managed_menu_count <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-001 V132 managed role or project query menu prerequisite mismatch';
  END IF;

  IF involved_grant_count = 0 THEN
    INSERT INTO `system_role_menu`
    (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
    VALUES (992004800002, 18067, 'facc001_v132', NOW(), 'facc001_v132', NOW(), b'0', 0);
  ELSEIF NOT (involved_grant_count = 1 AND exact_grant_count = 1) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-001 V132 project query grant is partial or conflicting';
  END IF;

  IF (SELECT COUNT(*) FROM `system_role_menu`
      WHERE `role_id` = 992004800002
        AND `menu_id` = 18067
        AND `tenant_id` = 0
        AND `deleted` = b'0') <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-001 V132 project query grant verification failed';
  END IF;

  COMMIT;
END$$
DELIMITER ;

CALL `facc001_apply_v132_project_query_permission`();
DROP PROCEDURE IF EXISTS `facc001_apply_v132_project_query_permission`;
