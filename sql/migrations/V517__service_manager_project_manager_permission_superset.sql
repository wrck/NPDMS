-- Approved 2026-10-26: service managers are regional heads and include all PM functions.
-- Add current PM menus within the same tenant. Existing and deleted grant history stays intact.
-- Project scopes, owner facts, workflow assignees and draft/history guards remain independent.
INSERT INTO system_role_menu(role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT DISTINCT service_role.id,pm_grant.menu_id,'sm-permission-superset','sm-permission-superset',b'0',service_role.tenant_id
FROM system_role service_role
JOIN system_role project_role ON project_role.tenant_id=service_role.tenant_id
  AND project_role.code='PROJECT_MANAGER' AND project_role.status=0 AND project_role.deleted=b'0'
JOIN system_role_menu pm_grant ON pm_grant.role_id=project_role.id
  AND pm_grant.tenant_id=project_role.tenant_id AND pm_grant.deleted=b'0'
JOIN system_menu menu_row ON menu_row.id=pm_grant.menu_id AND menu_row.status=0 AND menu_row.deleted=b'0'
WHERE service_role.code IN ('SERVICE_MANAGER','SERVICE_MANAGER_L1','SERVICE_MANAGER_L2')
  AND service_role.status=0 AND service_role.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu existing
    WHERE existing.role_id=service_role.id AND existing.menu_id=pm_grant.menu_id
      AND existing.tenant_id=service_role.tenant_id AND existing.deleted=b'0');
