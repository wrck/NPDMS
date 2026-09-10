-- PM-01 / Q-FPROJ-012: extend the agreed member relation, never create or rewrite legacy team entities.
ALTER TABLE proj_project_member_assignment
    ADD COLUMN remark VARCHAR(500) NULL COMMENT '普通备注，非指派原因',
    ADD COLUMN end_reason VARCHAR(500) NULL COMMENT '区间结束原因，保留原加入/调整原因';

INSERT INTO system_dict_data
    (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater,deleted)
SELECT entry.id,entry.sort,entry.label,entry.value,'pms_project_member_role',0,'info','',
       'PM-01普通成员；不授予经理、审批或凭证权限','s0-inheritance','s0-inheritance',b'0'
FROM (
    SELECT 993109100504 AS id,4 AS sort,'团队成员/工程师' AS label,'ENGINEER' AS value
    UNION ALL SELECT 993109100505,5,'外协/驻场工程师','OUTSOURCED_ENGINEER'
) entry
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data existing
                  WHERE existing.id=entry.id OR (existing.dict_type='pms_project_member_role' AND existing.value=entry.value));
