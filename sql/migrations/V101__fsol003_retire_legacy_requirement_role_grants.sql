-- =============================================================================
-- V101: 收回旧需求分析入口的普通角色授权
--
-- 旧菜单、按钮、后端、前端、状态及业务数据全部保留；PRE-04不迁移、不双写旧事实。
-- 内置super_admin继续访问，其他角色仅逻辑撤销旧菜单树及按钮的role-menu关系。
-- =============================================================================

UPDATE `system_role_menu` rm
JOIN `system_role` r
  ON r.`id` = rm.`role_id`
 AND r.`tenant_id` = rm.`tenant_id`
 AND r.`deleted` = b'0'
JOIN `system_menu` m
  ON m.`id` = rm.`menu_id`
 AND m.`deleted` = b'0'
SET rm.`deleted` = b'1',
    rm.`updater` = 'system',
    rm.`update_time` = NOW()
WHERE rm.`deleted` = b'0'
  AND r.`code` <> 'super_admin'
  AND (
    m.`id` = 19010
    OR m.`parent_id` = 19010
    OR m.`permission` LIKE 'pms:eng-requirement:%'
  );
