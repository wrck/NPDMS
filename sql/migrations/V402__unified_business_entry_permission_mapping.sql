-- Unified entity migration: route existing SOL Owner permissions through the public entry.
-- Only ordinary routing/menu permissions are added. Native business permissions, scope,
-- status checks and Owner commands remain unchanged. No fixed business-role assignment.
-- V223 defines the parent navigation and V365 defines the catalog query menu.
INSERT INTO system_menu
    (id,name,permission,type,sort,parent_id,path,icon,component,component_name,
     status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 970000000000099224,'统一业务实体办理','pms:business-model:operate',3,9990,catalog.id,
       '','',NULL,NULL,0,b'0',b'1',b'1','unified-owner-entry','unified-owner-entry',b'0'
FROM system_menu catalog
WHERE catalog.permission='pms:business-model:query' AND catalog.deleted=b'0' AND catalog.status=0
  AND NOT EXISTS (SELECT 1 FROM system_menu existing
                  WHERE existing.permission='pms:business-model:operate' AND existing.deleted=b'0');

-- Preserve manager-as-reader semantics for requirement analysis; do not manufacture a native query grant.
-- The catalog and its existing parent only expose navigation. Each Owner still authorizes its own reads.
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT DISTINCT source.role_id,target.id,'unified-owner-entry','unified-owner-entry',b'0',source.tenant_id
FROM system_role_menu source
JOIN system_role role_fact ON role_fact.id=source.role_id AND role_fact.tenant_id=source.tenant_id
                          AND role_fact.deleted=b'0' AND role_fact.status=0
JOIN system_menu original ON original.id=source.menu_id AND original.deleted=b'0' AND original.status=0
JOIN system_menu target ON (target.permission='pms:business-model:query' OR target.id=993109100501)
                       AND target.deleted=b'0' AND target.status=0
WHERE source.deleted=b'0'
  AND original.permission IN ('pms:sol-site-survey:query','pms:requirement-analysis:query','pms:requirement-analysis:manage')
  AND NOT EXISTS (SELECT 1 FROM system_role_menu existing
      WHERE existing.role_id=source.role_id AND existing.menu_id=target.id AND existing.tenant_id=source.tenant_id);

-- Common operate is a routing gate, never a replacement for the specific Owner permission.
-- Retain tenant consistency and intentional prior revocations (including soft-deleted target grants).
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT DISTINCT source.role_id,target.id,'unified-owner-entry','unified-owner-entry',b'0',source.tenant_id
FROM system_role_menu source
JOIN system_role role_fact ON role_fact.id=source.role_id AND role_fact.tenant_id=source.tenant_id
                          AND role_fact.deleted=b'0' AND role_fact.status=0
JOIN system_menu original ON original.id=source.menu_id AND original.deleted=b'0' AND original.status=0
JOIN system_menu target ON target.permission='pms:business-model:operate' AND target.deleted=b'0' AND target.status=0
WHERE source.deleted=b'0'
  AND original.permission IN ('pms:sol-site-survey:create','pms:sol-site-survey:update','pms:sol-site-survey:delete',
                              'pms:requirement-analysis:manage')
  AND NOT EXISTS (SELECT 1 FROM system_role_menu existing
      WHERE existing.role_id=source.role_id AND existing.menu_id=target.id AND existing.tenant_id=source.tenant_id);
