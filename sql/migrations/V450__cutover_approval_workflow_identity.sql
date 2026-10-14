-- CUT业务审批保留原版本快照；BPM独立拥有运行实例和待办。只新增身份连接，不回刷历史审批。
CREATE TABLE IF NOT EXISTS cut_approval_workflow (
    id BIGINT NOT NULL COMMENT 'CUT审批实例ID',
    task_id BIGINT NOT NULL,
    process_instance_id VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cut_workflow_process (tenant_id, process_instance_id),
    KEY idx_cut_workflow_task (tenant_id, task_id),
    CONSTRAINT fk_cut_workflow_approval FOREIGN KEY (id) REFERENCES cut_approval_instance (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='割接审批与BPM运行身份';
