-- PM-01 / CUS-03: independent inheritance entry; never change old menu or business data.
INSERT INTO system_menu
    (id,name,permission,type,sort,parent_id,path,icon,component,component_name,
     status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT entry.id,entry.name,entry.permission,entry.type,entry.sort,entry.parent_id,
       entry.path,entry.icon,entry.component,entry.component_name,
       0,entry.visible,b'1',b'1','s0-inheritance','s0-inheritance',b'0'
FROM (
    SELECT 993109100501 AS id,'项目交付（新功能）' AS name,'' AS permission,1 AS type,
           80 AS sort,0 AS parent_id,'/pms-inheritance' AS path,'ep:files' AS icon,
           NULL AS component,NULL AS component_name,b'1' AS visible
    UNION ALL
    SELECT 993109100502,'项目列表','pms:project:query',2,1,993109100501,
           'projects','ep:list','pms/project/inheritance/projects/index','PmsProjectInheritanceList',b'1'
    UNION ALL
    SELECT 993109100503,'项目详情','pms:project:query',2,2,993109100501,
           'project-detail','ep:document','pms/project/inheritance/detail/index','PmsProjectInheritanceDetail',b'0'
) entry
WHERE NOT EXISTS (SELECT 1 FROM system_menu existing WHERE existing.id=entry.id);

-- New navigation is available only to existing project-page roles; no new functional/data permission.
INSERT INTO system_role_menu
    (role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT DISTINCT source.role_id,entry.menu_id,'s0-inheritance','s0-inheritance',b'0',source.tenant_id
FROM system_role_menu source
JOIN system_menu original ON original.id=source.menu_id AND original.deleted=b'0'
CROSS JOIN (
    SELECT 993109100501 AS menu_id
    UNION ALL SELECT 993109100502
    UNION ALL SELECT 993109100503
) entry
WHERE source.deleted=b'0' AND original.component='pms/project/projects/index'
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu existing
      WHERE existing.tenant_id=source.tenant_id AND existing.role_id=source.role_id
        AND existing.menu_id=entry.menu_id
  );
