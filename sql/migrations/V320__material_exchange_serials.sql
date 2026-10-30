-- 换货申请设备序列号快照；仅新增子表，不重写旧申请或设备档案。
CREATE TABLE IF NOT EXISTS imp_eng_material_exchange_serial (
    id bigint NOT NULL AUTO_INCREMENT,
    exchange_id bigint NOT NULL COMMENT '换货申请ID',
    equipment_id bigint NOT NULL COMMENT '设备稳定ID',
    sn varchar(128) NOT NULL COMMENT '保存时的设备序列号',
    name varchar(255) DEFAULT NULL,
    product_code varchar(128) DEFAULT NULL,
    product_model varchar(255) DEFAULT NULL,
    contract_no varchar(128) DEFAULT NULL,
    creator varchar(64) DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0',
    tenant_id bigint NOT NULL DEFAULT 0,
    active_equipment_id bigint GENERATED ALWAYS AS (IF(deleted = b'0', equipment_id, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exchange_active_device (tenant_id, exchange_id, active_equipment_id),
    KEY idx_exchange_serial (tenant_id, exchange_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='换货申请序列号子表';
