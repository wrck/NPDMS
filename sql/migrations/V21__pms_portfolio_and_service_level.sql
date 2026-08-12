-- =============================================================================
-- V21: T-V2-PROJ-001 项目组合管理（FR-PROJ-001）+ 客户服务等级管理（FR-PROJ-006）
-- 表：pms_project_portfolio / pms_project_portfolio_member / pms_project_portfolio_rule / pms_customer_service_level
-- 菜单：项目组合、服务等级（父菜单 18000）
-- 注：菜单 ID 19130~19145（V18 已使用 19094~19129，本迁移顺延避免冲突）
-- =============================================================================

-- 1. 项目组合主表（FR-PROJ-001）
CREATE TABLE pms_project_portfolio (
  id bigint NOT NULL AUTO_INCREMENT,
  code varchar(64) NOT NULL COMMENT '组合编码，全局唯一',
  name varchar(128) NOT NULL COMMENT '组合名称',
  purpose varchar(500) DEFAULT NULL COMMENT '组合用途（战略/客户/区域/计划/专项）',
  owner_user_id bigint DEFAULT NULL COMMENT '负责人用户编号',
  valid_from date DEFAULT NULL COMMENT '有效期开始',
  valid_to date DEFAULT NULL COMMENT '有效期结束',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2已归档',
  target_metrics text DEFAULT NULL COMMENT '统计目标（JSON 文本）',
  member_type varchar(16) NOT NULL DEFAULT 'STATIC' COMMENT '成员类型 STATIC 静态 / DYNAMIC 动态',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_portfolio_code (code),
  KEY idx_pms_portfolio_owner (owner_user_id),
  KEY idx_pms_portfolio_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS项目组合';

-- 2. 组合成员表（FR-PROJ-001）
CREATE TABLE pms_project_portfolio_member (
  id bigint NOT NULL AUTO_INCREMENT,
  portfolio_id bigint NOT NULL COMMENT '组合编号',
  project_id bigint NOT NULL COMMENT '项目编号',
  inclusion_type varchar(16) NOT NULL DEFAULT 'STATIC' COMMENT '纳入类型 STATIC 静态 / DYNAMIC 动态',
  inclusion_reason varchar(500) DEFAULT NULL COMMENT '纳入原因',
  exclusion_reason varchar(500) DEFAULT NULL COMMENT '排除原因',
  status tinyint NOT NULL DEFAULT 1 COMMENT '1纳入 2排除',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_portfolio_member (portfolio_id, project_id),
  KEY idx_pms_portfolio_member_project (project_id),
  KEY idx_pms_portfolio_member_status (portfolio_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS项目组合成员';

-- 3. 动态规则表（FR-PROJ-001）
CREATE TABLE pms_project_portfolio_rule (
  id bigint NOT NULL AUTO_INCREMENT,
  portfolio_id bigint NOT NULL COMMENT '组合编号',
  rule_field varchar(32) NOT NULL COMMENT '规则字段 CUSTOMER/REGION/TYPE/STATUS',
  rule_operator varchar(16) NOT NULL DEFAULT 'EQ' COMMENT '规则操作符 EQ/NE/IN/LIKE',
  rule_value varchar(500) NOT NULL COMMENT '规则值（IN 用逗号分隔）',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_pms_portfolio_rule_portfolio (portfolio_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS项目组合动态规则';

-- 4. 客户服务等级表（FR-PROJ-006）
CREATE TABLE pms_customer_service_level (
  id bigint NOT NULL AUTO_INCREMENT,
  customer_id bigint NOT NULL COMMENT '客户编号',
  level varchar(16) NOT NULL COMMENT '服务等级 STRATEGIC 战略 / IMPORTANT 重要 / STANDARD 标准 / GENERAL 一般',
  valid_from date DEFAULT NULL COMMENT '生效开始日期',
  valid_to date DEFAULT NULL COMMENT '生效结束日期',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已生效 2已停用 3已归档',
  response_time_hours int DEFAULT NULL COMMENT '响应时间（小时）',
  proactive_service bit(1) NOT NULL DEFAULT b'0' COMMENT '是否主动服务',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_pms_service_level_customer (customer_id),
  KEY idx_pms_service_level_status (customer_id, status),
  KEY idx_pms_service_level_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS客户服务等级';

-- =============================================================================
-- 菜单：项目组合 + 服务等级（父菜单 18000）
-- ID 19130~19145
-- =============================================================================
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- ========== 项目组合菜单（sort 91）==========
(19130, '项目组合', 'pms:portfolio:query', 2, 91, 18000, 'portfolio', 'ep:folder-opened',
 'pms/project/portfolio/index', 'PmsProjectPortfolio', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19131, '组合创建', 'pms:portfolio:create', 3, 1, 19130, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19132, '组合修改', 'pms:portfolio:update', 3, 2, 19130, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19133, '组合删除', 'pms:portfolio:delete', 3, 3, 19130, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19134, '组合发布', 'pms:portfolio:publish', 3, 4, 19130, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 服务等级菜单（sort 92）==========
(19135, '服务等级', 'pms:service-level:query', 2, 92, 18000, 'service-level', 'ep:medal',
 'pms/project/service-level/index', 'PmsCustomerServiceLevel', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19136, '等级创建', 'pms:service-level:create', 3, 1, 19135, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19137, '等级修改', 'pms:service-level:update', 3, 2, 19135, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19138, '等级删除', 'pms:service-level:delete', 3, 3, 19135, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `permission` = VALUES(`permission`), `path` = VALUES(`path`),
 `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
 `parent_id` = VALUES(`parent_id`), `type` = VALUES(`type`), `sort` = VALUES(`sort`),
 `icon` = VALUES(`icon`), `update_time` = NOW(), `deleted` = b'0';

-- 将新增菜单分配给超级管理员角色（role_id=1）
INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, m.id, 'admin', NOW(), 'admin', NOW(), b'0'
FROM `system_menu` m
WHERE m.deleted = b'0'
  AND m.id BETWEEN 19130 AND 19138;
