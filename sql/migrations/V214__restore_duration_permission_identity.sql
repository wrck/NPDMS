-- V91 duration permission IDs were reused by device menus. Preserve those device rows;
-- restore only the previously defined PMS permission identities, without role grants.
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 993109090008,'项目工期查询','pms:construction-plan:query',3,130,18071,'','',NULL,NULL,0,b'1',b'1',b'1','site-survey-restoration',NOW(),'site-survey-restoration',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='pms:construction-plan:query' AND deleted=b'0');
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 993109090009,'项目工期维护','pms:construction-plan:duration-manage',3,140,18071,'','',NULL,NULL,0,b'1',b'1',b'1','site-survey-restoration',NOW(),'site-survey-restoration',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='pms:construction-plan:duration-manage' AND deleted=b'0');
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 993109090010,'项目工期审批','pms:construction-plan:duration-approve',3,150,18071,'','',NULL,NULL,0,b'1',b'1',b'1','site-survey-restoration',NOW(),'site-survey-restoration',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='pms:construction-plan:duration-approve' AND deleted=b'0');
