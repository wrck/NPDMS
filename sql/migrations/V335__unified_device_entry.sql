-- 2026-09-21 用户批准：设备档案与设备工作台合并，同一登录用户使用同一数据权限。
-- 保留旧菜单作为隐藏兼容路由；已有档案角色获得同权限的统一入口，不增加操作权限。
INSERT INTO system_role_menu (role_id, menu_id, creator, updater, tenant_id)
SELECT DISTINCT old.role_id, canonical.id, 'device-entry-merge', 'device-entry-merge', old.tenant_id
FROM system_role_menu old
JOIN system_menu legacy ON legacy.id = old.menu_id AND legacy.component = 'pms/asset/device/archive/index' AND legacy.deleted = b'0'
JOIN system_menu canonical ON canonical.component = 'pms/asset/device/index' AND canonical.deleted = b'0'
WHERE old.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu existing
      WHERE existing.role_id = old.role_id AND existing.menu_id = canonical.id
        AND existing.tenant_id = old.tenant_id AND existing.deleted = b'0');

UPDATE system_menu SET visible = b'0', updater = 'device-entry-merge', update_time = NOW()
WHERE component = 'pms/asset/device/archive/index' AND deleted = b'0';

UPDATE system_menu SET name = '设备管理', updater = 'device-entry-merge', update_time = NOW()
WHERE component = 'pms/asset/device/index' AND deleted = b'0';

UPDATE system_menu child
JOIN system_menu legacy ON child.parent_id = legacy.id AND legacy.component = 'pms/asset/device/archive/index'
JOIN system_menu canonical ON canonical.component = 'pms/asset/device/index' AND canonical.deleted = b'0'
SET child.parent_id = canonical.id, child.updater = 'device-entry-merge', child.update_time = NOW()
WHERE child.type = 3 AND child.deleted = b'0';
