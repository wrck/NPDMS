-- =============================================================================
-- V52: 项目模板基座（F-PM03 / PM-03）
--
-- 目标模型按 SDS 数据库设计与 ADR-0019 命名：<domain_code>_<full_domain_object_name>，
-- uk 含 tenant_id，字符串状态码，门禁引用结构化存储（不走 JSON）。
-- 草稿即版本：每模板至多一个 DRAFT revision，发布冻结为 PUBLISHED 只读。
-- 不含种子模板数据：验收配置经模板管理后台录入（Feature Spec 第3节）。
-- =============================================================================

-- 1. 模板身份表
CREATE TABLE IF NOT EXISTS `proj_project_template` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    `code`            VARCHAR(64)  NOT NULL COMMENT '模板编码（租户内唯一，系统保留编码受保护）',
    `name`            VARCHAR(128) NOT NULL COMMENT '模板名称',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿/ACTIVE生效/RETIRED停用',
    `match_priority`  INT          NOT NULL DEFAULT 100 COMMENT '匹配优先级（数值小者先命中）',
    `description`     VARCHAR(500) NULL COMMENT '业务场景描述',
    `system_reserved` BIT(1)       NOT NULL DEFAULT b'0' COMMENT '系统保留编码标志：不得删除/复用/改义',
    `creator`         VARCHAR(64)  NULL DEFAULT '',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`         VARCHAR(64)  NULL DEFAULT '',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`    DATETIME     NULL,
    `tenant_id`       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`),
    KEY `idx_status_priority` (`status`, `match_priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目模板（身份/状态/优先级）';

