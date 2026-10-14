-- Tenant-wide CUT role approvals may read the files referenced by their frozen approval source.
-- PLT still delegates object scope to the CUT policy; no upload or reference-write grant is added.
INSERT INTO system_role_menu
(role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT role.id, menu.id, 'cut-role-20261013', NOW(), 'cut-role-20261013', NOW(), b'0', role.tenant_id
FROM system_role role
JOIN system_menu menu ON menu.permission IN ('pms:file:download', 'pms:file:preview')
WHERE role.tenant_id = 1
  AND role.code IN ('probSupporter', 'probRD')
  AND role.status = 0 AND role.deleted = b'0'
  AND menu.status = 0 AND menu.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu present
      WHERE present.tenant_id = role.tenant_id AND present.role_id = role.id
        AND present.menu_id = menu.id AND present.deleted = b'0'
  );
