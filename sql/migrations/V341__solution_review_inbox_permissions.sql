-- The designated reviewer needs the existing BPM inbox and its protected read/approve APIs.
-- Project visibility is still determined by current project scope; this grants no membership.
INSERT INTO system_role_menu (role_id, menu_id, creator, updater, tenant_id)
WITH RECURSIVE review_menus AS (
    SELECT id, parent_id FROM system_menu
    WHERE deleted = b'0' AND permission IN ('bpm:task:query', 'bpm:task:update',
        'bpm:process-instance:query', 'pms:sol-solution:query', 'pms:project:query')
    UNION DISTINCT
    SELECT m.id, m.parent_id FROM system_menu m JOIN review_menus c ON c.parent_id = m.id
    WHERE m.deleted = b'0'
)
SELECT r.id, m.id, 'seed_solution_review', 'seed_solution_review', r.tenant_id
FROM system_role r CROSS JOIN review_menus m
WHERE r.tenant_id = 1 AND r.code = 'ENGINEERING_MANAGEMENT_REVIEW' AND r.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id
    AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');
