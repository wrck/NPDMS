-- TF-032: use the existing engineering-management role; never assign named users.
-- This seed adds only the shared approval inbox and its query/decision permissions.
-- SOL's pending-review source/material endpoints accept major-review directly;
-- ordinary project/SOL lists, file administration and generic BPM management are not granted.
-- Tenant 1 is the explicitly reviewed target. Existing role memberships, menus and data scope are retained.
INSERT INTO system_role_menu (role_id, menu_id, creator, updater, tenant_id)
SELECT r.id, m.id, 'seed_tf032_engineering_role', 'seed_tf032_engineering_role', r.tenant_id
FROM system_role r
CROSS JOIN (
    SELECT 1185 AS menu_id, '' AS permission
    UNION ALL SELECT 1200, ''
    UNION ALL SELECT 1201, ''
    UNION ALL SELECT 1202, 'bpm:process-instance:query'
    UNION ALL SELECT 1207, ''
    UNION ALL SELECT 1221, 'bpm:task:query'
    UNION ALL SELECT 1222, 'bpm:task:update'
    UNION ALL SELECT 992209220341, 'pms:sol-solution:major-review'
) required_menu
JOIN system_menu m ON m.id = required_menu.menu_id
    AND m.permission = required_menu.permission AND m.status = 0 AND m.deleted = b'0'
WHERE r.tenant_id = 1 AND r.code = 'engineeManager' AND r.status = 0 AND r.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu existing
      WHERE existing.tenant_id = r.tenant_id AND existing.role_id = r.id
        AND existing.menu_id = m.id AND existing.deleted = b'0'
  );
