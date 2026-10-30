-- Business file buttons must not depend on granting the infrastructure file console.
-- Keep the original entries for other roles. These aliases add no permission key.
INSERT INTO system_menu
 (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT aliases.id,source.name,source.permission,3,source.sort,18000,'','',NULL,NULL,0,b'0',b'1',b'1',
       'business-file-entry','business-file-entry',b'0'
FROM (
 SELECT 970000000000099780 AS id,198780 AS source_id
 UNION ALL SELECT 970000000000099781,198781
 UNION ALL SELECT 970000000000099782,198782
 UNION ALL SELECT 970000000000099783,198783
 UNION ALL SELECT 970000000000099785,198785
) aliases
JOIN system_menu source ON source.id=aliases.source_id AND source.deleted=b'0' AND source.status=0
JOIN system_menu parent ON parent.id=18000 AND parent.deleted=b'0' AND parent.status=0
WHERE NOT EXISTS(SELECT 1 FROM system_menu existing WHERE existing.id=aliases.id)
  AND NOT EXISTS(SELECT 1 FROM system_menu existing WHERE existing.parent_id=18000 AND existing.permission=source.permission);

-- Only the two requested roles, and only keys already explicitly granted to them.
-- Preserve disabled/revoked source grants, target revocations and all other roles.
INSERT INTO system_role_menu(role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT DISTINCT grant_source.role_id,target.id,'business-file-entry','business-file-entry',b'0',grant_source.tenant_id
FROM system_role_menu grant_source
JOIN system_role role_fact ON role_fact.id=grant_source.role_id AND role_fact.tenant_id=grant_source.tenant_id
    AND role_fact.code IN ('PROJECT_MANAGER','SERVICE_MANAGER') AND role_fact.status=0 AND role_fact.deleted=b'0'
JOIN system_menu source ON source.id=grant_source.menu_id AND source.id IN (198780,198781,198782,198783,198785)
    AND source.status=0 AND source.deleted=b'0'
JOIN system_menu target ON target.permission=source.permission AND target.parent_id=18000
    AND target.id IN (970000000000099780,970000000000099781,970000000000099782,970000000000099783,970000000000099785)
    AND target.status=0 AND target.deleted=b'0'
JOIN system_role_menu parent_grant ON parent_grant.role_id=grant_source.role_id AND parent_grant.tenant_id=grant_source.tenant_id
    AND parent_grant.menu_id=18000 AND parent_grant.deleted=b'0'
WHERE grant_source.deleted=b'0'
  AND NOT EXISTS(SELECT 1 FROM system_role_menu existing WHERE existing.role_id=grant_source.role_id
      AND existing.tenant_id=grant_source.tenant_id AND existing.menu_id=target.id);
