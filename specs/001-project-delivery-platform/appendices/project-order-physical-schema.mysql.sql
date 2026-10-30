-- 项目、合同、ERP订单行与设备SN物理表结构评审草案
-- Target: MySQL 8.x / InnoDB / utf8mb4
-- Status: REVIEW DRAFT, not a Flyway/Liquibase production migration.
-- Safety: additive CREATE TABLE statements only; no DROP/TRUNCATE/legacy database writes.
-- Historical verification: the predecessor 52-table snapshot was executed in an
--           isolated MySQL 8.4.10 Docker schema on 2026-08-05.
-- Current gate: this core-migration-subset draft requires a new isolated execution after
--           AI-MIG-000 approval; historical verification cannot release it.
-- Quantity: DECIMAL(18,4) is a lossless superset of the legacy INT fields.
--           The final scale remains subject to material unit confirmation.

SET NAMES utf8mb4;

CREATE TABLE cus_customer (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    customer_code VARCHAR(64) NOT NULL COMMENT '客户编码',
    customer_name VARCHAR(255) NOT NULL COMMENT '客户名称',
    customer_address VARCHAR(1000) NULL COMMENT '客户地址',
    market_code VARCHAR(64) NULL COMMENT '市场部编码',
    market_name VARCHAR(255) NULL COMMENT '市场部名称',
    system_code VARCHAR(64) NULL COMMENT '系统部编码',
    system_name VARCHAR(255) NULL COMMENT '系统部名称',
    expend_code VARCHAR(64) NULL COMMENT '拓展部编码',
    expend_name VARCHAR(255) NULL COMMENT '拓展部名称',
    industry_code VARCHAR(64) NULL COMMENT '行业编码',
    industry_name VARCHAR(255) NULL COMMENT '行业名称',
    service_level_code VARCHAR(64) NULL COMMENT '客户默认服务等级编码',
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_tenant_row (tenant_id, id),
    UNIQUE KEY uk_customer_code (tenant_id, customer_code),
    KEY idx_customer_name (tenant_id, customer_name),
    KEY idx_customer_market_relation (
        tenant_id, market_code, system_code, expend_code, industry_code
    ),
    CONSTRAINT chk_customer_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '客户主档';

CREATE TABLE cus_market_relation (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    market_code VARCHAR(64) NOT NULL COMMENT '市场部编码',
    market_name VARCHAR(255) NULL COMMENT '市场部名称',
    system_code VARCHAR(64) NOT NULL COMMENT '系统部编码',
    system_name VARCHAR(255) NULL COMMENT '系统部名称',
    expend_code VARCHAR(64) NOT NULL COMMENT '拓展部编码',
    expend_name VARCHAR(255) NULL COMMENT '拓展部名称',
    industry_code VARCHAR(64) NOT NULL COMMENT '行业编码',
    industry_name VARCHAR(255) NULL COMMENT '行业名称',
    source_system VARCHAR(32) NOT NULL DEFAULT 'CRM' COMMENT '来源系统',
    source_record_key VARCHAR(128) NOT NULL COMMENT '稳定来源键',
    source_sync_time DATETIME(3) NULL COMMENT '来源同步时间',
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_market_relation_tenant_row (tenant_id, id),
    UNIQUE KEY uk_market_relation_source (
        tenant_id, source_system, source_record_key
    ),
    UNIQUE KEY uk_market_relation_business (
        tenant_id, market_code, system_code, expend_code, industry_code
    ),
    KEY idx_market_relation_name (
        tenant_id, market_name(64), system_name(64), expend_name(64), industry_name(64)
    ),
    CONSTRAINT chk_market_relation_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'CRM同步的客户市场行业划分组合目录';

CREATE TABLE cus_customer_contact (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    contact_name VARCHAR(255) NOT NULL COMMENT '联系人名称',
    phone VARCHAR(128) NULL COMMENT '电话',
    email VARCHAR(255) NULL COMMENT '邮箱',
    contact_address VARCHAR(1000) NULL COMMENT '联系人地址',
    customer_department_name VARCHAR(255) NULL COMMENT '客户内部门名称',
    position_name VARCHAR(255) NULL COMMENT '职位名称',
    is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '同一业务范围内是否为主记录：0否，1是',
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    primary_customer_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 AND is_primary = 1
             THEN customer_id ELSE NULL END
    ) STORED COMMENT '主客户ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_contact_tenant_row (tenant_id, id),
    KEY idx_customer_contact (tenant_id, customer_id, status, is_primary),
    UNIQUE KEY uk_customer_primary_contact (tenant_id, primary_customer_id),
    CONSTRAINT fk_customer_contact_customer
        FOREIGN KEY (tenant_id, customer_id) REFERENCES cus_customer (tenant_id, id),
    CONSTRAINT chk_customer_contact_primary CHECK (is_primary IN (0, 1)),
    CONSTRAINT chk_customer_contact_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '客户联系人';

CREATE TABLE ast_product (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    product_code VARCHAR(64) NOT NULL COMMENT '产品编码',
    product_name VARCHAR(255) NOT NULL COMMENT '产品名称',
    product_model VARCHAR(255) NULL COMMENT '产品型号',
    product_line_code VARCHAR(64) NULL COMMENT '产品行编码',
    product_category_code VARCHAR(64) NULL COMMENT '产品分类编码',
    product_type VARCHAR(32) NOT NULL DEFAULT 'DEVICE' COMMENT '产品类型编码',
    service_product_flag TINYINT NOT NULL DEFAULT 0 COMMENT '安服配置标志：0否，1是',
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_tenant_row (tenant_id, id),
    UNIQUE KEY uk_product_code (tenant_id, product_code),
    KEY idx_product_line (tenant_id, product_line_code, status),
    CONSTRAINT chk_product_service CHECK (service_product_flag IN (0, 1)),
    CONSTRAINT chk_product_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '产品主档，安服属性由产品配置判定';

CREATE TABLE proj_project (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_code VARCHAR(64) NOT NULL COMMENT '项目编码',
    code_root_id BIGINT NOT NULL COMMENT '创建时命名空间根项目ID',
    project_sequence INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '永久流水号：0表示独立命名空间',
    code_rule_version VARCHAR(32) NOT NULL COMMENT '项目编码生成规则版本',
    project_name VARCHAR(255) NULL COMMENT '项目名称',
    parent_id BIGINT NULL COMMENT '父记录ID',
    root_id BIGINT NOT NULL COMMENT '项目树根节点项目ID',
    tree_path VARCHAR(1024) NOT NULL COMMENT '项目祖先路径缓存',
    tree_depth INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '节点层级深度缓存：根节点为0',
    tree_sort INT NOT NULL DEFAULT 0 COMMENT '排序值',
    customer_id BIGINT NULL COMMENT '直接客户主档ID',
    customer_code VARCHAR(64) NULL COMMENT '客户编码',
    customer_name VARCHAR(255) NULL COMMENT '客户名称',
    manager_id BIGINT NULL COMMENT '主负责人用户ID',
    manager_employee_no VARCHAR(64) NULL COMMENT '负责人工号',
    manager_name VARCHAR(128) NULL COMMENT '负责人姓名',
    company_id BIGINT NULL COMMENT '主责公司ID',
    company_code VARCHAR(64) NULL COMMENT '公司编码',
    company_name VARCHAR(255) NULL COMMENT '公司名称',
    department_id BIGINT NULL COMMENT '主责部门ID',
    department_code VARCHAR(64) NULL COMMENT '部门编码',
    department_name VARCHAR(255) NULL COMMENT '部门名称',
    project_type VARCHAR(32) NOT NULL DEFAULT 'STANDARD' COMMENT '项目类型编码',
    market_code VARCHAR(64) NULL COMMENT '市场部编码',
    market_name VARCHAR(255) NULL COMMENT '市场部名称',
    system_code VARCHAR(64) NULL COMMENT '系统部编码',
    system_name VARCHAR(255) NULL COMMENT '系统部名称',
    expend_code VARCHAR(64) NULL COMMENT '拓展部编码',
    expend_name VARCHAR(255) NULL COMMENT '拓展部名称',
    industry_code VARCHAR(64) NULL COMMENT '行业编码',
    industry_name VARCHAR(255) NULL COMMENT '行业名称',
    customer_project_name VARCHAR(255) NULL COMMENT '客户项目名称',
    sales_type VARCHAR(32) NULL COMMENT '销售类型编码',
    business_type VARCHAR(32) NULL COMMENT '业务类型编码',
    project_category VARCHAR(32) NULL COMMENT '项目分类',
    implementation_mode VARCHAR(32) NULL COMMENT '实施模式编码',
    major_project_level VARCHAR(64) NULL COMMENT '重大项目级别',
    service_level_code VARCHAR(64) NULL COMMENT '服务级别编码',
    not_track_reason VARCHAR(1024) NULL COMMENT '不跟踪原因',
    project_start_time DATETIME(3) NULL COMMENT '项目开始时间',
    project_refresh_time DATETIME(3) NULL COMMENT '项目刷新时间',
    project_close_time DATETIME(3) NULL COMMENT '项目关闭时间',
    lifecycle_template_id BIGINT NULL COMMENT '生命周期模板ID',
    source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '项目创建来源类型',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_tenant_row (tenant_id, id),
    UNIQUE KEY uk_project_code (tenant_id, project_code),
    UNIQUE KEY uk_project_code_sequence (tenant_id, code_root_id, project_sequence),
    KEY idx_project_parent (tenant_id, parent_id, tree_sort, id),
    KEY idx_project_path (tenant_id, root_id, tree_path(191)),
    KEY idx_project_manager (tenant_id, manager_id, status),
    KEY idx_project_manager_employee (tenant_id, manager_employee_no, status, id),
    KEY idx_project_customer_code (tenant_id, customer_code, status, id),
    KEY idx_project_market_relation (
        tenant_id, market_code, system_code, expend_code, industry_code, status, id
    ),
    KEY idx_project_company_department_id (
        tenant_id, company_id, department_id, status, id
    ),
    KEY idx_project_company_department (
        tenant_id, company_code, department_code, status, id
    ),
    KEY idx_project_department_company (
        tenant_id, department_code, company_code, status, id
    ),
    CONSTRAINT fk_project_parent FOREIGN KEY (tenant_id, parent_id) REFERENCES proj_project (tenant_id, id),
    CONSTRAINT fk_project_code_root FOREIGN KEY (tenant_id, code_root_id) REFERENCES proj_project (tenant_id, id),
    CONSTRAINT chk_project_code_namespace CHECK (
        (project_sequence = 0 AND code_root_id = id)
        OR project_sequence > 0
    ),
    CONSTRAINT chk_project_depth CHECK (tree_depth >= 0),
    CONSTRAINT chk_project_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目主档及非固定层级项目树';

CREATE TABLE proj_project_relation (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    source_project_id BIGINT NOT NULL COMMENT '来源项目ID',
    target_project_id BIGINT NOT NULL COMMENT '目标项目ID',
    relation_type VARCHAR(32) NOT NULL COMMENT '关系类型编码',
    effective_time DATETIME(3) NULL COMMENT '生效时间',
    reason VARCHAR(500) NULL COMMENT '原因',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_relation_tenant_row (tenant_id, id),
    UNIQUE KEY uk_project_relation (
        tenant_id, source_project_id, target_project_id, relation_type
    ),
    KEY idx_project_relation_target (
        tenant_id, target_project_id, relation_type
    ),
    CONSTRAINT fk_project_rel_source
        FOREIGN KEY (tenant_id, source_project_id) REFERENCES proj_project (tenant_id, id),
    CONSTRAINT fk_project_rel_target
        FOREIGN KEY (tenant_id, target_project_id) REFERENCES proj_project (tenant_id, id),
    CONSTRAINT chk_project_relation_self
        CHECK (source_project_id <> target_project_id),
    CONSTRAINT chk_project_relation_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '扩容、续采、改造等非树项目关系';

CREATE TABLE proj_project_party (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    party_role VARCHAR(32) NOT NULL COMMENT '参与方角色编码',
    party_code VARCHAR(128) NULL COMMENT '参与方编码',
    party_name VARCHAR(1024) NULL COMMENT '参与方名称',
    contact_name VARCHAR(255) NULL COMMENT '联系人名称',
    phone VARCHAR(128) NULL COMMENT '联系人电话',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    source_table VARCHAR(64) NOT NULL COMMENT '来源系统物理表名',
    source_record_key VARCHAR(128) NOT NULL COMMENT '来源记录稳定唯一键',
    effective_from DATETIME(3) NULL COMMENT '生效开始时间',
    effective_to DATETIME(3) NULL COMMENT '生效结束时间',
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

CREATE TABLE proj_project_company_department_relation (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    company_id BIGINT NULL COMMENT '平台公司主档ID',
    company_code VARCHAR(64) NOT NULL COMMENT '公司编码',
    company_name VARCHAR(255) NULL COMMENT '公司名称',
    department_id BIGINT NULL COMMENT '部门ID',
    department_code VARCHAR(64) NULL COMMENT '部门编码',
    department_name VARCHAR(255) NULL COMMENT '部门名称',
    relation_role VARCHAR(32) NOT NULL COMMENT '业务角色',
    is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '同一业务范围内是否为主记录：0否，1是',
    effective_from DATETIME(3) NULL COMMENT '生效开始时间',
    effective_to DATETIME(3) NULL COMMENT '生效结束时间',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    primary_project_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 AND effective_to IS NULL AND is_primary = 1
             THEN project_id ELSE NULL END
    ) STORED COMMENT '主项目ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_company_department_rel_tenant_row (tenant_id, id),
    UNIQUE KEY uk_project_company_department_role (
        tenant_id, project_id, company_code, department_code, relation_role, effective_from
    ),
    KEY idx_project_company_department_id (
        tenant_id, company_id, department_id, status, project_id
    ),
    KEY idx_project_company_reverse (
        tenant_id, company_code, relation_role, status, project_id
    ),
    KEY idx_project_department_reverse (
        tenant_id, department_code, company_code, relation_role, status, project_id
    ),
    UNIQUE KEY uk_project_primary_company_department (
        tenant_id, primary_project_id, relation_role
    ),
    CONSTRAINT fk_project_company_department_project
        FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id),
    CONSTRAINT chk_project_company_department_primary CHECK (is_primary IN (0, 1)),
    CONSTRAINT chk_project_company_department_pair
        CHECK (department_id IS NULL OR department_code IS NOT NULL),
    CONSTRAINT chk_project_company_department_dates
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_project_company_department_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目业务角色下的公司与部门组合关系，保留配对但不建立全局主数据从属关系';

CREATE TABLE proj_project_member_assignment (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    employee_no VARCHAR(64) NULL COMMENT '成员工号',
    member_name VARCHAR(128) NULL COMMENT '成员姓名',
    company_id BIGINT NULL COMMENT '加入时公司ID',
    company_code VARCHAR(64) NULL COMMENT '公司编码',
    company_name VARCHAR(255) NULL COMMENT '公司名称',
    department_code VARCHAR(64) NULL COMMENT '部门编码',
    department_name VARCHAR(255) NULL COMMENT '部门名称',
    member_role VARCHAR(32) NOT NULL COMMENT '成员角色编码',
    responsibility VARCHAR(500) NULL COMMENT '职责',
    effective_from DATETIME(3) NULL COMMENT '生效开始时间',
    effective_to DATETIME(3) NULL COMMENT '生效结束时间',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_member_tenant_row (tenant_id, id),
    UNIQUE KEY uk_project_member_role (
        tenant_id, project_id, user_id, member_role, effective_from
    ),
    KEY idx_project_member_user (tenant_id, user_id, status, project_id),
    KEY idx_project_member_employee (tenant_id, employee_no, status, project_id),
    KEY idx_project_member_company_department (
        tenant_id, company_code, department_code, status, project_id
    ),
    CONSTRAINT fk_project_member_project
        FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id),
    CONSTRAINT chk_project_member_dates
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_project_member_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目成员、角色及有效期';

CREATE TABLE proj_project_template_task_definition (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    template_revision_id BIGINT NOT NULL COMMENT '项目模板发布版本逻辑引用',
    stage_definition_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    task_definition_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    parent_task_definition_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    name VARCHAR(255) NOT NULL COMMENT '任务定义名称',
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    work_binding_type_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    target_context_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    target_object_type VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    target_object_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    component_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    dynamic_form_revision_id BIGINT NULL,
    approval_definition_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    binding_config JSON NOT NULL COMMENT '受控绑定参数与Schema版本',
    permission_policy_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    completion_rule_type_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    completion_rule_config JSON NOT NULL COMMENT '完成规则配置与Schema版本',
    gate_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    definition_version INT UNSIGNED NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '草稿阶段乐观锁版本',
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater BIGINT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_template_task_definition_tenant_row (tenant_id, id),
    UNIQUE KEY uk_project_template_task_definition (
        tenant_id, template_revision_id, task_definition_key
    ),
    KEY idx_project_template_task_definition_tree (
        tenant_id, template_revision_id, stage_definition_key,
        parent_task_definition_key, sort_order
    ),
    CONSTRAINT chk_project_template_task_definition_version CHECK (definition_version > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目模板版本内的Stage-Task执行定义，发布后不可覆盖';

CREATE TABLE proj_project_task_execution_contract (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_task_id BIGINT NOT NULL COMMENT 'ProjectTask逻辑引用',
    template_task_definition_id BIGINT NULL COMMENT '来源模板任务定义逻辑引用',
    work_binding_type_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    target_context_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    target_object_type VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    target_object_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    component_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    dynamic_form_revision_id BIGINT NULL,
    approval_instance_id BIGINT NULL,
    binding_parameter_snapshot JSON NOT NULL COMMENT '冻结绑定参数与Schema版本',
    permission_policy_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    completion_rule_type_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    completion_rule_snapshot JSON NOT NULL COMMENT '冻结完成规则与Schema版本',
    gate_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    source_definition_version INT UNSIGNED NOT NULL,
    contract_version INT UNSIGNED NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    effective_to DATETIME(3) NULL,
    current_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN effective_to IS NULL THEN 1 ELSE NULL END
    ) STORED,
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '受控换绑乐观锁版本',
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater BIGINT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_task_execution_contract_tenant_row (tenant_id, id),
    UNIQUE KEY uk_project_task_execution_contract_version (
        tenant_id, project_task_id, contract_version
    ),
    UNIQUE KEY uk_project_task_execution_contract_current (
        tenant_id, project_task_id, current_marker
    ),
    KEY idx_project_task_execution_contract_target (
        tenant_id, target_context_code, target_object_type, target_object_key
    ),
    CONSTRAINT fk_project_task_execution_contract_definition
        FOREIGN KEY (tenant_id, template_task_definition_id)
        REFERENCES proj_project_template_task_definition (tenant_id, id),
    CONSTRAINT chk_project_task_execution_contract_version CHECK (
        source_definition_version > 0 AND contract_version > 0
    ),
    CONSTRAINT chk_project_task_execution_contract_dates CHECK (
        effective_to IS NULL OR effective_to >= effective_from
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'ProjectTask当前及历史WorkBinding、权限与完成规则冻结契约';

CREATE TABLE proj_project_task_completion_evaluation (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_task_id BIGINT NOT NULL COMMENT 'ProjectTask逻辑引用',
    execution_contract_id BIGINT NOT NULL COMMENT '执行契约版本逻辑引用',
    task_version INT UNSIGNED NOT NULL,
    contract_version INT UNSIGNED NOT NULL,
    fact_context_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    fact_object_type VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    fact_object_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    fact_version VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    evaluation_result_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    unmet_item_snapshot JSON NULL,
    gate_snapshot_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    command_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    evaluated_by BIGINT NOT NULL,
    evaluated_at DATETIME(3) NOT NULL,
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_task_completion_evaluation_tenant_row (tenant_id, id),
    UNIQUE KEY uk_project_task_completion_evaluation_idempotency (
        tenant_id, project_task_id, idempotency_key
    ),
    KEY idx_project_task_completion_evaluation_time (
        tenant_id, project_task_id, evaluated_at, id
    ),
    CONSTRAINT fk_project_task_completion_evaluation_contract
        FOREIGN KEY (tenant_id, execution_contract_id)
        REFERENCES proj_project_task_execution_contract (tenant_id, id),
    CONSTRAINT chk_project_task_completion_evaluation_version CHECK (
        task_version > 0 AND contract_version > 0
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'ProjectTask完成规则对绑定事实的不可覆盖判定记录';

CREATE TABLE plt_business_document (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    document_code VARCHAR(64) NOT NULL COMMENT '文档编码',
    document_name VARCHAR(255) NOT NULL COMMENT '文档名称',
    document_type VARCHAR(64) NOT NULL COMMENT '文档类型编码',
    current_version_id BIGINT NULL COMMENT '当前有效版本ID缓存',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_business_document_tenant_row (tenant_id, id),
    UNIQUE KEY uk_business_document_code (tenant_id, document_code),
    CONSTRAINT chk_business_document_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '业务文档元数据';

CREATE TABLE plt_document_version (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    version_no VARCHAR(32) NOT NULL COMMENT '版本编号',
    file_id BIGINT NOT NULL COMMENT '文件ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名称',
    file_checksum VARCHAR(128) NULL COMMENT '文件校验值',
    uploaded_by BIGINT NULL COMMENT '上传人用户ID',
    uploaded_time DATETIME(3) NOT NULL COMMENT '上传时间',
    status VARCHAR(32) NOT NULL DEFAULT 'VALID' COMMENT '状态',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_version_tenant_row (tenant_id, id),
    UNIQUE KEY uk_document_version (tenant_id, document_id, version_no),
    UNIQUE KEY uk_document_version_owner (tenant_id, document_id, id),
    KEY idx_document_file (tenant_id, file_id),
    CONSTRAINT fk_document_version_document
        FOREIGN KEY (tenant_id, document_id) REFERENCES plt_business_document (tenant_id, id),
    CONSTRAINT chk_document_version_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '业务文档不可变版本';

ALTER TABLE plt_business_document
    ADD CONSTRAINT fk_business_document_current_version
    FOREIGN KEY (tenant_id, id, current_version_id)
    REFERENCES plt_document_version (tenant_id, document_id, id);

CREATE TABLE acc_deliverable_template (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    template_code VARCHAR(64) NOT NULL COMMENT '模板编码',
    deliverable_type VARCHAR(64) NOT NULL COMMENT '交付件类型编码',
    template_document_id BIGINT NULL COMMENT '模板文档ID',
    applicable_stage VARCHAR(32) NULL COMMENT '适用阶段',
    required_flag TINYINT NOT NULL DEFAULT 0 COMMENT '要求标志：0否，1是',
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_deliverable_template_tenant_row (tenant_id, id),
    UNIQUE KEY uk_deliverable_template (tenant_id, template_code),
    CONSTRAINT chk_deliverable_template_required CHECK (required_flag IN (0, 1)),
    CONSTRAINT chk_deliverable_template_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '交付件类型和模板配置';

CREATE TABLE acc_project_deliverable (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    template_id BIGINT NULL COMMENT '模板ID',
    deliverable_type VARCHAR(64) NOT NULL COMMENT '交付件类型编码',
    document_id BIGINT NULL COMMENT '文档ID',
    planned_due_date DATE NULL COMMENT '计划到期日期',
    submit_time DATETIME(3) NULL COMMENT '提交时间',
    accepted_time DATETIME(3) NULL COMMENT '验收时间',
    owner_id BIGINT NULL COMMENT '责任人ID',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_deliverable_tenant_row (tenant_id, id),
    KEY idx_project_deliverable (tenant_id, project_id, deliverable_type, status),
    KEY idx_deliverable_owner (tenant_id, owner_id, status, planned_due_date),
    CONSTRAINT fk_project_deliverable_template
        FOREIGN KEY (tenant_id, template_id) REFERENCES acc_deliverable_template (tenant_id, id),
    CONSTRAINT chk_project_deliverable_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目交付件实例及完成状态';

CREATE TABLE proj_project_portfolio (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    portfolio_code VARCHAR(64) NOT NULL COMMENT '项目组合编码',
    portfolio_name VARCHAR(255) NOT NULL COMMENT '项目组合名称',
    owner_id BIGINT NULL COMMENT '责任人ID',
    member_rule_type VARCHAR(32) NOT NULL DEFAULT 'STATIC' COMMENT '成员规则类型编码',
    member_rule JSON NULL COMMENT '动态成员筛选规则JSON',
    valid_from DATETIME(3) NULL COMMENT '有效开始时间',
    valid_to DATETIME(3) NULL COMMENT '有效结束时间',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_portfolio_tenant_row (tenant_id, id),
    UNIQUE KEY uk_portfolio_code (tenant_id, portfolio_code),
    KEY idx_portfolio_owner (tenant_id, owner_id, status),
    CONSTRAINT chk_portfolio_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目组合，不改变项目父子层级';

CREATE TABLE proj_project_portfolio_member (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    portfolio_id BIGINT NOT NULL COMMENT '项目组合ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    member_source VARCHAR(32) NOT NULL DEFAULT 'STATIC' COMMENT '成员来源',
    effective_from DATETIME(3) NULL COMMENT '生效开始时间',
    effective_to DATETIME(3) NULL COMMENT '生效结束时间',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_portfolio_project_rel_tenant_row (tenant_id, id),
    UNIQUE KEY uk_portfolio_project (
        tenant_id, portfolio_id, project_id, member_source
    ),
    KEY idx_portfolio_project_reverse (tenant_id, project_id, portfolio_id),
    CONSTRAINT fk_portfolio_project_portfolio
        FOREIGN KEY (tenant_id, portfolio_id) REFERENCES proj_project_portfolio (tenant_id, id),
    CONSTRAINT fk_portfolio_project_project
        FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id),
    CONSTRAINT chk_portfolio_project_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目组合成员';

CREATE TABLE com_contract (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    company_id BIGINT NULL COMMENT '签约公司ID',
    company_code VARCHAR(64) NOT NULL COMMENT '签约公司编码',
    company_name VARCHAR(255) NULL COMMENT '签约公司名称',
    contract_no VARCHAR(64) NOT NULL COMMENT '合同编号',
    master_source_system VARCHAR(32) NOT NULL COMMENT '主档来源系统',
    master_source_record_key VARCHAR(128) NULL COMMENT '主档来源记录键',
    contract_type VARCHAR(32) NULL COMMENT '合同类型编码',
    customer_id BIGINT NULL COMMENT '客户ID',
    customer_code VARCHAR(64) NULL COMMENT '客户编码',
    customer_name VARCHAR(512) NULL COMMENT '客户名称',
    contract_name VARCHAR(512) NULL COMMENT '合同名称',
    currency_code VARCHAR(32) NULL COMMENT '币种编码',
    currency_name VARCHAR(32) NULL COMMENT '币种名称',
    contract_create_time DATETIME(3) NULL COMMENT '合同创建时间',
    effective_date DATE NULL COMMENT '生效日期',
    expiry_date DATE NULL COMMENT '失效日期',
    project_name VARCHAR(512) NULL COMMENT '项目名称',
    project_code VARCHAR(80) NULL COMMENT '项目编码',
    market_code VARCHAR(64) NULL COMMENT '市场部编码',
    market_name VARCHAR(128) NULL COMMENT '市场部名称',
    department_code VARCHAR(64) NULL COMMENT '办事处编码',
    department_name VARCHAR(128) NULL COMMENT '办事处名称',
    system_code VARCHAR(64) NULL COMMENT '系统部编码',
    system_name VARCHAR(255) NULL COMMENT '系统部名称',
    expend_code VARCHAR(64) NULL COMMENT '拓展部编码',
    expend_name VARCHAR(255) NULL COMMENT '拓展部名称',
    industry_code VARCHAR(64) NULL COMMENT '行业编码',
    industry_name VARCHAR(128) NULL COMMENT '行业名称',
    marketing_representative_code VARCHAR(64) NULL COMMENT '市场代表编码',
    marketing_representative_name VARCHAR(128) NULL COMMENT '市场代表名称',
    secondary_representative_code VARCHAR(64) NULL COMMENT '辅助代表编码',
    source_sync_time DATETIME(3) NULL COMMENT '来源同步时间',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_tenant_row (tenant_id, id),
    UNIQUE KEY uk_contract_business (
        tenant_id, company_code, contract_no
    ),
    UNIQUE KEY uk_contract_master_source (
        tenant_id, master_source_system, master_source_record_key
    ),
    KEY idx_contract_no (tenant_id, contract_no, company_code),
    KEY idx_contract_company (tenant_id, company_id, status, contract_no),
    KEY idx_contract_customer (tenant_id, customer_id, status),
    CONSTRAINT chk_contract_dates
        CHECK (expiry_date IS NULL OR effective_date IS NULL OR expiry_date >= effective_date),
    CONSTRAINT chk_contract_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '合同主档，以所属公司和合同号为业务唯一键';

CREATE TABLE com_contract_receivable (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    contract_id BIGINT NULL COMMENT '合同ID',
    contract_no VARCHAR(64) NOT NULL COMMENT '合同编号',
    company_id BIGINT NULL COMMENT '公司ID',
    company_code VARCHAR(64) NULL COMMENT '公司编码',
    company_name VARCHAR(255) NULL COMMENT '公司名称',
    company_resolution_source VARCHAR(32) NULL COMMENT '公司解析来源',
    mapping_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_COMPANY' COMMENT '映射状态',
    project_name VARCHAR(512) NULL COMMENT '项目名称',
    source_order_no VARCHAR(64) NULL COMMENT '来源订单编号',
    customer_code VARCHAR(64) NULL COMMENT '客户编码',
    customer_name VARCHAR(512) NULL COMMENT '客户名称',
    contract_amount DECIMAL(20, 2) NOT NULL COMMENT '合同金额',
    delivered_amount DECIMAL(20, 2) NOT NULL COMMENT '已发货金额',
    collected_amount DECIMAL(20, 2) NOT NULL COMMENT '已收金额',
    collected_ratio DECIMAL(18, 6) NULL COMMENT '已收比例',
    receivable_amount DECIMAL(20, 2) NULL COMMENT '应收金额',
    overdue_amount DECIMAL(20, 2) NULL COMMENT '逾期金额',
    currency_name VARCHAR(32) NULL COMMENT '币种名称',
    market_code VARCHAR(64) NULL COMMENT '市场部编码',
    market_name VARCHAR(128) NULL COMMENT '市场部名称',
    department_id BIGINT NULL COMMENT '办事处ID',
    department_code VARCHAR(64) NULL COMMENT '办事处编码',
    department_name VARCHAR(128) NULL COMMENT '办事处名称',
    industry_name VARCHAR(128) NULL COMMENT '行业名称',
    marketing_representative_name VARCHAR(128) NULL COMMENT '市场代表名称',
    source_batch_code VARCHAR(32) NULL COMMENT '来源批次编码',
    import_batch_no VARCHAR(64) NULL COMMENT '导入批次编号',
    project_code VARCHAR(80) NULL COMMENT '项目编码',
    system_source_key VARCHAR(64) NULL COMMENT '系统部来源键',
    system_code VARCHAR(64) NULL COMMENT '系统部编码',
    system_name VARCHAR(255) NULL COMMENT '系统部名称',
    industry_code VARCHAR(64) NULL COMMENT '行业编码',
    expend_source_key VARCHAR(64) NULL COMMENT '拓展部来源键',
    expend_code VARCHAR(64) NULL COMMENT '拓展部编码',
    expend_name VARCHAR(255) NULL COMMENT '拓展部名称',
    marketing_representative_code VARCHAR(64) NULL COMMENT '市场代表编码',
    secondary_representative_code VARCHAR(64) NULL COMMENT '辅助代表编码',
    original_division_values JSON NULL COMMENT '创建时行业划分原值JSON',
    source_effective_from DATETIME(3) NULL COMMENT '来源生效开始时间',
    source_effective_to DATETIME(3) NULL COMMENT '来源生效结束时间',
    contract_create_time DATETIME(3) NULL COMMENT '合同创建时间',
    latest_ship_time DATETIME(3) NULL COMMENT '最近发货时间',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    source_record_key VARCHAR(128) NOT NULL COMMENT '来源记录稳定唯一键',
    source_sync_time DATETIME(3) NULL COMMENT '来源同步时间',
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
    contract_id BIGINT NULL COMMENT '合同ID',
    contract_no VARCHAR(64) NULL COMMENT '合同编号',
    company_id BIGINT NULL COMMENT '公司ID',
    company_code VARCHAR(64) NULL COMMENT '公司编码',
    company_name VARCHAR(255) NULL COMMENT '公司名称',
    department_id BIGINT NULL COMMENT '办事处ID',
    department_code VARCHAR(64) NULL COMMENT '办事处编码',
    department_name VARCHAR(128) NULL COMMENT '办事处名称',
    contract_type VARCHAR(32) NULL COMMENT '合同类型编码',
    customer_name VARCHAR(512) NULL COMMENT '客户名称',
    project_name VARCHAR(512) NULL COMMENT '项目名称',
    market_code VARCHAR(64) NULL COMMENT '市场部编码',
    market_name VARCHAR(128) NULL COMMENT '市场部名称',
    system_source_key VARCHAR(64) NULL COMMENT '系统部来源键',
    system_code VARCHAR(64) NULL COMMENT '系统部编码',
    system_name VARCHAR(128) NULL COMMENT '系统部名称',
    warranty_flag VARCHAR(8) NULL COMMENT '质保标志：0否，1是',
    remark VARCHAR(4096) NULL COMMENT '备注',
    mapping_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_MAPPING' COMMENT '映射状态',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    source_record_key VARCHAR(128) NOT NULL COMMENT '来源记录稳定唯一键',
    source_sync_time DATETIME(3) NULL COMMENT '来源同步时间',
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
    shipment_contract_ref_id BIGINT NULL COMMENT '发货合同归属ID',
    package_no VARCHAR(128) NOT NULL COMMENT '装箱单编号',
    shipment_time DATETIME(3) NULL COMMENT '发货时间',
    warranty_start_time DATETIME(3) NULL COMMENT '质保开始时间',
    warranty_end_time DATETIME(3) NULL COMMENT '质保结束时间',
    receiver_name VARCHAR(512) NULL COMMENT '收件人名称',
    express_no VARCHAR(512) NULL COMMENT '快递编号',
    carrier_name VARCHAR(256) NULL COMMENT '承运商名称',
    mapping_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_MAPPING' COMMENT '映射状态',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    source_record_key VARCHAR(128) NOT NULL COMMENT '来源记录稳定唯一键',
    source_sync_time DATETIME(3) NULL COMMENT '来源同步时间',
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

CREATE TABLE com_project_contract_relation (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    relation_role VARCHAR(32) NOT NULL DEFAULT 'RELATED' COMMENT '关系角色编码',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    source_table VARCHAR(64) NULL COMMENT '来源系统物理表名',
    source_record_key VARCHAR(128) NULL COMMENT '来源记录稳定唯一键',
    effective_from DATETIME(3) NULL COMMENT '生效开始时间',
    effective_to DATETIME(3) NULL COMMENT '生效结束时间',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_contract_rel_tenant_row (tenant_id, id),
    UNIQUE KEY uk_project_contract (
        tenant_id, project_id, contract_id, relation_role
    ),
    KEY idx_project_contract_reverse (tenant_id, contract_id, project_id),
    CONSTRAINT fk_project_contract_contract
        FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id),
    CONSTRAINT chk_project_contract_dates
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_project_contract_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目与合同直接N:N关系';

CREATE TABLE com_sales_order (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    company_id BIGINT NULL COMMENT '公司ID',
    company_code VARCHAR(64) NOT NULL COMMENT '公司编码',
    company_name VARCHAR(255) NULL COMMENT '公司名称',
    order_type VARCHAR(32) NOT NULL COMMENT '订单类型编码',
    order_no VARCHAR(64) NOT NULL COMMENT '订单编号',
    sales_type VARCHAR(32) NULL COMMENT '销售类型编码',
    order_create_time DATETIME(3) NULL COMMENT '订单创建时间',
    customer_required_time DATETIME(3) NULL COMMENT '客户要求时间',
    customer_id BIGINT NULL COMMENT '客户ID',
    customer_code VARCHAR(64) NULL COMMENT '客户编码',
    customer_name VARCHAR(512) NULL COMMENT '客户名称',
    source_project_name VARCHAR(512) NULL COMMENT '来源项目名称',
    order_comment VARCHAR(2048) NULL COMMENT '订单说明',
    source_sync_time DATETIME(3) NULL COMMENT '来源同步时间',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_order_tenant_row (tenant_id, id),
    UNIQUE KEY uk_sales_order_business (
        tenant_id, source_system, company_code, order_type, order_no
    ),
    KEY idx_sales_order_no (tenant_id, order_no),
    KEY idx_sales_order_company (tenant_id, company_id, status, order_no),
    KEY idx_sales_order_customer (tenant_id, customer_code, status),
    KEY idx_sales_order_time (tenant_id, order_create_time, status),
    CONSTRAINT chk_sales_order_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'ERP销售订单主档';

CREATE TABLE com_order_contract_relation (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    relation_role VARCHAR(32) NOT NULL DEFAULT 'RELATED' COMMENT '关系角色编码',
    relation_source VARCHAR(32) NOT NULL COMMENT '关系来源',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_contract_rel_tenant_row (tenant_id, id),
    UNIQUE KEY uk_order_contract (tenant_id, order_id, contract_id),
    KEY idx_order_contract_reverse (tenant_id, contract_id, order_id),
    CONSTRAINT fk_order_contract_order
        FOREIGN KEY (tenant_id, order_id) REFERENCES com_sales_order (tenant_id, id),
    CONSTRAINT fk_order_contract_contract
        FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id),
    CONSTRAINT chk_order_contract_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '合同与ERP订单N:N关系';

CREATE TABLE com_sales_order_line (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    source_system VARCHAR(32) NOT NULL COMMENT '父订单来源系统',
    company_id BIGINT NULL COMMENT '父订单所属公司主档ID',
    company_code VARCHAR(64) NOT NULL COMMENT '公司编码',
    company_name VARCHAR(255) NULL COMMENT '公司名称',
    order_type VARCHAR(32) NOT NULL COMMENT '父订单类型编码',
    order_no VARCHAR(64) NOT NULL COMMENT '父订单编号',
    line_no VARCHAR(32) NOT NULL COMMENT '行编号',
    line_type VARCHAR(32) NULL COMMENT '行类型编码',
    customer_id BIGINT NULL COMMENT '父订单客户主档ID',
    customer_code VARCHAR(64) NULL COMMENT '客户编码',
    customer_name VARCHAR(512) NULL COMMENT '客户名称',
    product_id BIGINT NULL COMMENT '产品ID',
    item_code VARCHAR(64) NULL COMMENT '物料编码',
    item_desc VARCHAR(512) NULL COMMENT '物料描述',
    order_qty DECIMAL(18, 4) NULL COMMENT 'ERP订单行下单数量',
    open_qty DECIMAL(18, 4) NULL COMMENT 'ERP订单行当前未执行数量',
    delivered_qty DECIMAL(18, 4) NULL COMMENT 'ERP订单行当前累计发货数量',
    bundle_code VARCHAR(64) NULL COMMENT '套件编码',
    warranty_month INT NULL COMMENT '质保期限月数',
    profit_center VARCHAR(64) NULL COMMENT '利润中心',
    real_execution_no VARCHAR(64) NULL COMMENT '实际执行单编号',
    source_sync_time DATETIME(3) NULL COMMENT '来源同步时间',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_order_line_tenant_row (tenant_id, id),
    UNIQUE KEY uk_sales_order_line (tenant_id, order_id, line_no),
    KEY idx_sales_order_line_business (
        tenant_id, source_system, company_code, order_type, order_no, line_no
    ),
    KEY idx_sales_order_line_customer (tenant_id, customer_code, status, id),
    KEY idx_sales_order_line_item (tenant_id, item_code),
    KEY idx_sales_order_line_profit (tenant_id, profit_center, order_id),
    CONSTRAINT fk_sales_order_line_order
        FOREIGN KEY (tenant_id, order_id) REFERENCES com_sales_order (tenant_id, id),
    CONSTRAINT chk_sales_order_line_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'ERP销售订单行及数量快照';

CREATE TABLE com_delivery_scope (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    order_line_id BIGINT NOT NULL COMMENT '订单行ID',
    project_code VARCHAR(64) NOT NULL COMMENT '项目编码',
    project_name VARCHAR(255) NULL COMMENT '项目名称',
    project_customer_code VARCHAR(64) NULL COMMENT '项目客户编码',
    project_customer_name VARCHAR(255) NULL COMMENT '项目客户名称',
    project_company_code VARCHAR(64) NULL COMMENT '项目公司编码',
    project_company_name VARCHAR(255) NULL COMMENT '项目公司名称',
    project_department_code VARCHAR(64) NULL COMMENT '项目部门编码',
    project_department_name VARCHAR(255) NULL COMMENT '项目部门名称',
    project_manager_employee_no VARCHAR(64) NULL COMMENT '项目负责人工号',
    project_manager_name VARCHAR(128) NULL COMMENT '项目负责人姓名',
    order_source_system VARCHAR(32) NOT NULL COMMENT '订单来源系统编码',
    order_company_code VARCHAR(64) NOT NULL COMMENT '订单公司编码',
    order_company_name VARCHAR(255) NULL COMMENT '订单公司名称',
    order_type VARCHAR(32) NOT NULL COMMENT '订单类型编码',
    order_no VARCHAR(64) NOT NULL COMMENT '订单编号',
    line_no VARCHAR(32) NOT NULL COMMENT '订单行号',
    item_code VARCHAR(64) NULL COMMENT '物料编码',
    item_desc VARCHAR(512) NULL COMMENT '物料描述',
    allocated_qty DECIMAL(18, 4) NULL COMMENT '实施数量',
    scope_status VARCHAR(32) NOT NULL COMMENT '实施范围状态',
    allocation_source VARCHAR(32) NOT NULL COMMENT '分配来源',
    effective_from DATETIME(3) NULL COMMENT '生效开始时间',
    effective_to DATETIME(3) NULL COMMENT '生效结束时间',
    change_reason VARCHAR(500) NULL COMMENT '变更原因',
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    current_order_line_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN deleted = 0
             AND effective_to IS NULL
            THEN order_line_id
            ELSE NULL
        END
    ) STORED COMMENT '当前订单行ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_order_line_scope_tenant_row (tenant_id, id),
    UNIQUE KEY uk_scope_current (
        tenant_id, project_id, current_order_line_id
    ),
    KEY idx_scope_project (
        tenant_id, project_id, scope_status, order_line_id
    ),
    KEY idx_scope_order_line (
        tenant_id, order_line_id, scope_status, project_id
    ),
    KEY idx_scope_project_customer (
        tenant_id, project_customer_code, scope_status, project_id
    ),
    KEY idx_scope_project_company (
        tenant_id, project_company_code, scope_status, project_id
    ),
    KEY idx_scope_project_department (
        tenant_id, project_department_code, scope_status, project_id
    ),
    KEY idx_scope_order_business (
        tenant_id, order_source_system, order_company_code, order_type, order_no, line_no
    ),
    KEY idx_scope_item (tenant_id, item_code, scope_status, project_id),
    CONSTRAINT fk_scope_order_line
        FOREIGN KEY (tenant_id, order_line_id) REFERENCES com_sales_order_line (tenant_id, id),
    CONSTRAINT chk_scope_dates
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_scope_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目对ERP订单行的权威实施范围';

CREATE TABLE com_delivery_scope_detail (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    delivery_scope_id BIGINT NOT NULL COMMENT '交付范围主ID',
    detail_sequence INT UNSIGNED NOT NULL COMMENT '交付范围明细序号',
    product_code VARCHAR(64) NULL COMMENT '产品编码',
    product_name VARCHAR(255) NULL COMMENT '产品名称快照',
    device_type_code VARCHAR(64) NULL COMMENT '设备类型编码',
    device_type_name VARCHAR(255) NULL COMMENT '设备类型名称快照',
    allocated_qty DECIMAL(18, 4) NOT NULL COMMENT '分配数量',
    implementation_location VARCHAR(500) NOT NULL COMMENT '实施地点',
    delivery_batch_no VARCHAR(64) NULL COMMENT '交付批次编号',
    source_record_key VARCHAR(128) COLLATE utf8mb4_0900_bin NULL COMMENT '明细来源记录稳定键',
    remark VARCHAR(500) NULL COMMENT '交付范围明细备注',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_scope_detail_tenant_row (tenant_id, id),
    UNIQUE KEY uk_delivery_scope_detail_sequence (
        tenant_id, delivery_scope_id, detail_sequence
    ),
    KEY idx_delivery_scope_detail_product (
        tenant_id, product_code, device_type_code, delivery_scope_id
    ),
    KEY idx_delivery_scope_detail_location (
        tenant_id, implementation_location, delivery_scope_id
    ),
    CONSTRAINT fk_delivery_scope_detail_scope
        FOREIGN KEY (tenant_id, delivery_scope_id) REFERENCES com_delivery_scope (tenant_id, id),
    CONSTRAINT chk_delivery_scope_detail_subject
        CHECK (product_code IS NOT NULL OR device_type_code IS NOT NULL),
    CONSTRAINT chk_delivery_scope_detail_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '交付范围按地点、产品或设备类型及批次拆分的明细';

CREATE TABLE ast_device_sn (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    sn VARCHAR(100) NOT NULL COMMENT '序列号',
    product_id BIGINT NULL COMMENT '产品ID',
    item_code VARCHAR(64) NULL COMMENT '物料编码',
    internal_serial_no VARCHAR(100) NULL COMMENT '内部序列号',
    secondary_sn VARCHAR(100) NULL COMMENT '当前附加SN缓存',
    secondary_item VARCHAR(64) NULL COMMENT '当前附加SN对应物料编码缓存',
    hardware_customized TINYINT NULL COMMENT '定制硬件标志：0否，1是',
    warranty_status VARCHAR(32) NULL COMMENT '质保状态',
    software_maintenance_status VARCHAR(32) NULL COMMENT '软件维保状态',
    asset_status VARCHAR(32) NOT NULL COMMENT '资产状态',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    source_sync_time DATETIME(3) NULL COMMENT '来源同步时间',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_sn_tenant_row (tenant_id, id),
    UNIQUE KEY uk_device_sn (tenant_id, sn),
    KEY idx_device_item (tenant_id, item_code, asset_status),
    KEY idx_device_secondary_sn (tenant_id, secondary_sn),
    KEY idx_device_internal_serial_no (tenant_id, internal_serial_no),
    CONSTRAINT chk_device_secondary_cache CHECK (
        secondary_sn IS NOT NULL OR secondary_item IS NULL
    ),
    CONSTRAINT chk_device_secondary_self CHECK (
        secondary_sn IS NULL OR secondary_sn <> sn
    ),
    CONSTRAINT chk_device_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '设备SN主档，不承载重复发货事件';

CREATE TABLE ast_device_shipment_event (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    shipment_package_id BIGINT NULL COMMENT '发货装箱单ID',
    legacy_package_key VARCHAR(128) NULL COMMENT '旧系统装箱单键',
    order_line_id BIGINT NULL COMMENT '订单行ID',
    event_type VARCHAR(32) NOT NULL DEFAULT 'SHIPMENT_RECORD' COMMENT '事件类型编码',
    business_action_code VARCHAR(32) NOT NULL DEFAULT 'UNCLASSIFIED' COMMENT '业务行为编码',
    rma_no VARCHAR(128) NULL COMMENT 'RMA编号',
    rma_related_sn VARCHAR(100) NULL COMMENT 'RMA关联序列号',
    shipment_time DATETIME(3) NULL COMMENT '发货时间',
    warranty_start_date DATE NULL COMMENT '质保开始日期',
    warranty_month INT NULL COMMENT '质保期限月数',
    mapping_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_MAPPING' COMMENT '映射状态',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    source_record_key VARCHAR(128) NOT NULL COMMENT '来源记录稳定唯一键',
    source_sync_time DATETIME(3) NULL COMMENT '来源同步时间',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    rma_marked TINYINT GENERATED ALWAYS AS (
        CASE
            WHEN rma_no IS NULL
              OR TRIM(rma_no) = ''
              OR LOWER(TRIM(rma_no)) = 'null'
            THEN 0
            ELSE 1
        END
    ) STORED COMMENT 'RMA事件标志：0非RMA类，1RMA或借转类',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_shipment_event_tenant_row (tenant_id, id),
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
        FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id),
    CONSTRAINT chk_shipment_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '设备发货、退回、返还和再次发放的物流生命周期事件';

CREATE TABLE ast_device_project_assignment (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    project_order_line_scope_id BIGINT NULL COMMENT '项目订单行实施范围ID',
    project_code VARCHAR(64) NOT NULL COMMENT '项目编码',
    project_name VARCHAR(255) NULL COMMENT '项目名称',
    project_customer_code VARCHAR(64) NULL COMMENT '项目客户编码',
    project_customer_name VARCHAR(255) NULL COMMENT '项目客户名称',
    project_company_code VARCHAR(64) NULL COMMENT '项目公司编码',
    project_company_name VARCHAR(255) NULL COMMENT '项目公司名称',
    project_department_code VARCHAR(64) NULL COMMENT '项目部门编码',
    project_department_name VARCHAR(255) NULL COMMENT '项目部门名称',
    device_sn VARCHAR(100) NOT NULL COMMENT '设备SN',
    item_code VARCHAR(64) NULL COMMENT '物料编码',
    order_no VARCHAR(64) NULL COMMENT '实施订单编号',
    line_no VARCHAR(32) NULL COMMENT '实施订单行号',
    install_address TEXT NULL COMMENT '安装地址',
    assignment_type VARCHAR(32) NOT NULL COMMENT '归属类型编码',
    assignment_status VARCHAR(32) NOT NULL COMMENT '归属状态',
    effective_from DATETIME(3) NULL COMMENT '生效开始时间',
    effective_to DATETIME(3) NULL COMMENT '生效结束时间',
    transfer_batch_id BIGINT NULL COMMENT '转移批次ID',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    source_record_key VARCHAR(128) NOT NULL COMMENT '来源记录稳定唯一键',
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    current_device_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 AND effective_to IS NULL
             THEN device_id ELSE NULL END
    ) STORED COMMENT '当前设备ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_device_assignment_tenant_row (tenant_id, id),
    UNIQUE KEY uk_device_assignment_source (
        tenant_id, source_system, source_record_key
    ),
    KEY idx_device_assignment_project (
        tenant_id, project_id, effective_to, device_id
    ),
    KEY idx_device_assignment_device (
        tenant_id, device_id, effective_to, project_id
    ),
    KEY idx_device_assignment_project_code (
        tenant_id, project_code, effective_to, device_id
    ),
    KEY idx_device_assignment_company_department (
        tenant_id, project_company_code, project_department_code, effective_to, project_id
    ),
    KEY idx_device_assignment_customer (
        tenant_id, project_customer_code, effective_to, project_id
    ),
    KEY idx_device_assignment_sn (
        tenant_id, device_sn, effective_to, project_id
    ),
    KEY idx_device_assignment_order (
        tenant_id, order_no, line_no, effective_to, project_id
    ),
    UNIQUE KEY uk_device_current_assignment (tenant_id, current_device_id),
    CONSTRAINT fk_device_assignment_device
        FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id),
    CONSTRAINT chk_device_assignment_dates
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_device_assignment_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '设备SN到项目的归属及转移历史';

CREATE TABLE ast_device_relation (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    source_device_id BIGINT NOT NULL COMMENT '来源设备ID',
    target_device_id BIGINT NOT NULL COMMENT '目标设备ID',
    relation_type VARCHAR(32) NOT NULL COMMENT '关系类型编码',
    contract_id BIGINT NULL COMMENT '合同ID',
    effective_time DATETIME(3) NULL COMMENT '生效时间',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    source_record_key VARCHAR(128) NOT NULL COMMENT '来源记录稳定唯一键',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_relation_tenant_row (tenant_id, id),
    UNIQUE KEY uk_device_relation_source (
        tenant_id, source_system, source_record_key
    ),
    KEY idx_device_relation_source_device (
        tenant_id, source_device_id, relation_type
    ),
    KEY idx_device_relation_target_device (
        tenant_id, target_device_id, relation_type
    ),
    KEY idx_device_relation_latest (
        tenant_id, source_device_id, contract_id,
        relation_type, status, effective_time, id
    ),
    KEY idx_device_relation_contract_refresh (
        tenant_id, contract_id, relation_type, status, source_device_id
    ),
    CONSTRAINT fk_device_relation_source
        FOREIGN KEY (tenant_id, source_device_id) REFERENCES ast_device_sn (tenant_id, id),
    CONSTRAINT fk_device_relation_target
        FOREIGN KEY (tenant_id, target_device_id) REFERENCES ast_device_sn (tenant_id, id),
    CONSTRAINT chk_device_relation_self
        CHECK (source_device_id <> target_device_id),
    CONSTRAINT chk_device_relation_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '合同维度主附加SN、RMA替换等设备关系';

CREATE TABLE ast_device_configuration (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    project_id BIGINT NULL COMMENT '项目ID',
    configuration_stage VARCHAR(32) NOT NULL COMMENT '配置阶段',
    deployment_mode VARCHAR(64) NULL COMMENT '部署模式编码',
    management_address VARCHAR(255) NULL COMMENT '管理地址',
    install_location VARCHAR(1000) NULL COMMENT '安装位置',
    effective_from DATETIME(3) NOT NULL COMMENT '生效开始时间',
    effective_to DATETIME(3) NULL COMMENT '生效结束时间',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_configuration_tenant_row (tenant_id, id),
    KEY idx_device_configuration (tenant_id, device_id, status, effective_from),
    KEY idx_project_configuration (tenant_id, project_id, configuration_stage),
    CONSTRAINT fk_device_configuration_device
        FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id),
    CONSTRAINT chk_device_configuration_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_device_configuration_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '设备分阶段配置主记录';

CREATE TABLE ast_device_configuration_feature (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    configuration_id BIGINT NOT NULL COMMENT '配置ID',
    feature_code VARCHAR(128) NOT NULL COMMENT '功能特性编码',
    feature_name VARCHAR(255) NULL COMMENT '功能特性名称',
    feature_value VARCHAR(1000) NULL COMMENT '功能特性取值',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_configuration_feature_tenant_row (tenant_id, id),
    UNIQUE KEY uk_device_configuration_feature (
        tenant_id, configuration_id, feature_code
    ),
    CONSTRAINT fk_configuration_feature_configuration
        FOREIGN KEY (tenant_id, configuration_id) REFERENCES ast_device_configuration (tenant_id, id),
    CONSTRAINT chk_configuration_feature_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '设备配置启用特性明细';

CREATE TABLE ast_device_configuration_service (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    configuration_id BIGINT NOT NULL COMMENT '配置ID',
    service_code VARCHAR(128) NOT NULL COMMENT '服务编码',
    service_name VARCHAR(255) NULL COMMENT '服务名称',
    service_endpoint VARCHAR(1000) NULL COMMENT '服务访问端点',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_configuration_service_tenant_row (tenant_id, id),
    UNIQUE KEY uk_device_configuration_service (
        tenant_id, configuration_id, service_code
    ),
    CONSTRAINT fk_configuration_service_configuration
        FOREIGN KEY (tenant_id, configuration_id) REFERENCES ast_device_configuration (tenant_id, id),
    CONSTRAINT chk_configuration_service_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '设备配置运行服务明细';

CREATE TABLE ast_network_topology (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    topology_name VARCHAR(255) NOT NULL COMMENT '拓扑名称',
    document_id BIGINT NULL COMMENT '文档ID',
    effective_from DATETIME(3) NOT NULL COMMENT '生效开始时间',
    effective_to DATETIME(3) NULL COMMENT '生效结束时间',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_network_topology_tenant_row (tenant_id, id),
    KEY idx_network_topology_project (tenant_id, project_id, status),
    CONSTRAINT chk_network_topology_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_network_topology_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目网络拓扑版本';

CREATE TABLE ast_network_topology_device_relation (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    topology_id BIGINT NOT NULL COMMENT '拓扑ID',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    node_code VARCHAR(128) NULL COMMENT '节点编码',
    node_role VARCHAR(64) NULL COMMENT '节点角色编码',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_topology_device_rel_tenant_row (tenant_id, id),
    UNIQUE KEY uk_topology_device (tenant_id, topology_id, device_id),
    KEY idx_topology_device_reverse (tenant_id, device_id, topology_id),
    CONSTRAINT fk_topology_device_topology
        FOREIGN KEY (tenant_id, topology_id) REFERENCES ast_network_topology (tenant_id, id),
    CONSTRAINT fk_topology_device_device
        FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id),
    CONSTRAINT chk_topology_device_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '拓扑节点与设备关系';

CREATE TABLE ast_device_version (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    project_id BIGINT NULL COMMENT '项目ID',
    version_stage VARCHAR(32) NOT NULL COMMENT '版本阶段',
    component_type VARCHAR(32) NOT NULL COMMENT '组件类型编码',
    component_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '组件名称',
    version_value VARCHAR(255) NOT NULL COMMENT '版本取值',
    customized_flag TINYINT NULL COMMENT '定制标志：0否，1是',
    collected_time DATETIME(3) NULL COMMENT '已收时间',
    effective_from DATETIME(3) NOT NULL COMMENT '生效开始时间',
    effective_to DATETIME(3) NULL COMMENT '生效结束时间',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_version_tenant_row (tenant_id, id),
    KEY idx_device_version_current (
        tenant_id, device_id, component_type, status, effective_from
    ),
    KEY idx_project_device_version (tenant_id, project_id, version_stage),
    CONSTRAINT fk_device_version_device
        FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id),
    CONSTRAINT chk_device_version_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_device_version_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '设备组件版本及阶段历史';

CREATE TABLE ast_product_release (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    release_version VARCHAR(255) NOT NULL COMMENT '发布版本',
    release_type VARCHAR(32) NOT NULL COMMENT '发布类型编码',
    release_date DATE NULL COMMENT '发布日期',
    end_of_support_date DATE NULL COMMENT '支持日期',
    document_id BIGINT NULL COMMENT '文档ID',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_release_tenant_row (tenant_id, id),
    UNIQUE KEY uk_product_release (
        tenant_id, product_id, release_version, release_type
    ),
    CONSTRAINT fk_product_release_product
        FOREIGN KEY (tenant_id, product_id) REFERENCES ast_product (tenant_id, id),
    CONSTRAINT chk_product_release_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '产品版本发布与支持周期';

CREATE TABLE srv_service_incident (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    incident_no VARCHAR(64) NOT NULL COMMENT '故障事件编号',
    project_id BIGINT NULL COMMENT '项目ID',
    incident_title VARCHAR(500) NOT NULL COMMENT '故障事件标题',
    incident_type VARCHAR(32) NULL COMMENT '故障事件类型编码',
    severity VARCHAR(32) NULL COMMENT '严重级别',
    occurred_time DATETIME(3) NULL COMMENT '发生时间',
    reported_time DATETIME(3) NULL COMMENT '受理时间',
    restored_time DATETIME(3) NULL COMMENT '恢复时间',
    closed_time DATETIME(3) NULL COMMENT '关闭时间',
    symptom TEXT NULL COMMENT '问题现象',
    root_cause TEXT NULL COMMENT '根原因',
    solution TEXT NULL COMMENT '解析',
    report_document_id BIGINT NULL COMMENT '报告文档ID',
    owner_id BIGINT NULL COMMENT '责任人ID',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_incident_tenant_row (tenant_id, id),
    UNIQUE KEY uk_service_incident_no (tenant_id, incident_no),
    KEY idx_incident_project (tenant_id, project_id, status, occurred_time),
    KEY idx_incident_owner (tenant_id, owner_id, status),
    CONSTRAINT chk_service_incident_times CHECK (
        restored_time IS NULL OR occurred_time IS NULL OR restored_time >= occurred_time
    ),
    CONSTRAINT chk_service_incident_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '故障及服务事件主档';

CREATE TABLE srv_service_incident_device_relation (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    incident_id BIGINT NOT NULL COMMENT '故障事件ID',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    impact_description VARCHAR(1000) NULL COMMENT '影响描述',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_incident_device_rel_tenant_row (tenant_id, id),
    UNIQUE KEY uk_incident_device (tenant_id, incident_id, device_id),
    KEY idx_incident_device_reverse (tenant_id, device_id, incident_id),
    CONSTRAINT fk_incident_device_incident
        FOREIGN KEY (tenant_id, incident_id) REFERENCES srv_service_incident (tenant_id, id),
    CONSTRAINT chk_incident_device_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '故障与受影响设备多对多关系';

CREATE TABLE com_crm_execution_order (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    source_system VARCHAR(32) NOT NULL DEFAULT 'CRM' COMMENT '来源系统',
    execution_no VARCHAR(255) NOT NULL COMMENT '执行单编号',
    project_code VARCHAR(255) NULL COMMENT '项目编码',
    project_name VARCHAR(1024) NULL COMMENT '项目名称',
    primary_project_id BIGINT NULL COMMENT '主项目ID',
    sales_rep_code VARCHAR(64) NULL COMMENT '销售代表编码',
    sales_rep_name VARCHAR(128) NULL COMMENT '销售代表名称',
    sales_rep_phone VARCHAR(300) NULL COMMENT '销售代表电话',
    market_code VARCHAR(64) NULL COMMENT '市场部编码',
    market_name VARCHAR(255) NULL COMMENT '市场部名称',
    system_source_key VARCHAR(64) NULL COMMENT '系统部来源键',
    system_code VARCHAR(64) NULL COMMENT '系统部编码',
    system_name VARCHAR(255) NULL COMMENT '系统部名称',
    expend_source_key VARCHAR(64) NULL COMMENT '拓展部来源键',
    expend_code VARCHAR(64) NULL COMMENT '拓展部编码',
    expend_name VARCHAR(255) NULL COMMENT '拓展部名称',
    industry_code VARCHAR(64) NULL COMMENT '行业编码',
    industry_name VARCHAR(255) NULL COMMENT '行业名称',
    department_id BIGINT NULL COMMENT '办事处ID',
    department_code VARCHAR(64) NULL COMMENT '办事处编码',
    department_name VARCHAR(128) NULL COMMENT '办事处名称',
    service_type_name VARCHAR(128) NULL COMMENT '服务类型名称',
    channel_name VARCHAR(1024) NULL COMMENT '渠道名称',
    engineering_fee_raw VARCHAR(64) NULL COMMENT '工程费用原始',
    engineering_fee DECIMAL(20, 4) NULL COMMENT '工程费用',
    source_object_id VARCHAR(64) NULL COMMENT '来源对象ID',
    apply_type VARCHAR(64) NULL COMMENT '申请类型编码',
    company_id BIGINT NULL COMMENT '公司ID',
    company_code VARCHAR(64) NULL COMMENT '公司编码',
    company_name VARCHAR(255) NULL COMMENT '公司名称',
    customer_project_name VARCHAR(1024) NULL COMMENT '客户项目名称',
    final_customer_name VARCHAR(1024) NULL COMMENT '最终客户名称',
    agent_name VARCHAR(1024) NULL COMMENT '代理商名称',
    project_manager_code VARCHAR(1024) NULL COMMENT '项目负责人编码',
    project_manager_name VARCHAR(512) NULL COMMENT '项目负责人名称',
    decision_path VARCHAR(1024) NULL COMMENT '决策路径',
    required_in_date DATE NULL COMMENT '要求入场日期',
    receiver_name VARCHAR(512) NULL COMMENT '收件人名称',
    receiver_contact VARCHAR(300) NULL COMMENT '收件人联系人',
    receiver_address VARCHAR(1024) NULL COMMENT '收件人地址',
    loan_reason TEXT NULL COMMENT '借用原因',
    project_type VARCHAR(32) NULL COMMENT '项目类型编码',
    major_project_level VARCHAR(255) NULL COMMENT '重大项目级别',
    project_amount DECIMAL(20, 4) NULL COMMENT '项目金额',
    af_project_amount DECIMAL(20, 4) NULL COMMENT '安服项目金额',
    submit_time DATETIME(3) NULL COMMENT '提交时间',
    predicted_bid_time DATETIME(3) NULL COMMENT '预计中标时间',
    contact_name VARCHAR(255) NULL COMMENT '联系人名称',
    contact_phone VARCHAR(128) NULL COMMENT '联系人电话',
    af_evidence_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '安服证据状态：CONFIRMED已确认，UNKNOWN未知',
    source_sync_time DATETIME(3) NULL COMMENT '来源同步时间',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_execution_order_tenant_row (tenant_id, id),
    UNIQUE KEY uk_crm_execution (
        tenant_id, source_system, execution_no
    ),
    KEY idx_crm_execution_project (
        tenant_id, primary_project_id, status
    ),
    KEY idx_crm_execution_source_project (
        tenant_id, project_code, execution_no
    ),
    KEY idx_crm_execution_company_office (
        tenant_id, company_id, department_id, status, id
    ),
    KEY idx_crm_execution_company_office_code (
        tenant_id, company_code, department_code, status, id
    ),
    CONSTRAINT chk_crm_execution_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'CRM执行单辅助主档，安服仅保存正向证据';

CREATE TABLE com_crm_execution_config (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    execution_id BIGINT NOT NULL COMMENT '执行单ID',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    source_config_key VARCHAR(128) NOT NULL COMMENT '来源配置键',
    project_code VARCHAR(255) NULL COMMENT '项目编码',
    company_id BIGINT NULL COMMENT '所属公司主档ID',
    company_code VARCHAR(64) NULL COMMENT '公司编码',
    company_name VARCHAR(255) NULL COMMENT '公司名称',
    settlement_id VARCHAR(64) NULL COMMENT '结算ID',
    product_code VARCHAR(255) NULL COMMENT '产品编码',
    product_first_code VARCHAR(255) NULL COMMENT '产品首次编码',
    product_first_name VARCHAR(255) NULL COMMENT '产品首次名称',
    product_name VARCHAR(255) NULL COMMENT '产品名称',
    item_code VARCHAR(255) NULL COMMENT '物料编码',
    item_model VARCHAR(255) NULL COMMENT '物料型号',
    item_name VARCHAR(512) NULL COMMENT '物料名称',
    qty DECIMAL(18, 4) NULL COMMENT '数量',
    borrow_qty DECIMAL(18, 4) NULL COMMENT '借用数量',
    unit_price DECIMAL(20, 6) NULL COMMENT '单价价格',
    purchase_discount DECIMAL(20, 6) NULL COMMENT '采购折扣',
    purchase_price DECIMAL(29, 2) NULL COMMENT '采购价格',
    line_type VARCHAR(32) NULL COMMENT '行类型编码',
    memo MEDIUMTEXT NULL COMMENT '备注',
    amount DECIMAL(20, 4) NULL COMMENT '金额',
    is_af_evidence TINYINT NOT NULL DEFAULT 0 COMMENT '安服配置标志：0否，1是',
    source_sync_time DATETIME(3) NULL COMMENT '来源同步时间',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_execution_config_tenant_row (tenant_id, id),
    UNIQUE KEY uk_crm_execution_config (
        tenant_id, source_system, source_config_key
    ),
    KEY idx_crm_execution_config_company (
        tenant_id, company_code, status, execution_id
    ),
    KEY idx_crm_execution_config_execution (
        tenant_id, execution_id, item_code
    ),
    CONSTRAINT fk_crm_execution_config_execution
        FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id),
    CONSTRAINT chk_crm_execution_config_af CHECK (is_af_evidence IN (0, 1)),
    CONSTRAINT chk_crm_execution_config_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'CRM已获得的执行单产品配置，仅作辅助证据';

CREATE TABLE com_order_execution_relation (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    execution_id BIGINT NOT NULL COMMENT '执行单ID',
    is_primary TINYINT NOT NULL DEFAULT 1 COMMENT '主执行单标志：0否，1是',
    relation_source VARCHAR(32) NOT NULL COMMENT '关系来源',
    mapping_status VARCHAR(32) NOT NULL COMMENT '映射状态',
    source_record_key VARCHAR(128) NULL COMMENT '来源记录稳定唯一键',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_execution_rel_tenant_row (tenant_id, id),
    UNIQUE KEY uk_order_execution (tenant_id, order_id, execution_id),
    KEY idx_order_execution_execution (
        tenant_id, execution_id, order_id
    ),
    CONSTRAINT fk_order_execution_order
        FOREIGN KEY (tenant_id, order_id) REFERENCES com_sales_order (tenant_id, id),
    CONSTRAINT fk_order_execution_execution
        FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id),
    CONSTRAINT chk_order_execution_primary CHECK (is_primary IN (0, 1)),
    CONSTRAINT chk_order_execution_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'ERP订单与CRM执行单辅助关系';

CREATE TABLE com_order_line_execution_relation (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    order_line_id BIGINT NOT NULL COMMENT '订单行ID',
    execution_id BIGINT NOT NULL COMMENT '执行单ID',
    relation_source VARCHAR(32) NOT NULL COMMENT '关系来源',
    mapping_status VARCHAR(32) NOT NULL COMMENT '映射状态',
    source_record_key VARCHAR(128) NULL COMMENT '来源记录稳定唯一键',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_line_execution_rel_tenant_row (tenant_id, id),
    UNIQUE KEY uk_order_line_execution (tenant_id, order_line_id, execution_id),
    KEY idx_order_line_execution_reverse (tenant_id, execution_id, order_line_id),
    CONSTRAINT fk_order_line_execution_line
        FOREIGN KEY (tenant_id, order_line_id) REFERENCES com_sales_order_line (tenant_id, id),
    CONSTRAINT fk_order_line_execution_execution
        FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id),
    CONSTRAINT chk_order_line_execution_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'ERP订单行与CRM执行单辅助关系';

CREATE TABLE com_execution_order_merge_batch (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    source_merge_key VARCHAR(128) NOT NULL COMMENT '来源合并键',
    primary_execution_id BIGINT NULL COMMENT '主执行单ID',
    contract_id BIGINT NULL COMMENT '合同ID',
    legacy_contract_no VARCHAR(64) NULL COMMENT '旧系统合同编号',
    project_name VARCHAR(512) NULL COMMENT '项目名称',
    agent_name VARCHAR(255) NULL COMMENT '代理商名称',
    source_order_codes VARCHAR(2048) NULL COMMENT '来源订单编码集合',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_execution_merge_batch_tenant_row (tenant_id, id),
    UNIQUE KEY uk_execution_merge_batch (
        tenant_id, source_system, source_merge_key
    ),
    KEY idx_execution_merge_primary (
        tenant_id, primary_execution_id, status
    ),
    CONSTRAINT fk_execution_merge_primary
        FOREIGN KEY (tenant_id, primary_execution_id) REFERENCES com_crm_execution_order (tenant_id, id),
    CONSTRAINT fk_execution_merge_contract
        FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id),
    CONSTRAINT chk_execution_merge_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '特殊业务合并下单批次';

CREATE TABLE com_execution_order_merge_member (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    merge_batch_id BIGINT NOT NULL COMMENT '合并批次ID',
    execution_id BIGINT NULL COMMENT '执行单ID',
    execution_no VARCHAR(255) NOT NULL COMMENT '执行单编号',
    execution_no_short VARCHAR(255) NULL COMMENT '执行单编号简称',
    profit_center VARCHAR(64) NULL COMMENT '利润中心',
    source_order_code VARCHAR(128) NULL COMMENT '来源订单编码',
    member_sort INT NOT NULL DEFAULT 0 COMMENT '成员顺序',
    is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '同一业务范围内是否为主记录：0否，1是',
    source_record_key VARCHAR(128) NOT NULL COMMENT '来源记录稳定唯一键',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_execution_merge_member_tenant_row (tenant_id, id),
    UNIQUE KEY uk_execution_merge_member_source (
        tenant_id, merge_batch_id, source_record_key
    ),
    KEY idx_execution_merge_member_execution (
        tenant_id, execution_id, merge_batch_id
    ),
    CONSTRAINT fk_execution_merge_member_batch
        FOREIGN KEY (tenant_id, merge_batch_id) REFERENCES com_execution_order_merge_batch (tenant_id, id),
    CONSTRAINT fk_execution_merge_member_execution
        FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id),
    CONSTRAINT chk_execution_merge_member_primary CHECK (is_primary IN (0, 1)),
    CONSTRAINT chk_execution_merge_member_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '特殊合并下单执行单成员，不限制成员数量';

CREATE TABLE com_order_change_relation (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    source_order_id BIGINT NOT NULL COMMENT '来源订单ID',
    target_order_id BIGINT NOT NULL COMMENT '目标订单ID',
    relation_type VARCHAR(32) NOT NULL COMMENT '关系类型编码',
    change_batch_no VARCHAR(64) NULL COMMENT '变更批次编号',
    reason VARCHAR(500) NULL COMMENT '原因',
    effective_time DATETIME(3) NULL COMMENT '生效时间',
    source_evidence JSON NULL COMMENT '结构化来源证据JSON',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_change_rel_tenant_row (tenant_id, id),
    UNIQUE KEY uk_order_change (
        tenant_id, source_order_id, target_order_id, relation_type
    ),
    KEY idx_order_change_target (
        tenant_id, target_order_id, relation_type
    ),
    CONSTRAINT fk_order_change_source
        FOREIGN KEY (tenant_id, source_order_id) REFERENCES com_sales_order (tenant_id, id),
    CONSTRAINT fk_order_change_target
        FOREIGN KEY (tenant_id, target_order_id) REFERENCES com_sales_order (tenant_id, id),
    CONSTRAINT chk_order_change_self
        CHECK (source_order_id <> target_order_id),
    CONSTRAINT chk_order_change_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '改单、拆分、替代和退货订单血缘';

CREATE TABLE plt_sync_batch (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    batch_no VARCHAR(64) NOT NULL COMMENT '批次编号',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    object_type VARCHAR(64) NOT NULL COMMENT '对象类型编码',
    sync_mode VARCHAR(32) NOT NULL COMMENT '同步模式编码',
    started_time DATETIME(3) NOT NULL COMMENT '开始时间',
    finished_time DATETIME(3) NULL COMMENT '完成时间',
    read_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '读取数量',
    success_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成功数量',
    failure_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '失败数量',
    source_cursor VARCHAR(512) NULL COMMENT '来源游标',
    source_extract_location VARCHAR(1024) NULL COMMENT '来源抽取位置',
    source_extract_checksum VARCHAR(128) NULL COMMENT '来源抽取校验值',
    error_summary VARCHAR(2048) NULL COMMENT '错误摘要',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sync_batch_tenant_row (tenant_id, id),
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

CREATE TABLE plt_migration_source_record (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    batch_id BIGINT NOT NULL COMMENT '批次ID',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    source_table VARCHAR(64) NOT NULL COMMENT '来源系统物理表名',
    source_pk VARCHAR(128) NOT NULL COMMENT '来源主键原格式序列化值',
    source_business_key VARCHAR(512) NULL COMMENT '来源记录可读业务键',
    source_payload JSON NOT NULL COMMENT '来源记录原值JSON',
    source_checksum VARCHAR(128) NOT NULL COMMENT '来源记录SHA-256校验值',
    extracted_time DATETIME(3) NOT NULL COMMENT '抽取时间',
    mapping_status VARCHAR(32) NOT NULL DEFAULT 'EXTRACTED' COMMENT '映射状态',
    mapped_target_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已映射目标数量',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_migration_source_record_tenant_row (tenant_id, id),
    UNIQUE KEY uk_migration_source_record (
        tenant_id, batch_id, source_system, source_table, source_pk
    ),
    KEY idx_migration_source_mapping (
        tenant_id, batch_id, source_table, mapping_status
    ),
    KEY idx_migration_source_business (
        tenant_id, source_system, source_table, source_business_key(191)
    ),
    CONSTRAINT fk_migration_source_batch
        FOREIGN KEY (tenant_id, batch_id) REFERENCES plt_sync_batch (tenant_id, id),
    CONSTRAINT chk_migration_source_target_count
        CHECK (mapped_target_count >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '迁移批次逐源行的完整原值证据，不因目标归并或去重而覆盖';

CREATE TABLE plt_external_key_mapping (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    batch_id BIGINT NULL COMMENT '批次ID',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    source_table VARCHAR(64) NOT NULL COMMENT '来源系统物理表名',
    source_pk VARCHAR(128) NOT NULL COMMENT '来源主键',
    source_business_key VARCHAR(512) NULL COMMENT '来源业务键',
    target_role VARCHAR(32) NOT NULL DEFAULT 'PRIMARY' COMMENT '业务角色',
    target_sequence INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '目标顺序',
    target_table VARCHAR(64) NOT NULL COMMENT '目标表',
    target_id BIGINT NOT NULL COMMENT '目标ID',
    mapping_status VARCHAR(32) NOT NULL COMMENT '映射状态',
    source_checksum VARCHAR(128) NULL COMMENT '来源记录SHA-256校验值',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_external_key_map_tenant_row (tenant_id, id),
    UNIQUE KEY uk_external_key_source_target (
        tenant_id, source_system, source_table, source_pk,
        target_role, target_sequence, target_table, target_id
    ),
    KEY idx_external_key_source (
        tenant_id, source_system, source_table, source_pk
    ),
    KEY idx_external_key_target (
        tenant_id, target_table, target_id
    ),
    KEY idx_external_key_batch (tenant_id, batch_id, mapping_status),
    CONSTRAINT fk_external_key_batch
        FOREIGN KEY (tenant_id, batch_id) REFERENCES plt_sync_batch (tenant_id, id),
    CONSTRAINT chk_external_key_target_sequence CHECK (target_sequence >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '旧主键到新主键的可追溯映射';

CREATE TABLE plt_migration_issue (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    batch_id BIGINT NOT NULL COMMENT '批次ID',
    source_system VARCHAR(32) NOT NULL COMMENT '来源系统',
    source_table VARCHAR(64) NOT NULL COMMENT '来源系统物理表名',
    source_pk VARCHAR(128) NOT NULL COMMENT '来源主键',
    issue_type VARCHAR(64) NOT NULL COMMENT '问题类型编码',
    raw_business_key VARCHAR(512) NULL COMMENT '原始业务键',
    candidate_target_ids JSON NULL COMMENT '目标记录ID数组JSON',
    raw_payload JSON NULL COMMENT '原始字段和值JSON',
    resolution_status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '解析状态',
    resolution_action VARCHAR(2048) NULL COMMENT '解析行为',
    resolver VARCHAR(64) NULL COMMENT '解决人',
    resolved_time DATETIME(3) NULL COMMENT '解决时间',
    creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_migration_issue_tenant_row (tenant_id, id),
    UNIQUE KEY uk_migration_issue_source (
        tenant_id, batch_id, source_table, source_pk, issue_type
    ),
    KEY idx_migration_issue_status (
        tenant_id, issue_type, resolution_status, create_time
    ),
    CONSTRAINT fk_migration_issue_batch
        FOREIGN KEY (tenant_id, batch_id) REFERENCES plt_sync_batch (tenant_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '迁移缺失、重复、多义映射和人工解决记录';

CREATE TABLE ana_project_delivery_summary (
    project_id BIGINT NOT NULL COMMENT '项目ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_code VARCHAR(64) NOT NULL COMMENT '项目编码',
    project_name VARCHAR(255) NULL COMMENT '项目名称',
    project_type VARCHAR(32) NOT NULL COMMENT '项目类型',
    project_status VARCHAR(32) NOT NULL COMMENT '项目状态',
    parent_id BIGINT NULL COMMENT '父项目ID',
    root_id BIGINT NOT NULL COMMENT '根项目ID',
    customer_id BIGINT NULL COMMENT '客户主档ID',
    customer_code VARCHAR(64) NULL COMMENT '客户编码',
    customer_name VARCHAR(255) NULL COMMENT '客户名称',
    company_id BIGINT NULL COMMENT '主责公司ID',
    company_code VARCHAR(64) NULL COMMENT '主责公司编码',
    company_name VARCHAR(255) NULL COMMENT '主责公司名称',
    department_id BIGINT NULL COMMENT '主责部门ID',
    department_code VARCHAR(64) NULL COMMENT '主责部门编码',
    department_name VARCHAR(255) NULL COMMENT '主责部门名称',
    manager_id BIGINT NULL COMMENT '负责人用户ID',
    manager_employee_no VARCHAR(64) NULL COMMENT '负责人工号',
    manager_name VARCHAR(128) NULL COMMENT '负责人姓名',
    contract_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '合同数量',
    order_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '订单数量',
    order_line_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '订单行数量',
    active_scope_qty DECIMAL(20, 4) NOT NULL DEFAULT 0 COMMENT '有效实施范围数量',
    erp_delivered_qty DECIMAL(20, 4) NOT NULL DEFAULT 0 COMMENT 'ERP已发货数量',
    device_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '设备数量',
    pending_mapping_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '待处理映射数量',
    pending_qty_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '待处理数量',
    statistic_time DATETIME(3) NOT NULL COMMENT '汇总重算时间',
    source_batch_no VARCHAR(64) NULL COMMENT '来源批次编号',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (tenant_id, project_id),
    KEY idx_project_summary_status (
        tenant_id, pending_mapping_count, pending_qty_count
    ),
    KEY idx_project_summary_project_status (
        tenant_id, project_status, project_type, project_id
    ),
    KEY idx_project_summary_customer (
        tenant_id, customer_code, project_status, project_id
    ),
    KEY idx_project_summary_company_department (
        tenant_id, company_code, department_code, project_status, project_id
    ),
    KEY idx_project_summary_manager (
        tenant_id, manager_employee_no, project_status, project_id
    ),
    KEY idx_project_summary_time (tenant_id, statistic_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '可重建的项目合同、订单、发货和SN汇总读模型';

CREATE TABLE imp_configuration_collection_result (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    collection_task_id BIGINT NOT NULL COMMENT '统一采集任务逻辑引用',
    project_id BIGINT NOT NULL COMMENT '项目逻辑引用',
    device_id BIGINT NOT NULL COMMENT '设备逻辑引用',
    project_snapshot JSON NOT NULL COMMENT '采集时项目上下文快照',
    device_snapshot JSON NOT NULL COMMENT '采集时设备序列号、型号与类型快照',
    result_type_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '整机或其他可扩展结果类型',
    result_version_no INT UNSIGNED NOT NULL COMMENT '结果版本',
    source_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '在线采集或手工上传等可扩展来源',
    script_version VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '采集时SCH-03脚本版本',
    parser_version VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '解析器版本',
    raw_log_file_id BIGINT NOT NULL COMMENT '原始整机Log引用',
    raw_log_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原始整机Log SHA-256',
    operator_user_id BIGINT NOT NULL COMMENT '采集或上传操作人逻辑引用',
    operated_time DATETIME(3) NOT NULL,
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_configuration_collection_result (tenant_id, collection_task_id, result_type_code, result_version_no),
    UNIQUE KEY uk_configuration_collection_result_tenant_row (tenant_id, id),
    KEY idx_configuration_collection_result_device (tenant_id, project_id, device_id, operated_time),
    KEY idx_configuration_collection_result_hash (tenant_id, raw_log_sha256),
    CONSTRAINT chk_configuration_collection_result_version CHECK (result_version_no > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '不可覆盖的配置采集结果、整机Log及项目设备快照';

CREATE TABLE imp_configuration_collection_parse_attempt (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    collection_result_id BIGINT NOT NULL COMMENT '配置采集结果逻辑引用',
    attempt_no INT UNSIGNED NOT NULL,
    parser_version VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    parse_status_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    error_summary VARCHAR(1000) NULL,
    started_time DATETIME(3) NOT NULL,
    completed_time DATETIME(3) NULL,
    evidence_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '原始配置Log证据引用',
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_configuration_parse_attempt (tenant_id, collection_result_id, attempt_no),
    UNIQUE KEY uk_configuration_parse_attempt_tenant_row (tenant_id, id),
    KEY idx_configuration_parse_attempt_result (tenant_id, collection_result_id, started_time),
    CONSTRAINT chk_configuration_parse_attempt_no CHECK (attempt_no > 0),
    CONSTRAINT chk_configuration_parse_attempt_time CHECK (completed_time IS NULL OR completed_time >= started_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '配置采集结果解析尝试，不覆盖原始配置Log';

CREATE TABLE imp_configuration_component_candidate (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    parse_attempt_id BIGINT NOT NULL COMMENT '解析尝试逻辑引用',
    candidate_no INT UNSIGNED NOT NULL,
    parse_revision_no INT UNSIGNED NOT NULL COMMENT '所属独立解析版本',
    chassis_sn VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    slot_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    card_sn VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    card_model_code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    parser_version VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    card_configuration_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '板卡配置提取结果逻辑引用',
    match_status_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    matched_device_id BIGINT NULL COMMENT '已匹配设备逻辑引用',
    evidence_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_configuration_component_candidate (tenant_id, parse_attempt_id, candidate_no),
    UNIQUE KEY uk_configuration_component_candidate_tenant_row (tenant_id, id),
    KEY idx_configuration_component_candidate_match (tenant_id, match_status_code, create_time),
    KEY idx_configuration_component_candidate_sn (tenant_id, chassis_sn, slot_code, card_sn),
    CONSTRAINT chk_configuration_component_candidate_no CHECK (candidate_no > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '配置Log解析形成的板卡候选及待匹配证据';

CREATE TABLE acc_satisfaction_collection_task (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL COMMENT '项目逻辑引用',
    business_purpose_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '冻结满意度业务用途',
    applicable_timing_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '初验、终验、转包付款或模板配置时点',
    source_context VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    source_object_type VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    source_object_id BIGINT NOT NULL COMMENT '来源对象逻辑引用',
    source_object_version VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '冻结来源业务对象版本',
    payment_stage_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '适用转包付款阶段',
    payment_stage_key VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin GENERATED ALWAYS AS (
        COALESCE(payment_stage_code, '')
    ) STORED,
    delivery_scope_snapshot JSON NULL COMMENT '本次付款或验收交付范围冻结快照',
    delivery_scope_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    task_revision_no INT UNSIGNED NOT NULL COMMENT '整改重收任务序号',
    prior_task_id BIGINT NULL COMMENT '整改前序满意度任务逻辑引用',
    remediation_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '整改事实逻辑引用',
    template_id BIGINT NOT NULL COMMENT '冻结问卷模板逻辑引用',
    template_version VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    frozen_threshold DECIMAL(10, 4) NOT NULL,
    state_machine_version VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    status_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    current_responsible_user_id BIGINT NOT NULL COMMENT '当前责任人逻辑引用',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater BIGINT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_satisfaction_task_revision (tenant_id, project_id, source_context, source_object_type, source_object_id, source_object_version, business_purpose_code, applicable_timing_code, payment_stage_key, task_revision_no),
    UNIQUE KEY uk_satisfaction_collection_task_tenant_row (tenant_id, id),
    KEY idx_satisfaction_task_owner (tenant_id, current_responsible_user_id, status_code),
    KEY idx_satisfaction_task_source (tenant_id, source_context, source_object_type, source_object_id),
    CONSTRAINT chk_satisfaction_task_revision CHECK (task_revision_no > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目成员承办的满意度收集领域任务';

CREATE TABLE acc_satisfaction_questionnaire (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL COMMENT '满意度任务逻辑引用',
    source_questionnaire_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '旧问卷实例来源键',
    source_questionnaire_version VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    questionnaire_revision_no INT UNSIGNED NOT NULL,
    prior_questionnaire_id BIGINT NULL COMMENT '整改前序问卷逻辑引用',
    remediation_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '整改事实逻辑引用',
    template_id BIGINT NOT NULL COMMENT '问卷模板逻辑引用',
    frozen_question_json JSON NOT NULL COMMENT '冻结题目、必答项与分值规则',
    frozen_threshold DECIMAL(10, 4) NOT NULL,
    template_version VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    rule_version VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '冻结满意度规则版本',
    required_question_count INT UNSIGNED NOT NULL COMMENT '冻结必答题数',
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_satisfaction_questionnaire_revision (tenant_id, task_id, questionnaire_revision_no),
    UNIQUE KEY uk_satisfaction_questionnaire_tenant_row (tenant_id, id),
    KEY idx_satisfaction_questionnaire_task (tenant_id, task_id, create_time),
    CONSTRAINT chk_satisfaction_questionnaire_revision CHECK (questionnaire_revision_no > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '不可覆盖的满意度问卷冻结实例';

CREATE TABLE acc_satisfaction_response (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    questionnaire_id BIGINT NOT NULL COMMENT '问卷实例逻辑引用',
    response_no INT UNSIGNED NOT NULL,
    request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '提交幂等键',
    answer_json JSON NOT NULL,
    response_valid TINYINT NOT NULL COMMENT '答卷整体有效性事实',
    signature_valid TINYINT NOT NULL COMMENT '客户签字有效性事实',
    required_validation_summary JSON NOT NULL COMMENT '必答项验证摘要',
    item_validation_summary JSON NOT NULL COMMENT '逐项答案验证摘要',
    signature_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    attachment_refs_json JSON NULL,
    submit_time DATETIME(3) NOT NULL,
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_satisfaction_response_sequence (tenant_id, questionnaire_id, response_no),
    UNIQUE KEY uk_satisfaction_response_request (tenant_id, questionnaire_id, request_id),
    UNIQUE KEY uk_satisfaction_response_tenant_row (tenant_id, id),
    KEY idx_satisfaction_response_questionnaire (tenant_id, questionnaire_id, submit_time),
    CONSTRAINT chk_satisfaction_response_sequence CHECK (response_no > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '不可覆盖的客户满意度答卷、签字和附件事实';

CREATE TABLE acc_satisfaction_result (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    questionnaire_id BIGINT NOT NULL COMMENT '问卷实例逻辑引用',
    response_id BIGINT NOT NULL COMMENT '答卷逻辑引用',
    result_no INT UNSIGNED NOT NULL,
    response_valid TINYINT NOT NULL,
    signature_valid TINYINT NOT NULL,
    required_items_valid TINYINT NOT NULL,
    validation_summary JSON NOT NULL COMMENT '答卷、签字、必答项和逐项校验摘要',
    score DECIMAL(10, 4) NOT NULL,
    frozen_threshold DECIMAL(10, 4) NOT NULL,
    passed TINYINT NOT NULL,
    blocking_reason VARCHAR(1000) NULL COMMENT '闭环或付款阻断原因',
    archive_status_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'ACC-04归档事实状态',
    archive_artifact_id BIGINT NULL COMMENT 'ACC-04交付件逻辑引用',
    archive_payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    archive_time DATETIME(3) NULL,
    decision_rule_version VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    decision_time DATETIME(3) NOT NULL,
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_satisfaction_result_sequence (tenant_id, questionnaire_id, result_no),
    UNIQUE KEY uk_satisfaction_result_response (tenant_id, response_id),
    UNIQUE KEY uk_satisfaction_result_tenant_row (tenant_id, id),
    KEY idx_satisfaction_result_gate (tenant_id, passed, decision_time),
    CONSTRAINT chk_satisfaction_result_sequence CHECK (result_no > 0),
    CONSTRAINT chk_satisfaction_result_passed CHECK (passed IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '不可覆盖的满意度评分、阈值与达标判定事实';

CREATE TABLE cut_cutover_checklist (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    cutover_task_id BIGINT NOT NULL COMMENT 'CUT-01割接任务逻辑引用',
    assessment_id BIGINT NOT NULL COMMENT 'CUT-02等级评估逻辑引用',
    assessment_version INT UNSIGNED NOT NULL,
    checklist_version INT UNSIGNED NOT NULL,
    status_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    input_snapshot JSON NOT NULL COMMENT 'P3规则匹配输入冻结快照',
    input_snapshot_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    config_revision_snapshot JSON NOT NULL COMMENT '采集项与匹配配置revision快照',
    match_trace JSON NOT NULL COMMENT '逐项规则匹配轨迹',
    config_gap_snapshot JSON NULL COMMENT '配置缺口快照',
    submitted_by BIGINT NULL,
    submitted_at DATETIME(3) NULL,
    invalidated_at DATETIME(3) NULL,
    invalidated_reason VARCHAR(1000) NULL,
    current_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN invalidated_at IS NULL THEN 1 ELSE NULL END
    ) STORED,
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '草稿重匹配乐观锁版本',
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater BIGINT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cutover_checklist_tenant_row (tenant_id, id),
    UNIQUE KEY uk_cutover_checklist_version (
        tenant_id, cutover_task_id, checklist_version
    ),
    UNIQUE KEY uk_cutover_checklist_current (
        tenant_id, cutover_task_id, current_marker
    ),
    KEY idx_cutover_checklist_assessment (
        tenant_id, assessment_id, assessment_version
    ),
    CONSTRAINT chk_cutover_checklist_version CHECK (
        assessment_version > 0 AND checklist_version > 0
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'CUT-03调研及风险考察清单的输入、匹配与配置缺口版本';

CREATE TABLE cut_cutover_checklist_item (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    checklist_id BIGINT NOT NULL COMMENT 'CUT-03清单版本ID',
    stable_item_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    item_definition_id BIGINT NULL COMMENT '系统采集项定义逻辑引用',
    item_definition_version INT UNSIGNED NULL,
    item_type_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    item_description VARCHAR(2000) NULL,
    interface_format_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    interface_schema_snapshot JSON NULL COMMENT '冻结界面与输入Schema',
    display_condition_snapshot JSON NULL COMMENT '冻结显示条件',
    work_mode_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    required_flag TINYINT NOT NULL DEFAULT 0,
    source_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    device_id BIGINT NULL COMMENT '适用设备逻辑引用',
    command_template_id BIGINT NULL COMMENT 'DAC命令模板逻辑引用',
    matched_rule_id BIGINT NULL,
    matched_rule_version INT UNSIGNED NULL,
    applicable_flag TINYINT NOT NULL DEFAULT 1,
    custom_creator_user_id BIGINT NULL COMMENT '自定义项创建人',
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '草稿阶段乐观锁版本',
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater BIGINT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cutover_checklist_item_tenant_row (tenant_id, id),
    UNIQUE KEY uk_cutover_checklist_item_key (
        tenant_id, checklist_id, stable_item_key
    ),
    KEY idx_cutover_checklist_item_type (
        tenant_id, checklist_id, item_type_code, applicable_flag, sort_order
    ),
    KEY idx_cutover_checklist_item_device (tenant_id, device_id, checklist_id),
    CONSTRAINT fk_cutover_checklist_item_checklist
        FOREIGN KEY (tenant_id, checklist_id)
        REFERENCES cut_cutover_checklist (tenant_id, id),
    CONSTRAINT chk_cutover_checklist_item_required CHECK (required_flag IN (0, 1)),
    CONSTRAINT chk_cutover_checklist_item_applicable CHECK (applicable_flag IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'CUT-03清单版本内稳定采集项、界面与匹配快照';

CREATE TABLE cut_cutover_checklist_item_result (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    checklist_item_id BIGINT NOT NULL COMMENT 'CUT-03清单项ID',
    result_version INT UNSIGNED NOT NULL,
    result_source_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL
        COMMENT '直接填写、自动采集、外部加载或人工降级',
    answer_snapshot JSON NULL COMMENT '结构化答案冻结快照',
    fact_description TEXT NULL COMMENT '文本事实说明',
    collection_task_id BIGINT NULL COMMENT 'DAC CollectionTask逻辑引用',
    collection_result_reference_id BIGINT NULL COMMENT 'DAC结果稳定引用',
    collection_result_version VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    external_source_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    query_condition_snapshot JSON NULL COMMENT '外部加载查询条件脱敏快照',
    queried_at DATETIME(3) NULL,
    load_failure_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    manual_evidence_file_reference VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    selection_started_at DATETIME(3) NOT NULL COMMENT '成为当前选择时间',
    selection_ended_at DATETIME(3) NULL COMMENT '结束当前选择时间',
    selected_by BIGINT NOT NULL,
    selection_reason_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    current_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN selection_ended_at IS NULL THEN 1 ELSE NULL END
    ) STORED,
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cutover_checklist_item_result_tenant_row (tenant_id, id),
    UNIQUE KEY uk_cutover_checklist_item_result_version (
        tenant_id, checklist_item_id, result_version
    ),
    UNIQUE KEY uk_cutover_checklist_item_result_current (
        tenant_id, checklist_item_id, current_marker
    ),
    KEY idx_cutover_checklist_item_result_collection_task (
        tenant_id, collection_task_id
    ),
    KEY idx_cutover_checklist_item_result_selected (
        tenant_id, checklist_item_id, selection_started_at
    ),
    CONSTRAINT fk_cutover_checklist_item_result_item
        FOREIGN KEY (tenant_id, checklist_item_id)
        REFERENCES cut_cutover_checklist_item (tenant_id, id),
    CONSTRAINT chk_cutover_checklist_item_result_version CHECK (result_version > 0),
    CONSTRAINT chk_cutover_checklist_item_result_selection CHECK (
        selection_ended_at IS NULL OR selection_ended_at >= selection_started_at
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'CUT-03直接填写、采集、外部加载和人工降级结果的追加事实';

CREATE TABLE cut_cutover_support_arrangement (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    cutover_task_id BIGINT NOT NULL COMMENT 'CUT-01割接任务逻辑引用',
    plan_revision_id BIGINT NOT NULL COMMENT 'CUT-04方案版本逻辑引用',
    arrangement_no INT UNSIGNED NOT NULL COMMENT '方案版本内保障人员顺序',
    person_type_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '内部人员或外部联系人',
    person_name VARCHAR(128) NOT NULL,
    internal_user_id BIGINT NULL COMMENT '内部人员逻辑引用',
    contact_info VARCHAR(512) NOT NULL,
    arrival_time DATETIME(3) NULL,
    role_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    task_duty VARCHAR(1000) NOT NULL,
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater BIGINT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cutover_support_arrangement_no (tenant_id, plan_revision_id, arrangement_no),
    UNIQUE KEY uk_cutover_support_arrangement_tenant_row (tenant_id, id),
    KEY idx_cutover_support_arrangement_task (tenant_id, cutover_task_id, plan_revision_id),
    CONSTRAINT chk_cutover_support_arrangement_no CHECK (arrangement_no > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'CUT-04方案从属保障人员安排，不具有工单状态或责任区间';

CREATE TABLE cut_cutover_closure (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    cutover_task_id BIGINT NOT NULL COMMENT 'CUT-01割接任务逻辑引用',
    plan_revision_id BIGINT NOT NULL COMMENT '已批准CUT-04方案版本',
    precheck_normal TINYINT NULL,
    execution_normal TINYINT NULL,
    test_normal TINYINT NULL,
    rollback_occurred TINYINT NULL,
    rollback_description VARCHAR(1000) NULL,
    detail_description TEXT NULL,
    legacy_item_text TEXT NULL COMMENT '遗留项闭环快照文本',
    collection_result_refs JSON NULL COMMENT 'INT-12回调或人工上传结果引用',
    attachment_refs JSON NULL,
    result_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '成功或失败；提交前可空',
    submitted_by BIGINT NULL,
    submitted_time DATETIME(3) NULL,
    archive_time DATETIME(3) NULL,
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cutover_closure_task (tenant_id, cutover_task_id),
    UNIQUE KEY uk_cutover_closure_tenant_row (tenant_id, id),
    KEY idx_cutover_closure_result (tenant_id, result_code, archive_time),
    CONSTRAINT chk_cutover_closure_precheck CHECK (precheck_normal IS NULL OR precheck_normal IN (0, 1)),
    CONSTRAINT chk_cutover_closure_execution CHECK (execution_normal IS NULL OR execution_normal IN (0, 1)),
    CONSTRAINT chk_cutover_closure_test CHECK (test_normal IS NULL OR test_normal IN (0, 1)),
    CONSTRAINT chk_cutover_closure_rollback CHECK (rollback_occurred IS NULL OR rollback_occurred IN (0, 1)),
    CONSTRAINT chk_cutover_closure_submit CHECK (
        submitted_time IS NULL
        OR (submitted_by IS NOT NULL AND archive_time IS NOT NULL AND result_code IS NOT NULL)
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'CUT-06 P6轻量闭环与归档事实，不保存逐步骤执行或稳定观察';

CREATE TABLE ast_device_component_relation (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    chassis_device_id BIGINT NOT NULL COMMENT '机框设备逻辑引用',
    chassis_sn VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    slot_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    card_device_id BIGINT NULL COMMENT '板卡设备逻辑引用',
    card_sn VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    card_model_code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    relation_source_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    evidence_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    effective_to DATETIME(3) NULL,
    current_slot_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin GENERATED ALWAYS AS (
        CASE WHEN effective_to IS NULL THEN slot_code ELSE NULL END
    ) STORED,
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_component_current_slot (tenant_id, chassis_device_id, current_slot_code),
    UNIQUE KEY uk_device_component_relation_tenant_row (tenant_id, id),
    KEY idx_device_component_card (tenant_id, card_sn, effective_to),
    KEY idx_device_component_chassis (tenant_id, chassis_sn, effective_from),
    CONSTRAINT chk_device_component_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '机框、槽位与板卡的当前及历史关系';
