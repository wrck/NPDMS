-- Requirement: COM-01; project parties follow the approved core migration model.
-- User-authorized missing-table delivery, 2026-09-17.
-- Source: specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql
-- Schema only: preserve source facts; no legacy extraction, master aggregation or historical writes.
-- Contract master source policy is pending user clarification; this migration does not change it.

CREATE TABLE proj_project_party (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '关联项目记录的全局唯一ID',
    party_role VARCHAR(32) NOT NULL COMMENT '参与方角色编码，取值由对应业务字典约束',
    party_code VARCHAR(128) NULL COMMENT '项目参与方的参与方编码',
    party_name VARCHAR(1024) NULL COMMENT '项目参与方的参与方名称',
    contact_name VARCHAR(255) NULL COMMENT '项目参与方的联系人名称',
    phone VARCHAR(128) NULL COMMENT '项目参与方的联系人电话',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统编码，用于同步幂等和数据血缘追踪',
    source_table VARCHAR(64) NOT NULL COMMENT '来源系统物理表名，仅用于迁移或同步血缘',
    source_record_key VARCHAR(128) NOT NULL COMMENT '来源记录稳定唯一键，用于幂等写入和回溯',
    effective_from DATETIME(3) NULL COMMENT '业务关系或事实开始生效的时间，空值表示来源未提供',
    effective_to DATETIME(3) NULL COMMENT '业务关系或事实失效的时间，空值表示当前仍有效',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_party_tenant_row (tenant_id, id),
    UNIQUE KEY uk_project_party_source (
        tenant_id, source_system, source_table, source_record_key, party_role
    ),
    KEY idx_project_party_project (
        tenant_id, project_id, party_role, status
    ),
    KEY idx_project_party_code (
        tenant_id, party_role, party_code, status
    ),
    CONSTRAINT fk_project_party_project
        FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id),
    CONSTRAINT chk_project_party_dates
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_project_party_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目参与方，按合同客户、最终用户、代理商、服务商等角色保存';

