-- 仅补齐需求方批准的管理角色独立菜单。既有项目角色的query/update等权限保留，
-- 独立入口由角色展示规则隐藏，项目详情的重新倒排不受影响。
INSERT INTO system_role_menu
  (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'stage_plan_menu_v516', NOW(), 'stage_plan_menu_v516', NOW(), b'0', r.tenant_id
FROM system_role r
JOIN system_menu m ON m.deleted = b'0' AND m.status = 0
  AND (m.id = 18000 OR m.component = 'pms/engineering/stage-plan/index')
WHERE r.code IN ('admin', 'engineeManager', 'engineeManagerLeader')
  AND r.deleted = b'0' AND r.status = 0
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu existing
    WHERE existing.role_id = r.id AND existing.menu_id = m.id
      AND existing.tenant_id = r.tenant_id AND existing.deleted = b'0'
  );
