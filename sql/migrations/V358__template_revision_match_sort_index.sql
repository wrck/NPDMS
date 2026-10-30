-- 模板匹配按发布版本列表查询会读取包含大JSON列的整行并按revision_no排序；
-- 现有idx_template_status(template_id,status)使优化器放弃uk_tenant_template_revision的索引序，
-- 退化为filesort，行宽超过sort_buffer_size时MySQL抛Out of sort memory，导致模板匹配整体不可用。
-- 用(template_id,status,revision_no)替代，使ORDER BY revision_no直接走索引序，消除filesort。
ALTER TABLE proj_project_template_revision
    ADD INDEX idx_template_status_revision (template_id, status, revision_no),
    DROP INDEX idx_template_status;
