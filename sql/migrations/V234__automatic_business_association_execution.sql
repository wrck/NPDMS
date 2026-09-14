-- Record the receiving execution, without changing old association/completion history.
ALTER TABLE proj_task_business_link
    ADD COLUMN node_execution_id BIGINT NULL COMMENT '自动关联接收方：当前节点执行实例',
    ADD KEY idx_task_business_execution (tenant_id, project_id, task_id, node_execution_id);
