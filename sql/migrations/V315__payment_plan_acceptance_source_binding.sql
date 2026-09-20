-- Current-input ownership only. Approved schedule snapshots remain immutable.
CREATE TABLE proj_payment_acceptance_binding (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    node_type VARCHAR(16) NOT NULL,
    node_id BIGINT NOT NULL,
    source_owner VARCHAR(96) NOT NULL,
    source_key VARCHAR(128) NOT NULL,
    creator VARCHAR(64) NOT NULL DEFAULT 'payment_plan_sync',
    updater VARCHAR(64) NOT NULL DEFAULT 'payment_plan_sync',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_acceptance_node (tenant_id, node_type, node_id),
    UNIQUE KEY uk_payment_acceptance_source (tenant_id, source_owner, source_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回款计划验收输入来源绑定';
