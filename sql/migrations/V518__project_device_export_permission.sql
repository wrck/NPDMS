-- TF-039, approved 2026-10-27: independent export for PM / regional service managers.
-- DML only. Does not expand project data scopes or modify existing/deleted grants.
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 993039000001,'项目设备清单导出','pms:project-device:export',3,50,198770,'','',NULL,NULL,0,b'1',b'1',b'1','tf039-export',NOW(),'tf039-export',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=993039000001 OR permission='pms:project-device:export');

INSERT INTO system_role_menu(role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT role_row.id,menu_row.id,'tf039-export','tf039-export',b'0',role_row.tenant_id
FROM system_role role_row
JOIN system_menu menu_row ON menu_row.permission='pms:project-device:export' AND menu_row.status=0 AND menu_row.deleted=b'0'
WHERE role_row.code IN ('PROJECT_MANAGER','SERVICE_MANAGER','SERVICE_MANAGER_L1','SERVICE_MANAGER_L2')
  AND role_row.status=0 AND role_row.deleted=b'0'
  AND NOT EXISTS(SELECT 1 FROM system_role_menu existing
    WHERE existing.role_id=role_row.id AND existing.menu_id=menu_row.id AND existing.tenant_id=role_row.tenant_id AND existing.deleted=b'0');
