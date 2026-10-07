-- Tenant-level presentation configuration is distinct from ordinary project record updates.
-- Register assignable actions; deliberately do not create any role or role-menu grant.
INSERT INTO system_menu
 (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT action.id,action.name,action.permission,3,9991,parent.id,'','',NULL,NULL,0,b'0',b'1',b'1','business-defaults','business-defaults',b'0'
FROM (
 SELECT 970000000000099401 AS id,'工勘字段配置' AS name,'pms:sol-site-survey:configure' AS permission,'pms:sol-site-survey:query' AS source_permission
 UNION ALL SELECT 970000000000099402,'需求分析字段配置','pms:requirement-analysis:configure','pms:requirement-analysis:query'
) action
JOIN (SELECT permission,MIN(parent_id) AS id FROM system_menu WHERE deleted=b'0' AND status=0 GROUP BY permission) parent
 ON parent.permission=action.source_permission
WHERE NOT EXISTS(SELECT 1 FROM system_menu existing WHERE existing.permission=action.permission)
  AND NOT EXISTS(SELECT 1 FROM system_menu existing WHERE existing.id=action.id);
