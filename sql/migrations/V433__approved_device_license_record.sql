-- User-approved 16-column formal license history; no plaintext license codes.
CREATE TABLE ast_device_license_record (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    device_id BIGINT NULL,
    device_sn VARCHAR(255) NULL,
    contract_id BIGINT NULL,
    contract_no VARCHAR(32) NULL,
    contract_type VARCHAR(16) NULL,
    product_code VARCHAR(255) NULL,
    product_model VARCHAR(256) NULL,
    license_status VARCHAR(255) NULL,
    license_code_ciphertext TEXT CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    creator VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL,
    updater VARCHAR(64) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    KEY idx_license_device (tenant_id,device_id,deleted),
    KEY idx_license_contract (tenant_id,contract_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备许可历史记录';
