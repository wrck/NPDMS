-- Project managers can open the existing BPM process detail from implementation solutions.
-- Read APIs retain tenant, participation and current project-scope checks; no approval/admin grant.
INSERT INTO system_role_menu (role_id, menu_id, creator, updater, tenant_id)
WITH RECURSIVE process_read_menus AS (
    SELECT id, parent_id FROM system_menu
    WHERE deleted = b'0' AND permission IN ('bpm:process-instance:query', 'bpm:task:query')
    UNION DISTINCT
    SELECT m.id, m.parent_id FROM system_menu m JOIN process_read_menus c ON c.parent_id = m.id
    WHERE m.deleted = b'0'
)
SELECT r.id, m.id, 'solution_process_read_20261021', 'solution_process_read_20261021', r.tenant_id
FROM system_role r CROSS JOIN process_read_menus m
WHERE r.tenant_id = 1 AND r.code = 'PROJECT_MANAGER' AND r.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id
    AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');
