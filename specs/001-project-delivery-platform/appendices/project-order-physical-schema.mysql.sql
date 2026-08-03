-- 项目、合同、ERP订单行与设备SN物理表结构评审草案
-- Target: MySQL 8.x / InnoDB / utf8mb4
-- Status: REVIEW DRAFT, not a Flyway/Liquibase production migration.
-- Safety: additive CREATE TABLE statements only; no DROP/TRUNCATE/legacy database writes.
-- Verified: executed successfully in an isolated MySQL 8.4.10 Docker schema
--           on 2026-07-29; 27 tables, 39 foreign keys, 42 CHECK constraints.
-- Quantity: DECIMAL(18,4) is a lossless superset of the legacy INT fields.
--           The final scale remains subject to material unit confirmation.

SET NAMES utf8mb4;

CREATE TABLE pms_project (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(255) NOT NULL,
    parent_id BIGINT NULL,
    root_id BIGINT NOT NULL,
    tree_path VARCHAR(1024) NOT NULL,
    tree_depth INT UNSIGNED NOT NULL DEFAULT 0,
    tree_sort INT NOT NULL DEFAULT 0,
    customer_id BIGINT NULL,
    manager_id BIGINT NULL,
    org_id BIGINT NULL,
    project_type VARCHAR(32) NULL,
    lifecycle_template_id BIGINT NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    status VARCHAR(32) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_code (tenant_id, project_code),
    KEY idx_project_parent (tenant_id, parent_id, tree_sort, id),
    KEY idx_project_path (tenant_id, root_id, tree_path(191)),
    KEY idx_project_manager (tenant_id, manager_id, status),
    KEY idx_project_org (tenant_id, org_id, status),
    CONSTRAINT fk_project_parent FOREIGN KEY (parent_id) REFERENCES pms_project (id),
    CONSTRAINT chk_project_depth CHECK (tree_depth >= 0),
    CONSTRAINT chk_project_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目主档及非固定层级项目树';

CREATE TABLE pms_project_relation (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_project_id BIGINT NOT NULL,
    target_project_id BIGINT NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    effective_time DATETIME(3) NULL,
    reason VARCHAR(500) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_relation (
        tenant_id, source_project_id, target_project_id, relation_type
    ),
    KEY idx_project_relation_target (
        tenant_id, target_project_id, relation_type
    ),
    CONSTRAINT fk_project_rel_source
        FOREIGN KEY (source_project_id) REFERENCES pms_project (id),
    CONSTRAINT fk_project_rel_target
        FOREIGN KEY (target_project_id) REFERENCES pms_project (id),
    CONSTRAINT chk_project_relation_self
        CHECK (source_project_id <> target_project_id),
    CONSTRAINT chk_project_relation_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '扩容、续采、改造等非树项目关系';

CREATE TABLE pms_portfolio (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    portfolio_code VARCHAR(64) NOT NULL,
    portfolio_name VARCHAR(255) NOT NULL,
    owner_id BIGINT NULL,
    member_rule_type VARCHAR(32) NOT NULL DEFAULT 'STATIC',
    member_rule JSON NULL,
    valid_from DATETIME(3) NULL,
    valid_to DATETIME(3) NULL,
    status VARCHAR(32) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_portfolio_code (tenant_id, portfolio_code),
    KEY idx_portfolio_owner (tenant_id, owner_id, status),
    CONSTRAINT chk_portfolio_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目组合，不改变项目父子层级';

CREATE TABLE pms_portfolio_project_rel (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    portfolio_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    member_source VARCHAR(32) NOT NULL DEFAULT 'STATIC',
    effective_from DATETIME(3) NULL,
    effective_to DATETIME(3) NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_portfolio_project (
        tenant_id, portfolio_id, project_id, member_source
    ),
    KEY idx_portfolio_project_reverse (tenant_id, project_id, portfolio_id),
    CONSTRAINT fk_portfolio_project_portfolio
        FOREIGN KEY (portfolio_id) REFERENCES pms_portfolio (id),
    CONSTRAINT fk_portfolio_project_project
        FOREIGN KEY (project_id) REFERENCES pms_project (id),
    CONSTRAINT chk_portfolio_project_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目组合成员';

CREATE TABLE pms_contract (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    company_code VARCHAR(32) NOT NULL,
    contract_no VARCHAR(64) NOT NULL,
    master_source_system VARCHAR(32) NOT NULL,
    master_source_record_key VARCHAR(128) NULL,
    contract_type VARCHAR(32) NULL,
    customer_id BIGINT NULL,
    customer_code VARCHAR(64) NULL,
    customer_name VARCHAR(512) NULL,
    contract_name VARCHAR(512) NULL,
    currency_code VARCHAR(32) NULL,
    effective_date DATE NULL,
    expiry_date DATE NULL,
    source_payload JSON NULL,
    source_sync_time DATETIME(3) NULL,
    status VARCHAR(32) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_business (
        tenant_id, company_code, contract_no
    ),
    UNIQUE KEY uk_contract_master_source (
        tenant_id, master_source_system, master_source_record_key
    ),
    KEY idx_contract_no (tenant_id, contract_no, company_code),
    KEY idx_contract_customer (tenant_id, customer_id, status),
    CONSTRAINT chk_contract_dates
        CHECK (expiry_date IS NULL OR effective_date IS NULL OR expiry_date >= effective_date),
    CONSTRAINT chk_contract_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '合同主档，以所属公司和合同号为业务唯一键';

CREATE TABLE pms_contract_receivable (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    contract_no VARCHAR(64) NOT NULL,
    company_code VARCHAR(32) NULL,
    company_resolution_source VARCHAR(32) NULL,
    mapping_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_COMPANY',
    project_name VARCHAR(512) NULL,
    source_order_no VARCHAR(64) NULL,
    customer_code VARCHAR(64) NULL,
    customer_name VARCHAR(512) NULL,
    contract_amount DECIMAL(20, 2) NOT NULL,
    delivered_amount DECIMAL(20, 2) NOT NULL,
    collected_amount DECIMAL(20, 2) NOT NULL,
    collected_ratio DECIMAL(18, 6) NULL,
    receivable_amount DECIMAL(20, 2) NULL,
    overdue_amount DECIMAL(20, 2) NULL,
    currency_name VARCHAR(32) NULL,
    marketing_department_name VARCHAR(128) NULL,
    office_code VARCHAR(80) NULL,
    office_name VARCHAR(128) NULL,
    industry_name VARCHAR(128) NULL,
    marketing_representative_name VARCHAR(128) NULL,
    source_effective_from DATETIME(3) NULL,
    source_effective_to DATETIME(3) NULL,
    contract_create_time DATETIME(3) NULL,
    latest_ship_time DATETIME(3) NULL,
    source_system VARCHAR(32) NOT NULL,
    source_record_key VARCHAR(128) NOT NULL,
    source_payload JSON NULL,
    source_sync_time DATETIME(3) NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_receivable_source (
        tenant_id, source_system, source_record_key
    ),
    KEY idx_contract_receivable_business (
        tenant_id, contract_no, company_code, mapping_status
    ),
    KEY idx_contract_receivable_contract (
        tenant_id, contract_id, source_sync_time
    ),
    CONSTRAINT fk_contract_receivable_contract
        FOREIGN KEY (contract_id) REFERENCES pms_contract (id),
    CONSTRAINT chk_contract_receivable_dates
        CHECK (
            source_effective_to IS NULL
            OR source_effective_from IS NULL
            OR source_effective_to >= source_effective_from
        ),
    CONSTRAINT chk_contract_receivable_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'SAP合同回款来源记录，保留公司待解析和一号多行证据';

CREATE TABLE pms_shipment_contract_ref (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    contract_no VARCHAR(64) NULL,
    company_code VARCHAR(32) NULL,
    office_code VARCHAR(32) NULL,
    contract_type VARCHAR(32) NULL,
    customer_name VARCHAR(512) NULL,
    project_name VARCHAR(512) NULL,
    market_code VARCHAR(32) NULL,
    market_name VARCHAR(128) NULL,
    system_id BIGINT NULL,
    system_name VARCHAR(128) NULL,
    warranty_flag VARCHAR(8) NULL,
    remark VARCHAR(4096) NULL,
    mapping_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_MAPPING',
    source_system VARCHAR(32) NOT NULL,
    source_record_key VARCHAR(128) NOT NULL,
    source_payload JSON NULL,
    source_sync_time DATETIME(3) NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipment_contract_ref_source (
        tenant_id, source_system, source_record_key
    ),
    KEY idx_shipment_contract_ref_no (
        tenant_id, contract_no, company_code, mapping_status
    ),
    KEY idx_shipment_contract_ref_contract (
        tenant_id, contract_id, mapping_status
    ),
    CONSTRAINT fk_shipment_contract_ref_contract
        FOREIGN KEY (contract_id) REFERENCES pms_contract (id),
    CONSTRAINT chk_shipment_contract_ref_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '发货记录的合同归属，不作为合同主档';

CREATE TABLE pms_shipment_package (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    shipment_contract_ref_id BIGINT NULL,
    contract_id BIGINT NULL,
    package_no VARCHAR(128) NOT NULL,
    shipment_time DATETIME(3) NULL,
    warranty_start_time DATETIME(3) NULL,
    warranty_end_time DATETIME(3) NULL,
    receiver_name VARCHAR(512) NULL,
    express_no VARCHAR(512) NULL,
    express_company VARCHAR(256) NULL,
    mapping_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_MAPPING',
    source_system VARCHAR(32) NOT NULL,
    source_record_key VARCHAR(128) NOT NULL,
    source_payload JSON NULL,
    source_sync_time DATETIME(3) NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipment_package_source (
        tenant_id, source_system, source_record_key
    ),
    UNIQUE KEY uk_shipment_package_no (
        tenant_id, source_system, package_no
    ),
    KEY idx_shipment_package_contract (
        tenant_id, contract_id, shipment_time
    ),
    KEY idx_shipment_package_contract_ref (
        tenant_id, shipment_contract_ref_id, shipment_time
    ),
    CONSTRAINT fk_shipment_package_contract_ref
        FOREIGN KEY (shipment_contract_ref_id)
        REFERENCES pms_shipment_contract_ref (id),
    CONSTRAINT fk_shipment_package_contract
        FOREIGN KEY (contract_id) REFERENCES pms_contract (id),
    CONSTRAINT chk_shipment_package_warranty_dates
        CHECK (
            warranty_end_time IS NULL
            OR warranty_start_time IS NULL
            OR warranty_end_time >= warranty_start_time
        ),
    CONSTRAINT chk_shipment_package_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '发货装箱单主档';

CREATE TABLE pms_project_contract_rel (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    relation_role VARCHAR(32) NOT NULL DEFAULT 'RELATED',
    source_system VARCHAR(32) NOT NULL,
    effective_from DATETIME(3) NULL,
    effective_to DATETIME(3) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_contract (
        tenant_id, project_id, contract_id, relation_role
    ),
    KEY idx_project_contract_reverse (tenant_id, contract_id, project_id),
    CONSTRAINT fk_project_contract_project
        FOREIGN KEY (project_id) REFERENCES pms_project (id),
    CONSTRAINT fk_project_contract_contract
        FOREIGN KEY (contract_id) REFERENCES pms_contract (id),
    CONSTRAINT chk_project_contract_dates
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_project_contract_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目与合同直接N:N关系';

CREATE TABLE pms_sales_order (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_system VARCHAR(32) NOT NULL,
    company_code VARCHAR(32) NOT NULL,
    order_type VARCHAR(32) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    sales_type VARCHAR(32) NULL,
    order_create_time DATETIME(3) NULL,
    customer_required_time DATETIME(3) NULL,
    customer_code VARCHAR(64) NULL,
    customer_name VARCHAR(255) NULL,
    project_name VARCHAR(255) NULL,
    order_comment VARCHAR(2048) NULL,
    source_payload JSON NULL,
    source_sync_time DATETIME(3) NULL,
    status VARCHAR(32) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_order_business (
        tenant_id, source_system, company_code, order_type, order_no
    ),
    KEY idx_sales_order_no (tenant_id, order_no),
    KEY idx_sales_order_customer (tenant_id, customer_code, status),
    KEY idx_sales_order_time (tenant_id, order_create_time, status),
    CONSTRAINT chk_sales_order_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'ERP销售订单主档';

CREATE TABLE pms_order_contract_rel (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    relation_role VARCHAR(32) NOT NULL DEFAULT 'RELATED',
    source_record_key VARCHAR(128) NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_contract (tenant_id, order_id, contract_id),
    KEY idx_order_contract_reverse (tenant_id, contract_id, order_id),
    CONSTRAINT fk_order_contract_order
        FOREIGN KEY (order_id) REFERENCES pms_sales_order (id),
    CONSTRAINT fk_order_contract_contract
        FOREIGN KEY (contract_id) REFERENCES pms_contract (id),
    CONSTRAINT chk_order_contract_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '合同与ERP订单N:N关系';

CREATE TABLE pms_sales_order_line (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    line_no VARCHAR(32) NOT NULL,
    item_code VARCHAR(64) NULL,
    item_desc VARCHAR(512) NULL,
    order_qty DECIMAL(18, 4) NULL,
    open_qty DECIMAL(18, 4) NULL,
    delivered_qty DECIMAL(18, 4) NULL,
    bundle_code VARCHAR(64) NULL,
    warranty_month INT NULL,
    profit_center VARCHAR(64) NULL,
    real_execution_no VARCHAR(64) NULL,
    source_payload JSON NULL,
    source_sync_time DATETIME(3) NULL,
    status VARCHAR(32) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_order_line (tenant_id, order_id, line_no),
    KEY idx_sales_order_line_item (tenant_id, item_code),
    KEY idx_sales_order_line_profit (tenant_id, profit_center, order_id),
    CONSTRAINT fk_sales_order_line_order
        FOREIGN KEY (order_id) REFERENCES pms_sales_order (id),
    CONSTRAINT chk_sales_order_line_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'ERP销售订单行及数量快照';

CREATE TABLE pms_project_order_line_scope (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    order_line_id BIGINT NULL,
    allocated_qty DECIMAL(18, 4) NULL,
    scope_status VARCHAR(32) NOT NULL,
    allocation_source VARCHAR(32) NOT NULL,
    legacy_order_no VARCHAR(64) NULL,
    legacy_line_no VARCHAR(32) NULL,
    legacy_item_code VARCHAR(64) NULL,
    source_system VARCHAR(32) NOT NULL,
    source_table VARCHAR(64) NOT NULL,
    source_record_key VARCHAR(128) NOT NULL,
    effective_from DATETIME(3) NULL,
    effective_to DATETIME(3) NULL,
    change_reason VARCHAR(500) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    current_order_line_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN deleted = 0
             AND scope_status IN ('ACTIVE', 'PENDING_QUANTITY')
            THEN order_line_id
            ELSE NULL
        END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope_source (
        tenant_id, source_system, source_table, source_record_key
    ),
    UNIQUE KEY uk_scope_current (
        tenant_id, project_id, current_order_line_id
    ),
    KEY idx_scope_project (
        tenant_id, project_id, scope_status, order_line_id
    ),
    KEY idx_scope_order_line (
        tenant_id, order_line_id, scope_status, project_id
    ),
    KEY idx_scope_legacy (
        tenant_id, legacy_order_no, legacy_line_no
    ),
    CONSTRAINT fk_scope_project
        FOREIGN KEY (project_id) REFERENCES pms_project (id),
    CONSTRAINT fk_scope_order_line
        FOREIGN KEY (order_line_id) REFERENCES pms_sales_order_line (id),
    CONSTRAINT chk_scope_active
        CHECK (
            scope_status <> 'ACTIVE'
            OR (order_line_id IS NOT NULL AND allocated_qty IS NOT NULL)
        ),
    CONSTRAINT chk_scope_dates
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_scope_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目对ERP订单行的权威实施范围';

CREATE TABLE pms_device_sn (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    sn VARCHAR(100) NOT NULL,
    item_code VARCHAR(64) NULL,
    secondary_sn VARCHAR(100) NULL,
    asset_status VARCHAR(32) NOT NULL,
    source_system VARCHAR(32) NOT NULL,
    source_payload JSON NULL,
    status VARCHAR(32) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_sn (tenant_id, sn),
    KEY idx_device_item (tenant_id, item_code, asset_status),
    KEY idx_device_secondary_sn (tenant_id, secondary_sn),
    CONSTRAINT chk_device_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '设备SN主档，不承载重复发货事件';

CREATE TABLE pms_device_shipment_event (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    shipment_package_id BIGINT NULL,
    legacy_package_key VARCHAR(128) NULL,
    order_line_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL DEFAULT 'SHIPMENT_RECORD',
    business_action_code VARCHAR(32) NOT NULL DEFAULT 'UNCLASSIFIED',
    rma_no VARCHAR(128) NULL,
    shipment_time DATETIME(3) NULL,
    profit_center VARCHAR(64) NULL,
    mapping_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_MAPPING',
    source_system VARCHAR(32) NOT NULL,
    source_record_key VARCHAR(128) NOT NULL,
    source_payload JSON NULL,
    status VARCHAR(32) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    rma_marked TINYINT GENERATED ALWAYS AS (
        CASE
            WHEN rma_no IS NULL
              OR TRIM(rma_no) = ''
              OR LOWER(TRIM(rma_no)) = 'null'
            THEN 0
            ELSE 1
        END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipment_event_source (
        tenant_id, source_system, source_record_key
    ),
    KEY idx_shipment_device (tenant_id, device_id, shipment_time),
    KEY idx_shipment_package (tenant_id, shipment_package_id, device_id),
    KEY idx_shipment_order_line (tenant_id, order_line_id, shipment_time),
    KEY idx_shipment_rma (
        tenant_id, rma_marked, business_action_code, rma_no
    ),
    CONSTRAINT fk_shipment_device
        FOREIGN KEY (device_id) REFERENCES pms_device_sn (id),
    CONSTRAINT fk_shipment_package
        FOREIGN KEY (shipment_package_id) REFERENCES pms_shipment_package (id),
    CONSTRAINT fk_shipment_order_line
        FOREIGN KEY (order_line_id) REFERENCES pms_sales_order_line (id),
    CONSTRAINT chk_shipment_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '设备发货、退回、返还和再次发放的物流生命周期事件';

CREATE TABLE pms_project_device_assignment (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    project_order_line_scope_id BIGINT NULL,
    assignment_type VARCHAR(32) NOT NULL,
    assignment_status VARCHAR(32) NOT NULL,
    effective_from DATETIME(3) NULL,
    effective_to DATETIME(3) NULL,
    transfer_batch_id BIGINT NULL,
    source_system VARCHAR(32) NOT NULL,
    source_table VARCHAR(64) NOT NULL,
    source_record_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_assignment_source (
        tenant_id, source_system, source_table, source_record_key
    ),
    KEY idx_device_assignment_project (
        tenant_id, project_id, effective_to, device_id
    ),
    KEY idx_device_assignment_device (
        tenant_id, device_id, effective_to, project_id
    ),
    CONSTRAINT fk_device_assignment_project
        FOREIGN KEY (project_id) REFERENCES pms_project (id),
    CONSTRAINT fk_device_assignment_device
        FOREIGN KEY (device_id) REFERENCES pms_device_sn (id),
    CONSTRAINT fk_device_assignment_scope
        FOREIGN KEY (project_order_line_scope_id)
        REFERENCES pms_project_order_line_scope (id),
    CONSTRAINT chk_device_assignment_dates
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_device_assignment_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '设备SN到项目的归属及转移历史';

CREATE TABLE pms_device_relation (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_device_id BIGINT NOT NULL,
    target_device_id BIGINT NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    contract_id BIGINT NULL,
    effective_time DATETIME(3) NULL,
    source_system VARCHAR(32) NOT NULL,
    source_record_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_relation_source (
        tenant_id, source_system, source_record_key
    ),
    KEY idx_device_relation_source_device (
        tenant_id, source_device_id, relation_type
    ),
    KEY idx_device_relation_target_device (
        tenant_id, target_device_id, relation_type
    ),
    CONSTRAINT fk_device_relation_source
        FOREIGN KEY (source_device_id) REFERENCES pms_device_sn (id),
    CONSTRAINT fk_device_relation_target
        FOREIGN KEY (target_device_id) REFERENCES pms_device_sn (id),
    CONSTRAINT fk_device_relation_contract
        FOREIGN KEY (contract_id) REFERENCES pms_contract (id),
    CONSTRAINT chk_device_relation_self
        CHECK (source_device_id <> target_device_id),
    CONSTRAINT chk_device_relation_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '母子公司SN、RMA替换等设备关系';

CREATE TABLE pms_crm_execution_order (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_system VARCHAR(32) NOT NULL DEFAULT 'CRM',
    execution_no VARCHAR(64) NOT NULL,
    crm_project_code VARCHAR(64) NULL,
    crm_project_name VARCHAR(255) NULL,
    primary_project_id BIGINT NULL,
    af_evidence_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    source_payload JSON NULL,
    source_sync_time DATETIME(3) NULL,
    status VARCHAR(32) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_execution (
        tenant_id, source_system, execution_no
    ),
    KEY idx_crm_execution_project (
        tenant_id, primary_project_id, status
    ),
    CONSTRAINT fk_crm_execution_project
        FOREIGN KEY (primary_project_id) REFERENCES pms_project (id),
    CONSTRAINT chk_crm_execution_af
        CHECK (af_evidence_status IN ('CONFIRMED', 'UNKNOWN')),
    CONSTRAINT chk_crm_execution_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'CRM执行单辅助主档，安服仅保存正向证据';

CREATE TABLE pms_crm_execution_config (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    execution_id BIGINT NOT NULL,
    config_source VARCHAR(32) NOT NULL,
    source_config_key VARCHAR(128) NOT NULL,
    item_code VARCHAR(64) NULL,
    quantity DECIMAL(18, 4) NULL,
    amount DECIMAL(20, 4) NULL,
    is_af_evidence TINYINT NOT NULL DEFAULT 0,
    source_payload JSON NULL,
    source_sync_time DATETIME(3) NULL,
    status VARCHAR(32) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_execution_config (
        tenant_id, config_source, source_config_key
    ),
    KEY idx_crm_execution_config_execution (
        tenant_id, execution_id, item_code
    ),
    CONSTRAINT fk_crm_execution_config_execution
        FOREIGN KEY (execution_id) REFERENCES pms_crm_execution_order (id),
    CONSTRAINT chk_crm_execution_config_af CHECK (is_af_evidence IN (0, 1)),
    CONSTRAINT chk_crm_execution_config_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'CRM已获得的执行单产品配置，仅作辅助证据';

CREATE TABLE pms_order_execution_rel (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    order_line_id BIGINT NULL,
    execution_id BIGINT NOT NULL,
    relation_level VARCHAR(16) NOT NULL,
    is_primary TINYINT NOT NULL DEFAULT 0,
    relation_source VARCHAR(32) NOT NULL,
    mapping_status VARCHAR(32) NOT NULL,
    source_record_key VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_order_execution_order (
        tenant_id, order_id, execution_id
    ),
    KEY idx_order_execution_line (
        tenant_id, order_line_id, execution_id
    ),
    KEY idx_order_execution_execution (
        tenant_id, execution_id, relation_level
    ),
    CONSTRAINT fk_order_execution_order
        FOREIGN KEY (order_id) REFERENCES pms_sales_order (id),
    CONSTRAINT fk_order_execution_line
        FOREIGN KEY (order_line_id) REFERENCES pms_sales_order_line (id),
    CONSTRAINT fk_order_execution_execution
        FOREIGN KEY (execution_id) REFERENCES pms_crm_execution_order (id),
    CONSTRAINT chk_order_execution_target
        CHECK (order_id IS NOT NULL OR order_line_id IS NOT NULL),
    CONSTRAINT chk_order_execution_primary CHECK (is_primary IN (0, 1)),
    CONSTRAINT chk_order_execution_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '订单或订单行与CRM执行单的可空辅助关系';

CREATE TABLE pms_execution_merge_batch (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_system VARCHAR(32) NOT NULL,
    source_merge_key VARCHAR(128) NOT NULL,
    primary_execution_id BIGINT NULL,
    contract_id BIGINT NULL,
    source_payload JSON NULL,
    status VARCHAR(32) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_execution_merge_batch (
        tenant_id, source_system, source_merge_key
    ),
    KEY idx_execution_merge_primary (
        tenant_id, primary_execution_id, status
    ),
    CONSTRAINT fk_execution_merge_primary
        FOREIGN KEY (primary_execution_id) REFERENCES pms_crm_execution_order (id),
    CONSTRAINT fk_execution_merge_contract
        FOREIGN KEY (contract_id) REFERENCES pms_contract (id),
    CONSTRAINT chk_execution_merge_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '特殊业务合并下单批次';

CREATE TABLE pms_execution_merge_member (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    merge_batch_id BIGINT NOT NULL,
    execution_id BIGINT NULL,
    execution_no VARCHAR(64) NOT NULL,
    profit_center VARCHAR(64) NULL,
    source_order_code VARCHAR(128) NULL,
    member_sort INT NOT NULL DEFAULT 0,
    is_primary TINYINT NOT NULL DEFAULT 0,
    source_record_key VARCHAR(128) NOT NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_execution_merge_member_source (
        tenant_id, merge_batch_id, source_record_key
    ),
    KEY idx_execution_merge_member_execution (
        tenant_id, execution_id, merge_batch_id
    ),
    CONSTRAINT fk_execution_merge_member_batch
        FOREIGN KEY (merge_batch_id) REFERENCES pms_execution_merge_batch (id),
    CONSTRAINT fk_execution_merge_member_execution
        FOREIGN KEY (execution_id) REFERENCES pms_crm_execution_order (id),
    CONSTRAINT chk_execution_merge_member_primary CHECK (is_primary IN (0, 1)),
    CONSTRAINT chk_execution_merge_member_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '特殊合并下单执行单成员，不限制成员数量';

CREATE TABLE pms_order_change_rel (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_order_id BIGINT NOT NULL,
    target_order_id BIGINT NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    change_batch_no VARCHAR(64) NULL,
    reason VARCHAR(500) NULL,
    effective_time DATETIME(3) NULL,
    source_evidence JSON NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_change (
        tenant_id, source_order_id, target_order_id, relation_type
    ),
    KEY idx_order_change_target (
        tenant_id, target_order_id, relation_type
    ),
    CONSTRAINT fk_order_change_source
        FOREIGN KEY (source_order_id) REFERENCES pms_sales_order (id),
    CONSTRAINT fk_order_change_target
        FOREIGN KEY (target_order_id) REFERENCES pms_sales_order (id),
    CONSTRAINT chk_order_change_self
        CHECK (source_order_id <> target_order_id),
    CONSTRAINT chk_order_change_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '改单、拆分、替代和退货订单血缘';

CREATE TABLE pms_sync_batch (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    source_system VARCHAR(32) NOT NULL,
    object_type VARCHAR(64) NOT NULL,
    sync_mode VARCHAR(32) NOT NULL,
    started_time DATETIME(3) NOT NULL,
    finished_time DATETIME(3) NULL,
    read_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    success_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    failure_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    source_cursor VARCHAR(512) NULL,
    source_extract_location VARCHAR(1024) NULL,
    source_extract_checksum VARCHAR(128) NULL,
    error_summary VARCHAR(2048) NULL,
    status VARCHAR(32) NOT NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sync_batch_no (tenant_id, batch_no),
    KEY idx_sync_batch_object (
        tenant_id, source_system, object_type, started_time
    ),
    CONSTRAINT chk_sync_batch_time
        CHECK (finished_time IS NULL OR finished_time >= started_time),
    CONSTRAINT chk_sync_batch_count
        CHECK (success_count + failure_count <= read_count)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '一次性迁移及只读同步批次';

CREATE TABLE pms_external_key_map (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    batch_id BIGINT NULL,
    source_system VARCHAR(32) NOT NULL,
    source_table VARCHAR(64) NOT NULL,
    source_pk VARCHAR(128) NOT NULL,
    source_business_key VARCHAR(512) NULL,
    target_table VARCHAR(64) NOT NULL,
    target_id BIGINT NOT NULL,
    mapping_status VARCHAR(32) NOT NULL,
    source_checksum VARCHAR(128) NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_external_key_source (
        tenant_id, source_system, source_table, source_pk
    ),
    KEY idx_external_key_target (
        tenant_id, target_table, target_id
    ),
    KEY idx_external_key_batch (tenant_id, batch_id, mapping_status),
    CONSTRAINT fk_external_key_batch
        FOREIGN KEY (batch_id) REFERENCES pms_sync_batch (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '旧主键到新主键的可追溯映射';

CREATE TABLE pms_migration_issue (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    source_system VARCHAR(32) NOT NULL,
    source_table VARCHAR(64) NOT NULL,
    source_pk VARCHAR(128) NOT NULL,
    issue_type VARCHAR(64) NOT NULL,
    raw_business_key VARCHAR(512) NULL,
    candidate_target_ids JSON NULL,
    raw_payload JSON NULL,
    resolution_status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    resolution_action VARCHAR(2048) NULL,
    resolver VARCHAR(64) NULL,
    resolved_time DATETIME(3) NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_migration_issue_source (
        tenant_id, batch_id, source_table, source_pk, issue_type
    ),
    KEY idx_migration_issue_status (
        tenant_id, issue_type, resolution_status, create_time
    ),
    CONSTRAINT fk_migration_issue_batch
        FOREIGN KEY (batch_id) REFERENCES pms_sync_batch (id),
    CONSTRAINT chk_migration_issue_resolution
        CHECK (
            resolution_status <> 'RESOLVED'
            OR (resolver IS NOT NULL AND resolved_time IS NOT NULL)
        )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '迁移缺失、重复、多义映射和人工解决记录';

CREATE TABLE pms_project_delivery_summary (
    project_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    contract_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    order_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    order_line_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    active_scope_qty DECIMAL(20, 4) NOT NULL DEFAULT 0,
    erp_delivered_qty DECIMAL(20, 4) NOT NULL DEFAULT 0,
    device_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    pending_mapping_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    pending_quantity_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    statistic_time DATETIME(3) NOT NULL,
    source_batch_no VARCHAR(64) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (project_id),
    KEY idx_project_summary_status (
        tenant_id, pending_mapping_count, pending_quantity_count
    ),
    KEY idx_project_summary_time (tenant_id, statistic_time),
    CONSTRAINT fk_project_summary_project
        FOREIGN KEY (project_id) REFERENCES pms_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '可重建的项目合同、订单、发货和SN汇总读模型';
