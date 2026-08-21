-- =============================================================================
-- V57: 项目主档与手工创建基座（F-PM01 / PM-01）
--
-- 目标模型按 SDS 数据库设计与 ADR-0019 命名：proj_ 前缀、uk 含 tenant_id、
-- 字符串状态码、乐观锁 version。与 V52 一致不建外键（应用层维护引用完整性）。
--
-- 相对附录目标 DDL 的两处运行时适配（其余列/键按目标 DDL 落地）：
-- 1. 丢弃 proj_project 自引用外键与 chk_project_code_namespace：
--    根项目 code_root_id=id 在 AUTO_INCREMENT 下无法单语句满足（MySQL 无延迟约束），
--    由领域层同事务两段写入并经单测锁定不变量；
--    PRD 双重唯一（项目编码租户内唯一 / 序号在编码命名空间根内唯一）仍由
--    uk(tenant_id, project_code) 与 uk(tenant_id, code_root_id, project_sequence) 兜底。
-- 2. SDS 08 要求四维分别保存，目标 DDL 缺 signing_method 等列，按 SDS 09 §12.1 前向扩列。
--
-- 实例五要素表（阶段/任务/里程碑/交付件/门禁+门禁引用）不在核心 60 表 DDL 内，
-- 按 FEATURE_FORWARD_MIGRATION 先例由本迁移前向新建，列语义对齐 V52 模板定义表。
-- =============================================================================

