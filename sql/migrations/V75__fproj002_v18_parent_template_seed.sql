-- F-PROJ-002 V1.8：拆分子项目按正式契约继承父项目冻结模板版本。
-- 仅绑定V55已发布的完整S0~S6示例模板，不创建或修改模板主数据。
UPDATE `proj_project` p
JOIN `proj_project_template_revision` r
  ON r.`id`=911016 AND r.`template_id`=910008 AND r.`revision_no`=1
  AND r.`status`='PUBLISHED' AND r.`deleted`=b'0'
SET p.`lifecycle_template_id`=r.`template_id`,
    p.`lifecycle_template_revision_no`=r.`revision_no`,
    p.`template_load_method`='MANUAL_SELECTED',
    p.`updater`='seed', p.`update_time`=NOW()
WHERE p.`tenant_id`=0 AND p.`id`=992002000000 AND p.`deleted`=b'0';
