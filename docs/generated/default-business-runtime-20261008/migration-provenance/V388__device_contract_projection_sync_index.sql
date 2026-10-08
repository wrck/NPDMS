-- 合同同步的设备归属刷新按租户、合同号和无项目归属筛选。
-- 避免每个同步分块在百万级设备表上扫描主键范围；保持既有筛选、授权和刷新事务。
SET SESSION lock_wait_timeout = 15;
SET @device_contract_projection_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ast_device'
      AND index_name = 'idx_ast_device_contract_projection'
);
SET @device_contract_projection_ddl := IF(
    @device_contract_projection_index > 0,
    'SELECT 1',
    'ALTER TABLE ast_device ADD INDEX idx_ast_device_contract_projection (tenant_id, contract_no, project_id, deleted, id), ALGORITHM=INPLACE, LOCK=NONE'
);
PREPARE device_contract_projection_stmt FROM @device_contract_projection_ddl;
EXECUTE device_contract_projection_stmt;
DEALLOCATE PREPARE device_contract_projection_stmt;
