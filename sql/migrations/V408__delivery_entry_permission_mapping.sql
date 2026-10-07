-- Shared delivery entry actions: assignable, never a substitute for native Owner authorization.
-- No fixed role mapping, no catalog-management grant inferred from native business permissions.
INSERT INTO system_menu
 (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT action.id,action.name,action.permission,3,9990,catalog.id,'','',NULL,NULL,0,b'0',b'1',b'1',
       'delivery-entry','delivery-entry',b'0'
FROM (
 SELECT 970000000000099241 AS id,'统一交付查询' AS name,'pms:delivery:query' AS permission
 UNION ALL SELECT 970000000000099242,'统一交付操作','pms:delivery:operate'
 UNION ALL SELECT 970000000000099243,'统一交付配置管理','pms:delivery:manage'
) action
-- Permission aliases are valid; choose one stable active parent for each fixed action ID.
JOIN (SELECT MIN(id) AS id FROM system_menu
      WHERE permission='pms:business-model:query' AND deleted=b'0' AND status=0) catalog ON catalog.id IS NOT NULL
WHERE NOT EXISTS(SELECT 1 FROM system_menu existing WHERE existing.permission=action.permission)
  AND NOT EXISTS(SELECT 1 FROM system_menu existing WHERE existing.id=action.id);

-- Add only the shared read gate to roles already granted an implemented native read permission.
-- Role tenant must match source grant; revoked/inactive source grants and targets never participate.
-- Preserve intentional target revocations: existence checks include soft-deleted role-menu rows.
INSERT INTO system_role_menu(role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT DISTINCT source.role_id,target.id,'delivery-entry','delivery-entry',b'0',source.tenant_id
FROM system_role_menu source
JOIN system_role role_fact ON role_fact.id=source.role_id AND role_fact.tenant_id=source.tenant_id
                         AND role_fact.deleted=b'0' AND role_fact.status=0
JOIN system_menu original ON original.id=source.menu_id AND original.deleted=b'0' AND original.status=0
JOIN system_menu target ON target.permission='pms:delivery:query' AND target.deleted=b'0' AND target.status=0
WHERE source.deleted=b'0'
  AND original.permission IN ('pms:project:query','pms:sol-solution:query','pms:sol-briefing:query',
      'pms:requirement-analysis:query','pms:requirement-analysis:manage',
      'pms:imp-training:query','pms:imp-arrival:query','pms:acc-archive-document:query',
      'pms:acc-completion-certificate:query','pms:acc-deliverable-checklist:query',
      'pms:acceptance:report:query','pms:acceptance:satisfaction:query')
  AND NOT EXISTS(SELECT 1 FROM system_role_menu existing
      WHERE existing.role_id=source.role_id AND existing.menu_id=target.id AND existing.tenant_id=source.tenant_id);

-- Only Owner write permissions for currently supported generic material writers map the operation gate.
-- Every command still rechecks the Owner permission, native state and current project scope.
INSERT INTO system_role_menu(role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT DISTINCT source.role_id,target.id,'delivery-entry','delivery-entry',b'0',source.tenant_id
FROM system_role_menu source
JOIN system_role role_fact ON role_fact.id=source.role_id AND role_fact.tenant_id=source.tenant_id
                         AND role_fact.deleted=b'0' AND role_fact.status=0
JOIN system_menu original ON original.id=source.menu_id AND original.deleted=b'0' AND original.status=0
JOIN system_menu target ON target.permission='pms:delivery:operate' AND target.deleted=b'0' AND target.status=0
WHERE source.deleted=b'0'
  AND original.permission IN ('pms:project:update','pms:sol-solution:update',
      'pms:acc-archive-document:update','pms:acc-completion-certificate:update')
  AND NOT EXISTS(SELECT 1 FROM system_role_menu existing
      WHERE existing.role_id=source.role_id AND existing.menu_id=target.id AND existing.tenant_id=source.tenant_id);
