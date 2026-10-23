-- Reconcile the existing V408 mapping for implementation-solution writers.
-- Preserve intentional revocations (including soft-deleted role-menu rows).
-- Native Owner permission, lifecycle and project-scope checks remain mandatory.
INSERT INTO system_role_menu(role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT DISTINCT source.role_id,target.id,'solution-delivery-mapping','solution-delivery-mapping',b'0',source.tenant_id
FROM system_role_menu source
JOIN system_role role_fact ON role_fact.id=source.role_id AND role_fact.tenant_id=source.tenant_id
                         AND role_fact.deleted=b'0' AND role_fact.status=0
JOIN system_menu original ON original.id=source.menu_id AND original.deleted=b'0' AND original.status=0
JOIN system_menu target ON target.permission='pms:delivery:operate' AND target.deleted=b'0' AND target.status=0
WHERE source.deleted=b'0' AND original.permission='pms:sol-solution:update'
  AND NOT EXISTS(SELECT 1 FROM system_role_menu existing
      WHERE existing.role_id=source.role_id AND existing.menu_id=target.id AND existing.tenant_id=source.tenant_id);
