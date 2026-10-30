-- 百万级设备工作台权限候选和精确总数统计的覆盖索引。
-- 不改变租户、组织、合同、有效项目关系的可见范围，不修改设备及历史数据。
SET SESSION lock_wait_timeout = 15;
SET @device_visibility_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ast_device'
      AND index_name = 'idx_ast_device_visibility_cover'
);
SET @device_visibility_ddl := IF(
    @device_visibility_index > 0,
    'SELECT 1',
    'ALTER TABLE ast_device ADD INDEX idx_ast_device_visibility_cover (tenant_id, deleted, company_id, department_id, department_code, organization_source, project_id, contract_no, sn), ALGORITHM=INPLACE, LOCK=NONE'
);
PREPARE device_visibility_stmt FROM @device_visibility_ddl;
EXECUTE device_visibility_stmt;
DEALLOCATE PREPARE device_visibility_stmt;