CREATE TABLE com_contract_receivable (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    contract_id BIGINT NULL COMMENT '关联合同记录的全局唯一ID',
    contract_no VARCHAR(64) NOT NULL COMMENT '合同回款的合同编号',
    company_id BIGINT NULL COMMENT '合同回款所属公司对应的平台公司主档ID',
    company_code VARCHAR(64) NULL COMMENT '公司编码',
    company_name VARCHAR(255) NULL COMMENT '公司名称',
    company_resolution_source VARCHAR(32) NULL COMMENT '合同回款的公司解析来源',
    mapping_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_COMPANY' COMMENT '跨系统关联解析状态，如待映射、已映射或存在冲突',
    project_name VARCHAR(512) NULL COMMENT '合同回款的项目名称',
    source_order_no VARCHAR(64) NULL COMMENT '合同回款的来源订单编号',
    customer_code VARCHAR(64) NULL COMMENT '合同回款的客户编码',
    customer_name VARCHAR(512) NULL COMMENT '合同回款的客户名称',
    contract_amount DECIMAL(20, 2) NOT NULL COMMENT '合同金额，币种和含税口径沿用来源业务单据',
    delivered_amount DECIMAL(20, 2) NOT NULL COMMENT '已发货金额，币种和含税口径沿用来源业务单据',
    collected_amount DECIMAL(20, 2) NOT NULL COMMENT '已收金额，币种和含税口径沿用来源业务单据',
    collected_ratio DECIMAL(18, 6) NULL COMMENT '合同回款的已收比例',
    receivable_amount DECIMAL(20, 2) NULL COMMENT '应收金额，币种和含税口径沿用来源业务单据',
    overdue_amount DECIMAL(20, 2) NULL COMMENT '逾期金额，币种和含税口径沿用来源业务单据',
    currency_name VARCHAR(32) NULL COMMENT '合同回款的币种名称',
    marketing_department_id BIGINT NULL COMMENT '合同回款市场部门对应的平台共享部门主档ID',
    marketing_department_code VARCHAR(64) NULL COMMENT '市场部门编码',
    marketing_department_name VARCHAR(128) NULL COMMENT '市场部门名称',
    office_department_id BIGINT NULL COMMENT '合同回款办事处对应的平台共享部门主档ID',
    office_department_code VARCHAR(64) NULL COMMENT '办事处部门编码',
    office_department_name VARCHAR(128) NULL COMMENT '办事处部门名称',
    industry_name VARCHAR(128) NULL COMMENT '合同回款的行业名称',
    marketing_representative_name VARCHAR(128) NULL COMMENT '合同回款的市场代表名称',
    source_batch_code VARCHAR(32) NULL COMMENT '合同回款的来源批次编码',
    import_batch_no VARCHAR(64) NULL COMMENT '合同回款的导入批次编号',
    project_code VARCHAR(80) NULL COMMENT '合同回款的项目编码',
    system_department_source_key VARCHAR(64) NULL COMMENT '合同回款来源中的系统部门原始ID或键，不冒充正式部门编码',
    system_department_id BIGINT NULL COMMENT '合同回款系统部门对应的平台共享部门主档ID',
    system_department_code VARCHAR(64) NULL COMMENT '解析后的系统部门编码',
    system_department_name VARCHAR(255) NULL COMMENT '解析后的系统部门名称',
    industry_code VARCHAR(64) NULL COMMENT '合同回款的行业编码',
    expansion_department_source_key VARCHAR(64) NULL COMMENT '合同回款来源中的拓展部门原始ID或键，不冒充正式部门编码',
    expansion_department_id BIGINT NULL COMMENT '合同回款拓展部门对应的平台共享部门主档ID',
    expansion_department_code VARCHAR(64) NULL COMMENT '解析后的拓展部门编码',
    expansion_department_name VARCHAR(255) NULL COMMENT '解析后的拓展部门名称',
    marketing_representative_code VARCHAR(64) NULL COMMENT '合同回款的市场代表编码',
    secondary_representative_code VARCHAR(64) NULL COMMENT '合同回款的辅助代表编码',
    original_system_department_source_key VARCHAR(64) NULL COMMENT '合同回款来源中的原始系统部门ID或键，按来源原值保留',
    original_expansion_department_source_key VARCHAR(64) NULL COMMENT '合同回款来源中的原始拓展部门ID或键，按来源原值保留',
    original_industry_name VARCHAR(128) NULL COMMENT '合同回款的原始行业名称',
    source_effective_from DATETIME(3) NULL COMMENT '来源生效开始时间，空值表示未限定开始时间',
    source_effective_to DATETIME(3) NULL COMMENT '来源生效结束时间，空值表示当前仍有效',
    contract_create_time DATETIME(3) NULL COMMENT '合同创建时间，采用系统统一时区，空值表示来源未提供或事件未发生',
    latest_ship_time DATETIME(3) NULL COMMENT '最近发货时间，采用系统统一时区，空值表示来源未提供或事件未发生',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统编码，用于同步幂等和数据血缘追踪',
    source_record_key VARCHAR(128) NOT NULL COMMENT '来源记录稳定唯一键，用于幂等写入和回溯',
    source_sync_time DATETIME(3) NULL COMMENT '来源记录最近一次成功同步到本系统的时间',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_receivable_tenant_row (tenant_id, id),
    UNIQUE KEY uk_contract_receivable_source (
        tenant_id, source_system, source_record_key
    ),
    KEY idx_contract_receivable_business (
        tenant_id, contract_no, company_code, mapping_status
    ),
    KEY idx_contract_receivable_contract (
        tenant_id, contract_id, source_sync_time
    ),
    KEY idx_contract_receivable_company (
        tenant_id, company_id, mapping_status, contract_id
    ),
    CONSTRAINT fk_contract_receivable_contract
        FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id),
    CONSTRAINT chk_contract_receivable_dates
        CHECK (
            source_effective_to IS NULL
            OR source_effective_from IS NULL
            OR source_effective_to >= source_effective_from
        ),
    CONSTRAINT chk_contract_receivable_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'SAP合同回款来源记录，保留公司待解析和一号多行证据';

