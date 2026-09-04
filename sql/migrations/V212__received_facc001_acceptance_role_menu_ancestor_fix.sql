-- Chronologically received from the ACC/INT/CUT source branches.
-- Original path: sql/migrations/V129__facc001_acceptance_role_menu_ancestor_fix.sql
-- Active Flyway version reassigned after master@220486237b9570ab3d2b0663df39c89be2a5ec69.

-- F-ACC-001 / ACC-03：补齐受管验收角色的菜单祖先授权闭包。
-- V128保持不可变；本迁移不新增权限键、菜单、角色或业务角色模板。

DROP PROCEDURE IF EXISTS `facc001_apply_v129_role_menu_ancestor_fix`;

DELIMITER $$
CREATE PROCEDURE `facc001_apply_v129_role_menu_ancestor_fix`()
BEGIN
  DECLARE managed_role_count INT DEFAULT 0;
  DECLARE managed_ancestor_menu_count INT DEFAULT 0;
  DECLARE managed_ancestor_grant_count INT DEFAULT 0;
  DECLARE managed_ancestor_grant_row_count INT DEFAULT 0;

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

  SELECT COUNT(*) INTO managed_ancestor_menu_count
  FROM `system_menu`
  WHERE (`id` = 19260 AND `parent_id` = 0
      OR `id` = 19266 AND `parent_id` = 19261
      OR `id` = 19261 AND `parent_id` = 18000
      OR `id` = 18000 AND `parent_id` = 0
      OR `id` = 1243 AND `parent_id` = 2
      OR `id` = 2 AND `parent_id` = 0)
    AND `status` = 0
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO managed_ancestor_grant_count
  FROM `system_role_menu`
  WHERE `role_id` = 992004800002
    AND `menu_id` IN (19260, 19266, 19261, 18000, 1243, 2)
    AND `tenant_id` = 0
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO managed_ancestor_grant_row_count
  FROM `system_role_menu`
  WHERE `role_id` = 992004800002
    AND `menu_id` IN (19260, 19266, 19261, 18000, 1243, 2);

  IF managed_role_count <> 1 OR managed_ancestor_menu_count <> 6 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-001 V129 managed role or menu ancestor prerequisite mismatch';
  END IF;

  IF managed_ancestor_grant_count = 0 AND managed_ancestor_grant_row_count = 0 THEN
    INSERT INTO `system_role_menu`
    (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
    SELECT 992004800002, grant_row.`menu_id`, 'facc001_v129', NOW(),
           'facc001_v129', NOW(), b'0', 0
    FROM (
      SELECT 19260 AS `menu_id` UNION ALL SELECT 19266 UNION ALL SELECT 19261
      UNION ALL SELECT 18000 UNION ALL SELECT 1243 UNION ALL SELECT 2
    ) grant_row;
  ELSEIF NOT (managed_ancestor_grant_count = 6 AND managed_ancestor_grant_row_count = 6) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-001 V129 managed role menu ancestor state is partial or conflicting';
  END IF;

  IF (SELECT COUNT(*) FROM `system_role_menu`
      WHERE `role_id` = 992004800002
        AND `menu_id` IN (19260, 19266, 19261, 18000, 1243, 2)
        AND `tenant_id` = 0
        AND `deleted` = b'0') <> 6 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-001 V129 managed role menu ancestor verification failed';
  END IF;

  COMMIT;
END$$
DELIMITER ;

CALL `facc001_apply_v129_role_menu_ancestor_fix`();
DROP PROCEDURE IF EXISTS `facc001_apply_v129_role_menu_ancestor_fix`;
