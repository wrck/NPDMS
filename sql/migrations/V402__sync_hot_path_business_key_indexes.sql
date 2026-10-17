-- 400 万级同步热路径。只增加普通索引，不改变业务键、归属或删除语义。
-- 部署前核对磁盘/临时空间并安排 DDL 窗口；LOCK=NONE 仍可能等待短暂元数据锁。
-- 已存在同列同序索引（包括历史人工补建）时跳过，避免重复索引。
SET @sync_order_lookup_exists = (
  SELECT COUNT(*) FROM (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_in_order
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='com_sales_order_line'
    GROUP BY index_name
  ) indexes_in_scope
  WHERE columns_in_order='tenant_id,order_no,line_no,product_code'
);
SET @sync_order_lookup_ddl = IF(@sync_order_lookup_exists>0,'SELECT 1',
  'ALTER TABLE com_sales_order_line ADD INDEX idx_sales_order_line_sync_lookup (tenant_id,order_no,line_no,product_code), ALGORITHM=INPLACE, LOCK=NONE');
PREPARE sync_order_lookup_statement FROM @sync_order_lookup_ddl;
EXECUTE sync_order_lookup_statement;
DEALLOCATE PREPARE sync_order_lookup_statement;

SET @sync_binding_page_exists = (
  SELECT COUNT(*) FROM (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_in_order
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='int_sync_binding'
    GROUP BY index_name
  ) indexes_in_scope
  WHERE columns_in_order='tenant_id,task_id,deleted,id'
);
SET @sync_binding_page_ddl = IF(@sync_binding_page_exists>0,'SELECT 1',
  'ALTER TABLE int_sync_binding ADD INDEX idx_sync_binding_task_page (tenant_id,task_id,deleted,id), ALGORITHM=INPLACE, LOCK=NONE');
PREPARE sync_binding_page_statement FROM @sync_binding_page_ddl;
EXECUTE sync_binding_page_statement;
DEALLOCATE PREPARE sync_binding_page_statement;
