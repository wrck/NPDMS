-- 百万级同步绑定按当前租户和任务分页、计数及精简字段快照。
-- 同时覆盖软删除条件和主键排序，避免扫描其他任务的绑定记录。
SET SESSION lock_wait_timeout = 15;
SET @sync_binding_task_page_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_sync_binding'
      AND index_name = 'idx_int_binding_task_page'
);
SET @sync_binding_task_page_ddl := IF(
    @sync_binding_task_page_index > 0,
    'SELECT 1',
    'ALTER TABLE int_sync_binding ADD INDEX idx_int_binding_task_page (tenant_id, task_id, deleted, id), ALGORITHM=INPLACE, LOCK=NONE'
);
PREPARE sync_binding_task_page_stmt FROM @sync_binding_task_page_ddl;
EXECUTE sync_binding_task_page_stmt;
DEALLOCATE PREPARE sync_binding_task_page_stmt;
