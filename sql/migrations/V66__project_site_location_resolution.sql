ALTER TABLE proj_project
    ADD COLUMN location_resolution_status VARCHAR(16) NOT NULL DEFAULT 'UNRESOLVED'
        COMMENT '地点解析状态：RESOLVED/UNRESOLVED' AFTER implementation_location;

CREATE TABLE proj_project_site (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '关系ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户编号',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    site_id BIGINT NOT NULL COMMENT 'AST站点稳定ID（无物理外键）',
    site_version_snapshot INT NOT NULL COMMENT '站点引用版本快照',
    primary_site BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否主站点',
    scope_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '范围状态',
    effective_from DATETIME NOT NULL COMMENT '生效开始时间',
    effective_to DATETIME NULL COMMENT '失效时间',
    site_code_snapshot VARCHAR(64) NOT NULL COMMENT '站点编码快照',
    site_name_snapshot VARCHAR(128) NOT NULL COMMENT '站点名称快照',
    address_snapshot JSON NULL COMMENT '地址快照',
    deleted BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    current_primary_project_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN primary_site = b'1' AND effective_to IS NULL AND scope_status = 'ACTIVE'
              AND deleted = b'0' THEN project_id ELSE NULL END) STORED,
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新者',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_proj_site_interval (tenant_id, project_id, site_id, effective_from),
    UNIQUE KEY uk_proj_current_primary (tenant_id, current_primary_project_id),
    KEY idx_proj_site_project (tenant_id, project_id, effective_to),
    KEY idx_proj_site_site (tenant_id, site_id, effective_to),
    CONSTRAINT chk_proj_site_interval CHECK (effective_to IS NULL OR effective_to > effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目实施站点时态关系';
