-- Shared tenant-scoped field presentation. No business data or permissions are stored here.
CREATE TABLE IF NOT EXISTS plt_business_field_configuration (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    owner_module VARCHAR(64) NOT NULL,
    entity_type VARCHAR(128) NOT NULL,
    fields_json JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_business_field_configuration (tenant_id,owner_module,entity_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
