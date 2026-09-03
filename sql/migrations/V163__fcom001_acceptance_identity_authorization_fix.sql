-- F-COM-001 / COM-01：修正受管验收身份的公开登录名并补齐现役项目更新授权。
-- V161、V162保持不可变；两项纠偏必须在同一事务中全有或全无。

DROP PROCEDURE IF EXISTS `fcom001_apply_v163_acceptance_identity_fix`;

DELIMITER $$
CREATE PROCEDURE `fcom001_apply_v163_acceptance_identity_fix`()
BEGIN
  DECLARE managed_user_count INT DEFAULT 0;
  DECLARE old_username_count INT DEFAULT 0;
  DECLARE new_username_count INT DEFAULT 0;
  DECLARE conflicting_new_username_count INT DEFAULT 0;
  DECLARE managed_role_count INT DEFAULT 0;
  DECLARE managed_user_role_count INT DEFAULT 0;
  DECLARE project_update_menu_count INT DEFAULT 0;
  DECLARE project_update_grant_count INT DEFAULT 0;
  DECLARE project_update_grant_row_count INT DEFAULT 0;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;

  SELECT COUNT(*) INTO managed_user_count
  FROM `system_users`
  WHERE `id` = 992002800002
    AND `tenant_id` = 0
    AND `creator` = 'fcom001_seed'
    AND `nickname` = 'FCOM001全权限验收'
    AND `remark` = '本地隔离验收账号；权限通过FCOM001专用正式角色配置'
    AND `dept_id` = 930851
    AND `status` = 0
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO old_username_count
  FROM `system_users`
  WHERE `id` = 992002800002
    AND `tenant_id` = 0
    AND `username` = 'fcom001_acceptance'
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO new_username_count
  FROM `system_users`
  WHERE `id` = 992002800002
    AND `tenant_id` = 0
    AND `username` = 'fcom001acceptance'
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO conflicting_new_username_count
  FROM `system_users`
  WHERE `tenant_id` = 0
    AND `id` <> 992002800002
    AND `username` = 'fcom001acceptance'
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO managed_role_count
  FROM `system_role`
  WHERE `id` = 992002800001
    AND `tenant_id` = 0
    AND `code` = 'fcom001_acceptance_full'
    AND `status` = 0
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO managed_user_role_count
  FROM `system_user_role`
  WHERE `user_id` = 992002800002
    AND `role_id` = 992002800001
    AND `tenant_id` = 0
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO project_update_menu_count
  FROM `system_menu`
  WHERE `id` = 18069
    AND `parent_id` = 18067
    AND `permission` = 'pms:project:update'
    AND `status` = 0
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO project_update_grant_count
  FROM `system_role_menu`
  WHERE `role_id` = 992002800001
    AND `menu_id` = 18069
    AND `tenant_id` = 0
    AND `deleted` = b'0';

  SELECT COUNT(*) INTO project_update_grant_row_count
  FROM `system_role_menu`
  WHERE `role_id` = 992002800001
    AND `menu_id` = 18069;

  IF managed_user_count <> 1
      OR conflicting_new_username_count <> 0
      OR managed_role_count <> 1
      OR managed_user_role_count <> 1
      OR project_update_menu_count <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-COM-001 V163 managed acceptance identity prerequisite mismatch';
  END IF;

  IF old_username_count = 1 AND new_username_count = 0
      AND project_update_grant_count = 0 AND project_update_grant_row_count = 0 THEN
    UPDATE `system_users`
    SET `username` = 'fcom001acceptance',
        `updater` = 'fcom001_v163',
        `update_time` = NOW()
    WHERE `id` = 992002800002
      AND `tenant_id` = 0
      AND `username` = 'fcom001_acceptance'
      AND `deleted` = b'0';

    IF ROW_COUNT() <> 1 THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'F-COM-001 V163 managed username update failed';
    END IF;

    INSERT INTO `system_role_menu`
    (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
    VALUES
    (992002800001, 18069, 'fcom001_v163', NOW(), 'fcom001_v163', NOW(), b'0', 0);
  ELSEIF NOT (old_username_count = 0 AND new_username_count = 1
      AND project_update_grant_count = 1 AND project_update_grant_row_count = 1) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-COM-001 V163 managed acceptance identity is partial or conflicting';
  END IF;

  IF (SELECT COUNT(*) FROM `system_users`
      WHERE `id` = 992002800002 AND `tenant_id` = 0
        AND `username` = 'fcom001acceptance' AND `creator` = 'fcom001_seed'
        AND `nickname` = 'FCOM001全权限验收' AND `dept_id` = 930851
        AND `status` = 0 AND `deleted` = b'0') <> 1
      OR (SELECT COUNT(*) FROM `system_users`
          WHERE `tenant_id` = 0 AND `username` = 'fcom001_acceptance'
            AND `deleted` = b'0') <> 0
      OR (SELECT COUNT(*) FROM `system_role_menu`
          WHERE `role_id` = 992002800001 AND `menu_id` = 18069
            AND `tenant_id` = 0 AND `deleted` = b'0') <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-COM-001 V163 managed acceptance identity verification failed';
  END IF;

  COMMIT;
END$$
DELIMITER ;

CALL `fcom001_apply_v163_acceptance_identity_fix`();
DROP PROCEDURE IF EXISTS `fcom001_apply_v163_acceptance_identity_fix`;