-- 1. 项目主档 + 树邻接真值（目标 DDL + 前向扩列）
CREATE TABLE IF NOT EXISTS `proj_project` (
    `id`                            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '项目ID',
    `project_code`                  VARCHAR(64)   NOT NULL COMMENT '项目编码（租户内唯一，创建后不可变）',
    `code_root_id`                  BIGINT        NOT NULL COMMENT '创建时冻结的编码命名空间根项目ID（根项目=自身ID）',
    `project_sequence`              INT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '编码命名空间内永久流水号：0=自身建立独立命名空间，>0=子项目（不回收复用）',
    `code_rule_version`             VARCHAR(32)   NOT NULL DEFAULT 'V1' COMMENT '编码生成规则版本（创建时冻结，ADR-0020）',
    `project_name`                  VARCHAR(255)  NULL COMMENT '项目名称',
    `parent_id`                     BIGINT        NULL COMMENT '父项目ID（NULL=根项目）',
    `root_id`                       BIGINT        NOT NULL COMMENT '项目树根节点项目ID（可由父子关系重建）',
    `tree_path`                     VARCHAR(1024) NOT NULL COMMENT '祖先路径缓存（可由父子关系重建）',
    `tree_depth`                    INT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '节点层级深度（根=0）',
    `tree_sort`                     INT           NOT NULL DEFAULT 0 COMMENT '同父下排序值（小者优先）',
    `customer_id`                   BIGINT        NULL COMMENT '直接客户主档ID',
    `customer_code`                 VARCHAR(64)   NULL COMMENT '客户编码',
    `customer_name`                 VARCHAR(255)  NULL COMMENT '客户名称',
    `manager_id`                    BIGINT        NULL COMMENT '当前主负责人用户ID',
    `manager_employee_no`           VARCHAR(64)   NULL COMMENT '负责人工号',
    `manager_name`                  VARCHAR(128)  NULL COMMENT '负责人姓名',
    `company_id`                    BIGINT        NULL COMMENT '当前主责公司主档ID',
    `company_code`                  VARCHAR(64)   NULL COMMENT '公司编码',
    `company_name`                  VARCHAR(255)  NULL COMMENT '公司名称',
    `department_id`                 BIGINT        NULL COMMENT '当前主责部门主档ID',
    `department_code`               VARCHAR(64)   NULL COMMENT '部门编码',
    `department_name`               VARCHAR(255)  NULL COMMENT '部门名称',
    `project_type`                  VARCHAR(32)   NOT NULL DEFAULT 'STANDARD' COMMENT '项目类型编码（字典约束）',
    `signing_method`                VARCHAR(64)   NULL COMMENT '签约方式（字典 pms_signing_method；SDS 08 四维分列，前向扩列）',
    `project_category`              VARCHAR(64)   NULL COMMENT '项目类别（字典 pms_project_category）',
    `implementation_mode`           VARCHAR(64)   NULL COMMENT '实施方式（字典 pms_implementation_method）',
    `major_project_level`           VARCHAR(64)   NULL COMMENT '重大项目级别（CRM权威来源属性映射，NULL=不限）',
    `market_code`                   VARCHAR(64)   NULL COMMENT '市场部编码',
    `market_name`                   VARCHAR(255)  NULL COMMENT '市场部名称',
    `system_code`                   VARCHAR(64)   NULL COMMENT '系统部编码',
    `system_name`                   VARCHAR(255)  NULL COMMENT '系统部名称',
    `expend_code`                   VARCHAR(64)   NULL COMMENT '拓展部编码',
    `expend_name`                   VARCHAR(255)  NULL COMMENT '拓展部名称',
    `industry_code`                 VARCHAR(64)   NULL COMMENT '行业编码',
    `industry_name`                 VARCHAR(255)  NULL COMMENT '行业名称',
    `customer_project_name`         VARCHAR(255)  NULL COMMENT '客户项目名称',
    `sales_type`                    VARCHAR(32)   NULL COMMENT '销售类型编码（字典约束）',
    `business_type`                 VARCHAR(32)   NULL COMMENT '业务类型编码（字典约束）',
    `service_level_code`            VARCHAR(64)   NULL COMMENT '服务级别编码',
    `not_track_reason`              VARCHAR(1024) NULL COMMENT '不跟踪原因',
    `contract_no`                   VARCHAR(128)  NULL COMMENT '手工登记合同号（正式商业关系随 INT-02 接管；前向扩列）',
    `implementation_location`       VARCHAR(500)  NULL COMMENT '实施地点（多地点拆分属 PM-02；前向扩列）',
    `creation_reason`               VARCHAR(500)  NULL COMMENT '手工创建原因（BR-2 必填，应用层校验；前向扩列）',
    `lifecycle_template_id`         BIGINT        NULL COMMENT '冻结的生命周期模板ID（proj_project_template）',
    `lifecycle_template_revision_no` INT          NULL COMMENT '冻结的模板版本号（创建时快照；前向扩列）',
    `template_load_method`          VARCHAR(32)   NULL COMMENT '模板加载方式：AUTO_DEFAULT唯一默认命中/MANUAL_SELECTED人工选择（前向扩列）',
    `process_definition_key`        VARCHAR(64)   NULL COMMENT '冻结的流程定义引用（取自绑定版本，创建时快照；前向扩列）',
    `process_definition_version`    VARCHAR(32)   NULL COMMENT '冻结的流程定义版本引用（前向扩列）',
    `project_start_time`            DATETIME(3)   NULL COMMENT '项目开始时间',
    `project_refresh_time`          DATETIME(3)   NULL COMMENT '项目刷新时间',
    `project_close_time`            DATETIME(3)   NULL COMMENT '项目关闭时间',
    `source_type`                   VARCHAR(32)   NOT NULL DEFAULT 'MANUAL' COMMENT '创建来源：MANUAL手工/ORDER订单/MIGRATION迁移（字典 pms_project_source_type）',
    `status`                        VARCHAR(32)   NOT NULL COMMENT '项目状态（字典 pms_project_lifecycle_stage，初始 S0）',
    `version`                       INT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`                       VARCHAR(64)   NULL DEFAULT '',
    `create_time`                   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`                       VARCHAR(64)   NULL DEFAULT '',
    `update_time`                   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                       BIT(1)        NOT NULL DEFAULT b'0',
    `deleted_time`                  DATETIME      NULL,
    `tenant_id`                     BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_project_code` (`tenant_id`, `project_code`),
    UNIQUE KEY `uk_proj_project_code_sequence` (`tenant_id`, `code_root_id`, `project_sequence`),
    KEY `idx_proj_project_parent` (`tenant_id`, `parent_id`, `tree_sort`, `id`),
    KEY `idx_proj_project_path` (`tenant_id`, `root_id`, `tree_path`(191)),
    KEY `idx_proj_project_manager` (`tenant_id`, `manager_id`, `status`),
    KEY `idx_proj_project_customer` (`tenant_id`, `customer_code`, `status`, `id`),
    KEY `idx_proj_project_company_department` (`tenant_id`, `company_code`, `department_code`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目主档及非固定层级项目树（双重唯一：编码租户内唯一/序号命名空间根内唯一）';

-- 2. 阶段实例
CREATE TABLE IF NOT EXISTS `proj_project_stage` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '阶段实例ID',
    `project_id`              BIGINT       NOT NULL COMMENT '项目ID（proj_project）',
    `stage_code`              VARCHAR(32)  NOT NULL COMMENT '阶段码（S0～S6，实例化时冻结）',
    `name`                    VARCHAR(128) NOT NULL COMMENT '阶段名称（快照）',
    `sort_order`              INT          NOT NULL DEFAULT 0 COMMENT '阶段顺序（快照）',
    `entry_criteria`          VARCHAR(500) NULL COMMENT '准入条件说明（快照）',
    `exit_criteria`           VARCHAR(500) NULL COMMENT '准出条件说明（快照）',
    `source_definition_id`    BIGINT       NULL COMMENT '冻结来源：模板阶段定义ID（proj_project_template_stage_definition）',
    `status`                  VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '阶段实例状态（字典 pms_project_stage_status，S0 初始 ACTIVE）',
    `version`                 INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`                 VARCHAR(64)  NULL DEFAULT '',
    `create_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`                 VARCHAR(64)  NULL DEFAULT '',
    `update_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                 BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`            DATETIME     NULL,
    `tenant_id`               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_stage` (`tenant_id`, `project_id`, `stage_code`),
    KEY `idx_proj_stage_project` (`project_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目阶段实例（实例化时从模板冻结）';

-- 3. 任务实例
CREATE TABLE IF NOT EXISTS `proj_project_task` (
    `id`                      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '任务实例ID',
    `project_id`              BIGINT        NOT NULL COMMENT '项目ID',
    `task_code`               VARCHAR(64)   NOT NULL COMMENT '任务码（实例化时冻结，项目内唯一）',
    `name`                    VARCHAR(128)  NOT NULL COMMENT '任务名称（快照）',
    `parent_task_code`        VARCHAR(64)   NULL COMMENT '父任务码（NULL=顶层）',
    `stage_code`              VARCHAR(32)   NOT NULL COMMENT '所属阶段码',
    `priority`                INT           NOT NULL DEFAULT 2 COMMENT '优先级（快照）',
    `sort_order`              INT           NOT NULL DEFAULT 0 COMMENT '排序（快照）',
    `estimated_hours`         DECIMAL(6,1)  NULL COMMENT '预估工时（快照）',
    `satisfaction_timing`     VARCHAR(32)   NULL COMMENT '满意度适用时点（快照，由 ACC-02 消费）',
    `description`             VARCHAR(500)  NULL COMMENT '任务说明（快照）',
    `source_definition_id`    BIGINT        NULL COMMENT '冻结来源：模板任务定义ID（proj_project_template_task_definition）',
    `status`                  VARCHAR(32)   NOT NULL DEFAULT 'PENDING_ASSIGN' COMMENT '任务实例状态（字典 pms_project_task_status，初始待分配）',
    `version`                 INT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`                 VARCHAR(64)   NULL DEFAULT '',
    `create_time`             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`                 VARCHAR(64)   NULL DEFAULT '',
    `update_time`             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                 BIT(1)        NOT NULL DEFAULT b'0',
    `deleted_time`            DATETIME      NULL,
    `tenant_id`               BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_task` (`tenant_id`, `project_id`, `task_code`),
    KEY `idx_proj_task_parent` (`project_id`, `parent_task_code`),
    KEY `idx_proj_task_stage` (`project_id`, `stage_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目任务实例（初始待分配）';

-- 4. 里程碑实例
CREATE TABLE IF NOT EXISTS `proj_project_milestone` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '里程碑实例ID',
    `project_id`              BIGINT       NOT NULL COMMENT '项目ID',
    `milestone_code`          VARCHAR(64)  NOT NULL COMMENT '里程碑码（实例化时冻结，项目内唯一）',
    `name`                    VARCHAR(128) NOT NULL COMMENT '里程碑名称（快照）',
    `stage_code`              VARCHAR(32)  NOT NULL COMMENT '所属阶段码',
    `timing`                  VARCHAR(64)  NULL COMMENT '时点说明（快照）',
    `criteria`                VARCHAR(500) NULL COMMENT '达成标准（快照）',
    `source_definition_id`    BIGINT       NULL COMMENT '冻结来源：模板里程碑定义ID',
    `status`                  VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '里程碑状态（字典 pms_project_milestone_status）',
    `version`                 INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`                 VARCHAR(64)  NULL DEFAULT '',
    `create_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`                 VARCHAR(64)  NULL DEFAULT '',
    `update_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                 BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`            DATETIME     NULL,
    `tenant_id`               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_milestone` (`tenant_id`, `project_id`, `milestone_code`),
    KEY `idx_proj_milestone_stage` (`project_id`, `stage_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目里程碑实例';

-- 5. 交付件实例
CREATE TABLE IF NOT EXISTS `proj_project_deliverable` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '交付件实例ID',
    `project_id`              BIGINT       NOT NULL COMMENT '项目ID',
    `deliverable_code`        VARCHAR(64)  NOT NULL COMMENT '交付件码（实例化时冻结，项目内唯一）',
    `name`                    VARCHAR(128) NOT NULL COMMENT '交付件名称（快照）',
    `stage_code`              VARCHAR(32)  NOT NULL COMMENT '所属阶段码',
    `task_code`               VARCHAR(64)  NULL COMMENT '关联任务码（NULL=阶段级）',
    `required`                BIT(1)       NOT NULL DEFAULT b'1' COMMENT '必需标志（快照）',
    `source_definition_id`    BIGINT       NULL COMMENT '冻结来源：模板交付件定义ID',
    `status`                  VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '交付件状态（字典 pms_project_deliverable_status）',
    `version`                 INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`                 VARCHAR(64)  NULL DEFAULT '',
    `create_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`                 VARCHAR(64)  NULL DEFAULT '',
    `update_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                 BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`            DATETIME     NULL,
    `tenant_id`               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_deliverable` (`tenant_id`, `project_id`, `deliverable_code`),
    KEY `idx_proj_deliverable_stage` (`project_id`, `stage_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目交付件实例';

-- 6. 门禁实例
CREATE TABLE IF NOT EXISTS `proj_project_gate` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '门禁实例ID',
    `project_id`              BIGINT       NOT NULL COMMENT '项目ID',
    `gate_code`               VARCHAR(64)  NOT NULL COMMENT '门禁码（实例化时冻结，项目内唯一）',
    `name`                    VARCHAR(128) NOT NULL COMMENT '门禁名称（快照）',
    `gate_type`               VARCHAR(16)  NOT NULL COMMENT '类型：ENTRY准入/EXIT准出',
    `stage_code`              VARCHAR(32)  NOT NULL COMMENT '所属阶段码',
    `description`             VARCHAR(500) NULL COMMENT '门禁说明（快照）',
    `validation_summary`      VARCHAR(1000) NULL COMMENT '冻结的校验内容摘要（实例化时自模板引用行生成）',
    `source_definition_id`    BIGINT       NULL COMMENT '冻结来源：模板门禁定义ID',
    `status`                  VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '门禁状态（字典 pms_project_gate_status）',
    `version`                 INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`                 VARCHAR(64)  NULL DEFAULT '',
    `create_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`                 VARCHAR(64)  NULL DEFAULT '',
    `update_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                 BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`            DATETIME     NULL,
    `tenant_id`               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_gate` (`tenant_id`, `project_id`, `gate_code`),
    KEY `idx_proj_gate_stage` (`project_id`, `stage_code`, `gate_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目门禁实例（ENTRY/EXIT）';

-- 7. 门禁实例引用行（结构化三元组，对齐 V52 模板门禁引用）
CREATE TABLE IF NOT EXISTS `proj_project_gate_reference` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '门禁引用ID',
    `gate_id`     BIGINT      NOT NULL COMMENT '门禁实例ID（proj_project_gate）',
    `ref_type`    VARCHAR(16) NOT NULL COMMENT '引用类型：TASK/DELIVERABLE/STATE/PROCESS（冻结）',
    `ref_code`    VARCHAR(64) NOT NULL COMMENT '引用编码（冻结）',
    `ref_version` VARCHAR(32) NULL COMMENT '引用版本（流程引用时使用，冻结）',
    `version`     INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`     VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     BIT(1)      NOT NULL DEFAULT b'0',
    `deleted_time` DATETIME   NULL,
    `tenant_id`   BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_gate_ref` (`tenant_id`, `gate_id`, `ref_type`, `ref_code`),
    KEY `idx_proj_gate_ref_gate` (`gate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目门禁实例引用行';

-- 8. 项目成员角色区间（按附录目标 DDL 原样；区间重叠由应用层防重）
CREATE TABLE IF NOT EXISTS `proj_project_member_assignment` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '成员区间ID',
    `project_id`      BIGINT       NOT NULL COMMENT '项目ID',
    `user_id`         BIGINT       NOT NULL COMMENT '用户ID',
    `employee_no`     VARCHAR(64)  NULL COMMENT '成员工号',
    `member_name`     VARCHAR(128) NULL COMMENT '成员姓名',
    `company_id`      BIGINT       NULL COMMENT '成员加入时公司主档ID',
    `company_code`    VARCHAR(64)  NULL COMMENT '公司编码',
    `company_name`    VARCHAR(255) NULL COMMENT '公司名称',
    `department_code` VARCHAR(64)  NULL COMMENT '部门编码',
    `department_name` VARCHAR(255) NULL COMMENT '部门名称',
    `member_role`     VARCHAR(32)  NOT NULL COMMENT '成员角色（字典 pms_project_member_role）',
    `responsibility`  VARCHAR(500) NULL COMMENT '职责',
    `effective_from`  DATETIME(3)  NULL COMMENT '生效开始时间',
    `effective_to`    DATETIME(3)  NULL COMMENT '失效时间（NULL=当前有效）',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    `version`         INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`         VARCHAR(64)  NULL DEFAULT '',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`         VARCHAR(64)  NULL DEFAULT '',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`    DATETIME     NULL,
    `tenant_id`       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_member_role` (`tenant_id`, `project_id`, `user_id`, `member_role`, `effective_from`),
    KEY `idx_proj_member_user` (`tenant_id`, `user_id`, `status`, `project_id`),
    KEY `idx_proj_member_employee` (`tenant_id`, `employee_no`, `status`, `project_id`),
    CONSTRAINT `chk_proj_member_dates` CHECK (`effective_to` IS NULL OR `effective_from` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目成员、角色及有效期（区间留痕）';

-- 9. 项目组织关系（按附录目标 DDL 原样；V1 承载下单办事处 ORDER_OFFICE）
CREATE TABLE IF NOT EXISTS `proj_project_company_department_relation` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '组织关系ID',
    `project_id`       BIGINT       NOT NULL COMMENT '项目ID',
    `company_id`       BIGINT       NULL COMMENT '公司主档ID（未完成映射时可为空）',
    `company_code`     VARCHAR(64)  NOT NULL COMMENT '公司编码',
    `company_name`     VARCHAR(255) NULL COMMENT '公司名称',
    `department_id`    BIGINT       NULL COMMENT '部门主档ID（无部门维度时可为空）',
    `department_code`  VARCHAR(64)  NULL COMMENT '部门编码',
    `department_name`  VARCHAR(255) NULL COMMENT '部门名称',
    `relation_role`    VARCHAR(32)  NOT NULL COMMENT '业务角色（字典 pms_company_relation_role）',
    `is_primary`       TINYINT      NOT NULL DEFAULT 0 COMMENT '同业务范围内是否主记录：0否/1是',
    `effective_from`   DATETIME(3)  NULL COMMENT '生效开始时间',
    `effective_to`     DATETIME(3)  NULL COMMENT '失效时间（NULL=当前有效）',
    `status`           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    `primary_project_id` BIGINT GENERATED ALWAYS AS (
        CASE WHEN `deleted` = b'0' AND `effective_to` IS NULL AND `is_primary` = 1
             THEN `project_id` ELSE NULL END
    ) STORED COMMENT '当前有效主记录指向的项目ID（参与唯一键）',
    `version`          INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`          VARCHAR(64)  NULL DEFAULT '',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`          VARCHAR(64)  NULL DEFAULT '',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`          BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`     DATETIME     NULL,
    `tenant_id`        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_company_department_rel` (`tenant_id`, `project_id`, `company_code`, `department_code`, `relation_role`, `effective_from`),
    UNIQUE KEY `uk_proj_primary_company_department` (`tenant_id`, `primary_project_id`, `relation_role`),
    KEY `idx_proj_company_reverse` (`tenant_id`, `company_code`, `relation_role`, `status`, `project_id`),
    CONSTRAINT `chk_proj_company_department_primary` CHECK (`is_primary` IN (0, 1)),
    CONSTRAINT `chk_proj_company_department_pair` CHECK (`department_id` IS NULL OR `department_code` IS NOT NULL),
    CONSTRAINT `chk_proj_company_department_dates` CHECK (`effective_to` IS NULL OR `effective_from` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目业务角色下的公司与部门组合关系（每项目每角色至多一条当前有效主记录）';

-- 10. 平台编码序列（ADR-0020；行锁原子分配，租户级 PLATFORM_ROOT 起步）
CREATE TABLE IF NOT EXISTS `proj_project_code_sequence` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '序列ID',
    `code_namespace` VARCHAR(64)  NOT NULL COMMENT '编码命名空间：V1=PLATFORM_ROOT（租户级平台流水）；PM-02 预留 ROOT:<code_root_id>',
    `next_value`     BIGINT       NOT NULL DEFAULT 1 COMMENT '下一个可分配流水号',
    `version`        INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`        VARCHAR(64)  NULL DEFAULT '',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`        VARCHAR(64)  NULL DEFAULT '',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`   DATETIME     NULL,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_code_sequence` (`tenant_id`, `code_namespace`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目平台编码序列（SELECT ... FOR UPDATE 原子递增）';

-- 11. API 命令幂等记录（tenant+command+actor+key 作用域，重放返回原资源）
CREATE TABLE IF NOT EXISTS `proj_idempotency_record` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '幂等记录ID',
    `command`          VARCHAR(64)  NOT NULL COMMENT '命令标识（如 ProjectCreate）',
    `actor_id`         BIGINT       NOT NULL COMMENT '操作者用户ID',
    `idempotency_key`  VARCHAR(128) NOT NULL COMMENT '幂等键（Header Idempotency-Key）',
    `request_digest`   CHAR(64)     NOT NULL COMMENT '请求体 SHA-256 摘要（同键异摘要拒绝）',
    `response_payload` TEXT         NULL COMMENT '首次成功响应载荷（重放原样返回）',
    `status`           VARCHAR(16)  NOT NULL DEFAULT 'COMPLETED' COMMENT '记录状态：COMPLETED/FAILED',
    `version`          INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`          VARCHAR(64)  NULL DEFAULT '',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`          VARCHAR(64)  NULL DEFAULT '',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`          BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`     DATETIME     NULL,
    `tenant_id`        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_idempotency` (`tenant_id`, `command`, `actor_id`, `idempotency_key`),
    KEY `idx_proj_idempotency_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API 命令幂等记录';

-- =============================================================================
-- 字典：成员角色（仅 PRD 已定义角色）/ 组织关系角色 / 生命周期阶段（PRD 4.2 状态口径）/
--       模板加载方式 / 实例状态（初始值+可扩展）/ 创建来源
-- =============================================================================
INSERT IGNORE INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`) VALUES
('PMS-项目成员角色', 'pms_project_member_role', 0, '项目成员角色（PM-01/PM-04，仅PRD已定义角色）', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
('PMS-项目组织角色', 'pms_company_relation_role', 0, '项目公司与部门组合的业务角色（前六项对齐目标DDL注释，ORDER_OFFICE服务PM-01下单办事处）', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
('PMS-项目生命周期阶段', 'pms_project_lifecycle_stage', 0, '项目状态（PRD 4.2：S0待开始…S6闭环 + MAINT维护）', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
('PMS-模板加载方式', 'pms_template_load_method', 0, '项目创建时模板加载方式', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
('PMS-阶段实例状态', 'pms_project_stage_status', 0, '项目阶段实例状态（初始值+可扩展）', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
('PMS-任务实例状态', 'pms_project_task_status', 0, '项目任务实例状态（PRD 4.6：待分配→…→完成/关闭）', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
('PMS-里程碑实例状态', 'pms_project_milestone_status', 0, '项目里程碑实例状态（初始值+可扩展）', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
('PMS-交付件实例状态', 'pms_project_deliverable_status', 0, '项目交付件实例状态（初始值+可扩展）', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
('PMS-门禁实例状态', 'pms_project_gate_status', 0, '项目门禁实例状态（初始值+可扩展）', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
('PMS-项目创建来源', 'pms_project_source_type', 0, '项目创建来源类型（目标DDL source_type口径）', 'admin', NOW(), 'admin', NOW(), b'0', NULL);

INSERT IGNORE INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, '项目经理', 'PROJECT_MANAGER', 'pms_project_member_role', 0, 'primary', '', '项目经理', 'admin', NOW(), 'admin', NOW(), b'0'),
(2, '一级服务经理', 'SERVICE_MANAGER_L1', 'pms_project_member_role', 0, 'success', '', '一级服务经理（PM-01指派）', 'admin', NOW(), 'admin', NOW(), b'0'),
(3, '二级服务经理', 'SERVICE_MANAGER_L2', 'pms_project_member_role', 0, 'info', '', '二级服务经理（PM-04预留）', 'admin', NOW(), 'admin', NOW(), b'0'),
(1, '主责', 'PRIMARY', 'pms_company_relation_role', 0, 'primary', '', '主责公司/部门', 'admin', NOW(), 'admin', NOW(), b'0'),
(2, '销售', 'SALES', 'pms_company_relation_role', 0, 'info', '', '销售角色', 'admin', NOW(), 'admin', NOW(), b'0'),
(3, '市场', 'MARKET', 'pms_company_relation_role', 0, 'info', '', '市场角色', 'admin', NOW(), 'admin', NOW(), b'0'),
(4, '系统', 'SYSTEM', 'pms_company_relation_role', 0, 'info', '', '系统角色', 'admin', NOW(), 'admin', NOW(), b'0'),
(5, '拓展', 'EXPANSION', 'pms_company_relation_role', 0, 'info', '', '拓展角色', 'admin', NOW(), 'admin', NOW(), b'0'),
(6, '实施', 'IMPLEMENTATION', 'pms_company_relation_role', 0, 'info', '', '实施角色', 'admin', NOW(), 'admin', NOW(), b'0'),
(7, '下单办事处', 'ORDER_OFFICE', 'pms_company_relation_role', 0, 'warning', '', 'PM-01一级服务经理规则承载（is_primary=1）', 'admin', NOW(), 'admin', NOW(), b'0'),
(1, '待开始', 'S0', 'pms_project_lifecycle_stage', 0, 'info', '', 'S0 立项与指派', 'admin', NOW(), 'admin', NOW(), b'0'),
(2, '工前准备', 'S1', 'pms_project_lifecycle_stage', 0, 'primary', '', 'S1 工前准备', 'admin', NOW(), 'admin', NOW(), b'0'),
(3, '施工计划', 'S2', 'pms_project_lifecycle_stage', 0, 'primary', '', 'S2 施工计划', 'admin', NOW(), 'admin', NOW(), b'0'),
(4, '实施方案', 'S3', 'pms_project_lifecycle_stage', 0, 'primary', '', 'S3 方案设计', 'admin', NOW(), 'admin', NOW(), b'0'),
(5, '实施部署', 'S4', 'pms_project_lifecycle_stage', 0, 'primary', '', 'S4 实施部署', 'admin', NOW(), 'admin', NOW(), b'0'),
(6, '验收交维', 'S5', 'pms_project_lifecycle_stage', 0, 'warning', '', 'S5 验收交维', 'admin', NOW(), 'admin', NOW(), b'0'),
(7, '闭环', 'S6', 'pms_project_lifecycle_stage', 0, 'success', '', 'S6 闭环', 'admin', NOW(), 'admin', NOW(), b'0'),
(8, '维护', 'MAINT', 'pms_project_lifecycle_stage', 0, 'info', '', '维护期（转维保后）', 'admin', NOW(), 'admin', NOW(), b'0'),
(1, '自动默认', 'AUTO_DEFAULT', 'pms_template_load_method', 0, 'info', '', '唯一默认命中自动加载', 'admin', NOW(), 'admin', NOW(), b'0'),
(2, '人工选择', 'MANUAL_SELECTED', 'pms_template_load_method', 0, 'primary', '', '多匹配时人工选择', 'admin', NOW(), 'admin', NOW(), b'0'),
(1, '未开始', 'PENDING', 'pms_project_stage_status', 0, 'info', '', '阶段未开始', 'admin', NOW(), 'admin', NOW(), b'0'),
(2, '进行中', 'ACTIVE', 'pms_project_stage_status', 0, 'primary', '', '阶段进行中（S0 初始态）', 'admin', NOW(), 'admin', NOW(), b'0'),
(3, '已完成', 'DONE', 'pms_project_stage_status', 0, 'success', '', '阶段已完成', 'admin', NOW(), 'admin', NOW(), b'0'),
(1, '待分配', 'PENDING_ASSIGN', 'pms_project_task_status', 0, 'info', '', '任务初始态（PRD 4.6）', 'admin', NOW(), 'admin', NOW(), b'0'),
(2, '待开始', 'PENDING_START', 'pms_project_task_status', 0, 'info', '', '已分配未开始', 'admin', NOW(), 'admin', NOW(), b'0'),
(3, '进行中', 'IN_PROGRESS', 'pms_project_task_status', 0, 'primary', '', '执行中', 'admin', NOW(), 'admin', NOW(), b'0'),
(4, '待验收', 'PENDING_ACCEPT', 'pms_project_task_status', 0, 'warning', '', '提交待验收', 'admin', NOW(), 'admin', NOW(), b'0'),
(5, '完成', 'DONE', 'pms_project_task_status', 0, 'success', '', '已完成', 'admin', NOW(), 'admin', NOW(), b'0'),
(6, '关闭', 'CLOSED', 'pms_project_task_status', 0, 'info', '', '已关闭', 'admin', NOW(), 'admin', NOW(), b'0'),
(1, '待达成', 'PENDING', 'pms_project_milestone_status', 0, 'info', '', '里程碑初始态', 'admin', NOW(), 'admin', NOW(), b'0'),
(2, '已达成', 'ACHIEVED', 'pms_project_milestone_status', 0, 'success', '', '里程碑已达成', 'admin', NOW(), 'admin', NOW(), b'0'),
(1, '待提交', 'PENDING', 'pms_project_deliverable_status', 0, 'info', '', '交付件初始态', 'admin', NOW(), 'admin', NOW(), b'0'),
(2, '已提交', 'SUBMITTED', 'pms_project_deliverable_status', 0, 'primary', '', '已提交待检查', 'admin', NOW(), 'admin', NOW(), b'0'),
(3, '已验收', 'ACCEPTED', 'pms_project_deliverable_status', 0, 'success', '', '检查通过', 'admin', NOW(), 'admin', NOW(), b'0'),
(1, '待校验', 'PENDING', 'pms_project_gate_status', 0, 'info', '', '门禁初始态', 'admin', NOW(), 'admin', NOW(), b'0'),
(2, '通过', 'PASSED', 'pms_project_gate_status', 0, 'success', '', '门禁校验通过', 'admin', NOW(), 'admin', NOW(), b'0'),
(3, '驳回', 'FAILED', 'pms_project_gate_status', 0, 'danger', '', '门禁校验驳回', 'admin', NOW(), 'admin', NOW(), b'0'),
(1, '手工创建', 'MANUAL', 'pms_project_source_type', 0, 'primary', '', '工程管理部手工创建（PM-01）', 'admin', NOW(), 'admin', NOW(), b'0'),
(2, '订单创建', 'ORDER', 'pms_project_source_type', 0, 'info', '', 'CRM执行单/ERP订单自动创建（INT域）', 'admin', NOW(), 'admin', NOW(), b'0'),
(3, '迁移导入', 'MIGRATION', 'pms_project_source_type', 0, 'warning', '', '历史数据迁移导入（AI-MIG-000）', 'admin', NOW(), 'admin', NOW(), b'0');

-- =============================================================================
-- 菜单：项目列表（18067 页面，挂 19261 项目管理组 sort=0 置顶 + 18068～18070 按钮）
-- 权限码复用旧语义码（pms:project:query/create/update/assign），旧链端点退役后由新链承接。
-- =============================================================================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(18067, '项目列表', 'pms:project:query', 2, 0, 19261, 'projects', 'ep:list', 'pms/project/projects/index', 'PmsProjects', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18068, '项目创建', 'pms:project:create', 3, 1, 18067, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18069, '项目更新', 'pms:project:update', 3, 2, 18067, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18070, '服务经理指派', 'pms:project:assign', 3, 3, 18067, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`), `parent_id`=VALUES(`parent_id`), `sort`=VALUES(`sort`), `update_time`=NOW(), `deleted`=b'0';
