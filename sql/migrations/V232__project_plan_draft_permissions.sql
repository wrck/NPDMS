-- One editable project-owned draft; public template versions remain untouched.
ALTER TABLE proj_project_plan_version
    ADD COLUMN draft_marker TINYINT GENERATED ALWAYS AS (CASE WHEN status='DRAFT' AND deleted=b'0' THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_project_plan_draft (tenant_id, project_id, draft_marker);

-- Separate plan/rework authority, configured through existing roles; no automatic role grants.
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 993009232001,'项目计划改版','pms:project-plan:manage',3,180,18071,'','',NULL,NULL,0,b'1',b'1',b'1','project-rules',NOW(),'project-rules',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='pms:project-plan:manage' AND deleted=b'0');
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 993009232002,'项目节点返工','pms:project-plan:rework',3,190,18071,'','',NULL,NULL,0,b'1',b'1',b'1','project-rules',NOW(),'project-rules',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='pms:project-plan:rework' AND deleted=b'0');
