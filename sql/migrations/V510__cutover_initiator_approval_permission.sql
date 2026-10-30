-- P5 starts with an explicit five-item review by the submitting project manager.
-- Grant the existing action only; CUT still checks tenant, active account,
-- fixed current assignee, current node, state and version for every decision.
-- Do not grant BPM generic approval, reassignment or any project data scope.
INSERT INTO system_role_menu
(role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT role.id, menu.id, 'cut_initiator_20261021', 'cut_initiator_20261021', b'0', role.tenant_id
FROM system_role role
JOIN system_menu menu ON menu.permission = 'pms:cutover-task:approve'
    AND menu.status = 0 AND menu.deleted = b'0'
WHERE role.tenant_id = 1 AND role.code = 'PROJECT_MANAGER'
  AND role.status = 0 AND role.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu present
      WHERE present.tenant_id = role.tenant_id AND present.role_id = role.id
        AND present.menu_id = menu.id AND present.deleted = b'0'
  );
