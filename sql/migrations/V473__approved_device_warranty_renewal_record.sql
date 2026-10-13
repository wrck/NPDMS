-- Approved independent renewal entity. Existing warranty records/projections remain unchanged.
CREATE TABLE ast_device_warranty_renewal_record (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    device_sn VARCHAR(100) NULL COMMENT '设备序列号',
    warranty_contract_no VARCHAR(64) NULL COMMENT '续保合同号',
    warranty_grade VARCHAR(32) NULL COMMENT '维保等级编码',
    warranty_start_date DATE NULL COMMENT '实际开始日期',
    warranty_end_date DATE NULL COMMENT '实际结束日期',
    warranty_months INT UNSIGNED NULL COMMENT '参考月数，不据此重算实际日期',
    extended BIT NULL COMMENT '是否延保，NULL为未记录',
    is_latest BIT NULL DEFAULT NULL COMMENT '原最新选择标记，不代表在保状态',
    remark TEXT NULL COMMENT '续保备注',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL,
    updater VARCHAR(64) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    KEY idx_renewal_device (tenant_id,device_sn,warranty_start_date,id),
    KEY idx_renewal_contract (tenant_id,warranty_contract_no,device_sn),
    CONSTRAINT ck_renewal_dates CHECK (warranty_start_date IS NULL OR warranty_end_date IS NULL
        OR warranty_end_date >= warranty_start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备续保记录';
