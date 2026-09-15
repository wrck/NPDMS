-- Each child selects its own published revision. Existing drafts must be explicitly completed;
-- no parent-version backfill and no modification of already-created projects or frozen plans.
ALTER TABLE proj_project_split_item
    ADD COLUMN template_revision_id BIGINT NULL COMMENT '子项目显式选择的已发布模板版本',
    ADD COLUMN template_selection_reason VARCHAR(512) NULL COMMENT '模板匹配覆盖原因';

-- Separate override authority, assigned through existing roles; no automatic grants.
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 993009245001,'项目模板匹配覆盖','pms:project-template:override',3,200,18071,'','',NULL,NULL,0,b'1',b'1',b'1','project-rules',NOW(),'project-rules',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='pms:project-template:override' AND deleted=b'0');