CREATE TABLE com_shipment_contract_reference (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    contract_id BIGINT NULL COMMENT '关联合同记录的全局唯一ID',
    contract_no VARCHAR(64) NULL COMMENT '发货合同归属的合同编号',
    company_id BIGINT NULL COMMENT '发货合同归属对应的平台公司主档ID',
    company_code VARCHAR(64) NULL COMMENT '公司编码',
    company_name VARCHAR(255) NULL COMMENT '公司名称',
    office_department_id BIGINT NULL COMMENT '发货合同归属办事处对应的平台共享部门主档ID',
    office_department_code VARCHAR(64) NULL COMMENT '办事处部门编码',
    office_department_name VARCHAR(128) NULL COMMENT '办事处部门名称',
    contract_type VARCHAR(32) NULL COMMENT '合同类型编码，取值由对应业务字典约束',
    customer_name VARCHAR(512) NULL COMMENT '发货合同归属的客户名称',
    project_name VARCHAR(512) NULL COMMENT '发货合同归属的项目名称',
    marketing_department_id BIGINT NULL COMMENT '发货合同归属市场部门对应的平台共享部门主档ID',
    marketing_department_code VARCHAR(64) NULL COMMENT '市场部门编码',
    marketing_department_name VARCHAR(128) NULL COMMENT '市场部门名称',
    system_department_source_key VARCHAR(64) NULL COMMENT '发货合同来源中的系统部门原始ID或键，不冒充正式部门编码',
    system_department_id BIGINT NULL COMMENT '发货合同归属系统部门对应的平台共享部门主档ID',
    system_department_code VARCHAR(64) NULL COMMENT '解析后的系统部门编码',
    system_department_name VARCHAR(128) NULL COMMENT '解析后的系统部门名称',
    warranty_flag VARCHAR(8) NULL COMMENT '质保标志：0否，1是，空值表示来源未知',
    remark VARCHAR(4096) NULL COMMENT '发货合同归属的备注',
    mapping_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_MAPPING' COMMENT '跨系统关联解析状态，如待映射、已映射或存在冲突',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统编码，用于同步幂等和数据血缘追踪',
    source_record_key VARCHAR(128) NOT NULL COMMENT '来源记录稳定唯一键，用于幂等写入和回溯',
    source_sync_time DATETIME(3) NULL COMMENT '来源记录最近一次成功同步到本系统的时间',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipment_contract_ref_tenant_row (tenant_id, id),
    UNIQUE KEY uk_shipment_contract_ref_source (
        tenant_id, source_system, source_record_key
    ),
    KEY idx_shipment_contract_ref_no (
        tenant_id, contract_no, company_code, mapping_status
    ),
    KEY idx_shipment_contract_ref_contract (
        tenant_id, contract_id, mapping_status
    ),
    KEY idx_shipment_contract_ref_company (
        tenant_id, company_id, mapping_status, contract_id
    ),
    CONSTRAINT fk_shipment_contract_ref_contract
        FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id),
    CONSTRAINT chk_shipment_contract_ref_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '发货记录的合同归属，不作为合同主档';

CREATE TABLE com_shipment_package (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    shipment_contract_ref_id BIGINT NULL COMMENT '关联发货合同归属记录的全局唯一ID',
    package_no VARCHAR(128) NOT NULL COMMENT '发货装箱单的装箱单编号',
    shipment_time DATETIME(3) NULL COMMENT '发货时间，采用系统统一时区，空值表示来源未提供或事件未发生',
    warranty_start_time DATETIME(3) NULL COMMENT '质保开始时间，采用系统统一时区，空值表示来源未提供或事件未发生',
    warranty_end_time DATETIME(3) NULL COMMENT '质保结束时间，采用系统统一时区，空值表示来源未提供或事件未发生',
    receiver_name VARCHAR(512) NULL COMMENT '发货装箱单的收件人名称',
    express_no VARCHAR(512) NULL COMMENT '发货装箱单的快递编号',
    carrier_name VARCHAR(256) NULL COMMENT '发货装箱单的承运商名称，不关联平台公司主档',
    mapping_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_MAPPING' COMMENT '跨系统关联解析状态，如待映射、已映射或存在冲突',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统编码，用于同步幂等和数据血缘追踪',
    source_record_key VARCHAR(128) NOT NULL COMMENT '来源记录稳定唯一键，用于幂等写入和回溯',
    source_sync_time DATETIME(3) NULL COMMENT '来源记录最近一次成功同步到本系统的时间',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipment_package_tenant_row (tenant_id, id),
    UNIQUE KEY uk_shipment_package_source (
        tenant_id, source_system, source_record_key
    ),
    UNIQUE KEY uk_shipment_package_no (
        tenant_id, source_system, package_no
    ),
    KEY idx_shipment_package_contract_ref (
        tenant_id, shipment_contract_ref_id, shipment_time
    ),
    CONSTRAINT fk_shipment_package_contract_ref
        FOREIGN KEY (tenant_id, shipment_contract_ref_id) REFERENCES com_shipment_contract_reference (tenant_id, id),
    CONSTRAINT chk_shipment_package_warranty_dates
        CHECK (
            warranty_end_time IS NULL
            OR warranty_start_time IS NULL
            OR warranty_end_time >= warranty_start_time
        ),
    CONSTRAINT chk_shipment_package_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '发货装箱单主档';