-- 2. 模板版本表（草稿可编辑；发布后 PUBLISHED 行应用层只读）
CREATE TABLE IF NOT EXISTS `proj_project_template_revision` (
    `id`                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '版本ID',
    `template_id`               BIGINT       NOT NULL COMMENT '模板ID',
    `revision_no`               INT          NOT NULL DEFAULT 0 COMMENT '版本号（0=草稿工作副本，发布时递增冻结）',
    `status`                    VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT草稿/PUBLISHED已发布',
    `signing_method`            VARCHAR(64)  NULL COMMENT '签约方式（字典 pms_signing_method，NULL=不限）',
    `project_category`          VARCHAR(64)  NULL COMMENT '项目类别（字典 pms_project_category，NULL=不限）',
    `implementation_method`     VARCHAR(64)  NULL COMMENT '实施方式（字典 pms_implementation_method，NULL=不限）',
    `major_project_level`       VARCHAR(64)  NULL COMMENT '重大项目级别（CRM来源属性映射，NULL=不限）',
    `process_definition_key`    VARCHAR(64)  NULL COMMENT '模板级流程定义引用（仅存引用，不校验流程内部）',
    `process_definition_version` VARCHAR(32) NULL COMMENT '流程定义版本引用',
    `validation_summary`        VARCHAR(1000) NULL COMMENT '最近一次发布校验结果摘要（留痕）',
    `published_by`              VARCHAR(64)  NULL COMMENT '发布人',
    `published_time`            DATETIME     NULL COMMENT '发布时间',
    `creator`                   VARCHAR(64)  NULL DEFAULT '',
    `create_time`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`                   VARCHAR(64)  NULL DEFAULT '',
    `update_time`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                   BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`              DATETIME     NULL,
    `tenant_id`                 BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_template_revision` (`tenant_id`, `template_id`, `revision_no`),
    KEY `idx_template_status` (`template_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目模板版本（四维条件+流程引用，发布后只读）';

-- 3. 阶段定义
CREATE TABLE IF NOT EXISTS `proj_project_template_stage_definition` (
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '阶段定义ID',
    `template_revision_id`   BIGINT       NOT NULL COMMENT '模板版本ID',
    `stage_code`             VARCHAR(32)  NOT NULL COMMENT '阶段码（S0～S6）',
    `name`                   VARCHAR(128) NOT NULL COMMENT '阶段名称',
    `sort_order`             INT          NOT NULL DEFAULT 0 COMMENT '阶段顺序',
    `entry_criteria`         VARCHAR(500) NULL COMMENT '准入条件说明',
    `exit_criteria`          VARCHAR(500) NULL COMMENT '准出条件说明',
    `creator`                VARCHAR(64)  NULL DEFAULT '',
    `create_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`                VARCHAR(64)  NULL DEFAULT '',
    `update_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`           DATETIME     NULL,
    `tenant_id`              BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_revision_stage` (`template_revision_id`, `stage_code`),
    KEY `idx_revision` (`template_revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目模板阶段定义';

-- 4. 任务定义
CREATE TABLE IF NOT EXISTS `proj_project_template_task_definition` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务定义ID',
    `template_revision_id`  BIGINT       NOT NULL COMMENT '模板版本ID',
    `task_code`             VARCHAR(64)  NOT NULL COMMENT '任务码（版本内唯一）',
    `name`                  VARCHAR(128) NOT NULL COMMENT '任务名称',
    `parent_task_code`      VARCHAR(64)  NULL COMMENT '父任务码（NULL=顶层）',
    `stage_code`            VARCHAR(32)  NOT NULL COMMENT '所属阶段码',
    `priority`              INT          NOT NULL DEFAULT 2 COMMENT '优先级',
    `sort_order`            INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `estimated_hours`       DECIMAL(6,1) NULL COMMENT '预估工时',
    `satisfaction_timing`   VARCHAR(32)  NULL COMMENT '满意度适用时点（NULL=不适用，由ACC-02消费）',
    `description`           VARCHAR(500) NULL COMMENT '任务说明',
    `creator`               VARCHAR(64)  NULL DEFAULT '',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`               VARCHAR(64)  NULL DEFAULT '',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`               BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`          DATETIME     NULL,
    `tenant_id`             BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_revision_task` (`template_revision_id`, `task_code`),
    KEY `idx_revision` (`template_revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目模板任务定义';

-- 5. 里程碑定义
CREATE TABLE IF NOT EXISTS `proj_project_template_milestone_definition` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '里程碑定义ID',
    `template_revision_id`  BIGINT       NOT NULL COMMENT '模板版本ID',
    `milestone_code`        VARCHAR(64)  NOT NULL COMMENT '里程碑码（版本内唯一）',
    `name`                  VARCHAR(128) NOT NULL COMMENT '里程碑名称',
    `stage_code`            VARCHAR(32)  NOT NULL COMMENT '所属阶段码',
    `timing`                VARCHAR(64)  NULL COMMENT '时点说明',
    `criteria`              VARCHAR(500) NULL COMMENT '达成标准',
    `creator`               VARCHAR(64)  NULL DEFAULT '',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`               VARCHAR(64)  NULL DEFAULT '',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`               BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`          DATETIME     NULL,
    `tenant_id`             BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_revision_milestone` (`template_revision_id`, `milestone_code`),
    KEY `idx_revision` (`template_revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目模板里程碑定义';

-- 6. 交付件定义
CREATE TABLE IF NOT EXISTS `proj_project_template_deliverable_definition` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '交付件定义ID',
    `template_revision_id`  BIGINT       NOT NULL COMMENT '模板版本ID',
    `deliverable_code`      VARCHAR(64)  NOT NULL COMMENT '交付件码（版本内唯一）',
    `name`                  VARCHAR(128) NOT NULL COMMENT '交付件名称',
    `stage_code`            VARCHAR(32)  NOT NULL COMMENT '所属阶段码',
    `task_code`             VARCHAR(64)  NULL COMMENT '关联任务码（NULL=阶段级）',
    `required`              BIT(1)       NOT NULL DEFAULT b'1' COMMENT '必需标志',
    `creator`               VARCHAR(64)  NULL DEFAULT '',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`               VARCHAR(64)  NULL DEFAULT '',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`               BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`          DATETIME     NULL,
    `tenant_id`             BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_revision_deliverable` (`template_revision_id`, `deliverable_code`),
    KEY `idx_revision` (`template_revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目模板交付件定义';

-- 7. 门禁定义
CREATE TABLE IF NOT EXISTS `proj_project_template_gate_definition` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '门禁定义ID',
    `template_revision_id`  BIGINT       NOT NULL COMMENT '模板版本ID',
    `gate_code`             VARCHAR(64)  NOT NULL COMMENT '门禁码（版本内唯一）',
    `name`                  VARCHAR(128) NOT NULL COMMENT '门禁名称',
    `gate_type`             VARCHAR(16)  NOT NULL COMMENT '类型：ENTRY准入/EXIT准出',
    `stage_code`            VARCHAR(32)  NOT NULL COMMENT '所属阶段码',
    `description`           VARCHAR(500) NULL COMMENT '门禁说明',
    `creator`               VARCHAR(64)  NULL DEFAULT '',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`               VARCHAR(64)  NULL DEFAULT '',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`               BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`          DATETIME     NULL,
    `tenant_id`             BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_revision_gate` (`template_revision_id`, `gate_code`),
    KEY `idx_revision` (`template_revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目模板门禁定义';

-- 8. 门禁引用行（任务/交付件/状态/流程的结构化引用，发布校验据此逐项存在性检查）
CREATE TABLE IF NOT EXISTS `proj_project_template_gate_reference` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '门禁引用ID',
    `template_revision_id`  BIGINT       NOT NULL COMMENT '模板版本ID',
    `gate_code`             VARCHAR(64)  NOT NULL COMMENT '所属门禁码',
    `ref_type`              VARCHAR(16)  NOT NULL COMMENT '引用类型：TASK/DELIVERABLE/STATE/PROCESS',
    `ref_code`              VARCHAR(64)  NOT NULL COMMENT '引用编码',
    `ref_version`           VARCHAR(32)  NULL COMMENT '引用版本（流程引用时使用）',
    `creator`               VARCHAR(64)  NULL DEFAULT '',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`               VARCHAR(64)  NULL DEFAULT '',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`               BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`          DATETIME     NULL,
    `tenant_id`             BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_revision_gate_ref` (`template_revision_id`, `gate_code`, `ref_type`, `ref_code`),
    KEY `idx_revision` (`template_revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目模板门禁引用行';

-- =============================================================================
-- 四维匹配字典（PRD V1.7 3.2/4.2.3：四维独立引用字典，不拼装混合字段）
-- 重大项目级别为 CRM 权威来源属性映射，初始不预置取值，经来源映射受控扩展。
-- =============================================================================
INSERT IGNORE INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`) VALUES
('PMS-签约方式', 'pms_signing_method', 0, '模板匹配维度：直签/非直签', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
('PMS-项目类别', 'pms_project_category', 0, '模板匹配维度：普通/工程', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
('PMS-实施方式', 'pms_implementation_method', 0, '模板匹配维度：原厂直服/原厂督导/代理商自服', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
('PMS-重大项目级别', 'pms_major_project_level', 0, 'CRM权威来源属性映射，经来源映射受控扩展，不预置取值', 'admin', NOW(), 'admin', NOW(), b'0', NULL);

INSERT IGNORE INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, '直签', 'DIRECT_SIGN', 'pms_signing_method', 0, 'primary', '', '原厂直接与客户签约', 'admin', NOW(), 'admin', NOW(), b'0'),
(2, '非直签', 'CHANNEL_SIGN', 'pms_signing_method', 0, 'info', '', '渠道与客户签约，渠道交付', 'admin', NOW(), 'admin', NOW(), b'0'),
(1, '普通类', 'GENERAL', 'pms_project_category', 0, 'primary', '', '普通交付项目', 'admin', NOW(), 'admin', NOW(), b'0'),
(2, '工程类', 'ENGINEERING', 'pms_project_category', 0, 'warning', '', '含启动会等工程里程碑', 'admin', NOW(), 'admin', NOW(), b'0'),
(1, '原厂直服', 'DIRECT_SERVICE', 'pms_implementation_method', 0, 'primary', '', '原厂提供实施服务', 'admin', NOW(), 'admin', NOW(), b'0'),
(2, '原厂督导', 'SUPERVISION', 'pms_implementation_method', 0, 'success', '', '原厂督导渠道实施', 'admin', NOW(), 'admin', NOW(), b'0'),
(3, '代理商自服', 'AGENT_SELF_SERVICE', 'pms_implementation_method', 0, 'info', '', '代理商自行实施', 'admin', NOW(), 'admin', NOW(), b'0');

-- =============================================================================
-- 菜单：项目模板管理（18060 页面 + 18061～18066 按钮；18053+ 段已核对空闲）
-- =============================================================================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(18060, '项目模板管理', 'pms:project-template:query', 2, 60, 18000, 'project-templates', 'ep:document-copy', 'pms/project/project-templates/index', 'PmsProjectTemplate', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18061, '模板查询', 'pms:project-template:query', 3, 1, 18060, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18062, '模板创建', 'pms:project-template:create', 3, 2, 18060, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18063, '模板更新', 'pms:project-template:update', 3, 3, 18060, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18064, '模板删除', 'pms:project-template:delete', 3, 4, 18060, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18065, '模板发布', 'pms:project-template:publish', 3, 5, 18060, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18066, '模板停用', 'pms:project-template:disable', 3, 6, 18060, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`), `update_time`=NOW(), `deleted`=b'0';
