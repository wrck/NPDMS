-- =============================================================================
-- V27: 工程交底书生成 + 准备数据动态表单
-- FR-ENG-006: 工程交底书生成（briefing）
-- FR-ENG-007: 准备数据动态表单（form_template + form_instance）
-- 父菜单 18000，菜单 ID 19196~19220
-- =============================================================================

-- ========== 1. 工程交底书 pms_eng_briefing ==========
CREATE TABLE IF NOT EXISTS `pms_eng_briefing` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` VARCHAR(64) NOT NULL COMMENT '交底书编号（如 BR-2026-001）',
  `project_id` BIGINT NOT NULL COMMENT '关联项目ID',
  `name` VARCHAR(200) NOT NULL COMMENT '交底书名称',
  `briefing_type` VARCHAR(32) NOT NULL DEFAULT 'STANDARD' COMMENT '交底类型：STANDARD标准/EMERGENCY紧急/CUSTOM自定义',
  `template_id` BIGINT DEFAULT NULL COMMENT '关联交底书模板ID',
  `template_snapshot` TEXT COMMENT '模板快照JSON（模板版本固定到实例）',
  `source_snapshot` TEXT COMMENT '前序基线数据快照JSON（需求/方案/工勘聚合）',
  `content` TEXT COMMENT '交底内容富文本',
  `file_url` VARCHAR(512) DEFAULT NULL COMMENT '生成的文件URL',
  `file_name` VARCHAR(200) DEFAULT NULL COMMENT '文件名',
  `file_size` BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
  `file_checksum` VARCHAR(64) DEFAULT NULL COMMENT '文件校验值',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0草稿/1已生成/2已审核/3已发布/4已作废',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `generate_time` DATETIME DEFAULT NULL COMMENT '生成时间',
  `publish_time` DATETIME DEFAULT NULL COMMENT '发布时间',
  `approver_user_id` BIGINT DEFAULT NULL COMMENT '审核人',
  `approve_opinion` VARCHAR(500) DEFAULT NULL COMMENT '审核意见',
  `approve_time` DATETIME DEFAULT NULL COMMENT '审核时间',
  `creator_user_id` BIGINT DEFAULT NULL COMMENT '编制人',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `creator` VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pms_eng_briefing_code` (`code`, `tenant_id`),
  KEY `idx_pms_eng_briefing_project` (`project_id`),
  KEY `idx_pms_eng_briefing_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工程交底书';

-- ========== 2. 准备数据表单模板 pms_eng_form_template ==========
CREATE TABLE IF NOT EXISTS `pms_eng_form_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` VARCHAR(64) NOT NULL COMMENT '模板编号（如 FT-2026-001）',
  `name` VARCHAR(200) NOT NULL COMMENT '模板名称',
  `product_type` VARCHAR(64) DEFAULT NULL COMMENT '产品类型（联动条件）',
  `conf` TEXT NOT NULL COMMENT '表单配置JSON（form-create conf）',
  `fields` TEXT NOT NULL COMMENT '表单字段JSON（form-create fields）',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '模板说明',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0草稿/1已发布/2已停用',
  `version` INT NOT NULL DEFAULT 0 COMMENT '模板版本号',
  `creator` VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pms_eng_form_template_code` (`code`, `tenant_id`),
  KEY `idx_pms_eng_form_template_product` (`product_type`),
  KEY `idx_pms_eng_form_template_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='准备数据表单模板';

-- ========== 3. 表单实例 pms_eng_form_instance ==========
CREATE TABLE IF NOT EXISTS `pms_eng_form_instance` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` VARCHAR(64) NOT NULL COMMENT '实例编号（如 FI-2026-001）',
  `project_id` BIGINT NOT NULL COMMENT '关联项目ID',
  `template_id` BIGINT NOT NULL COMMENT '关联模板ID',
  `template_snapshot` TEXT NOT NULL COMMENT '模板快照JSON（版本固定到实例）',
  `form_data` TEXT COMMENT '填报数据JSON',
  `name` VARCHAR(200) DEFAULT NULL COMMENT '实例名称',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0待填/1已填/2已提交/3已审核/4已驳回',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `submit_time` DATETIME DEFAULT NULL COMMENT '提交时间',
  `approver_user_id` BIGINT DEFAULT NULL COMMENT '审核人',
  `approve_opinion` VARCHAR(500) DEFAULT NULL COMMENT '审核意见',
  `approve_time` DATETIME DEFAULT NULL COMMENT '审核时间',
  `filler_user_id` BIGINT DEFAULT NULL COMMENT '填报人',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `creator` VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pms_eng_form_instance_code` (`code`, `tenant_id`),
  KEY `idx_pms_eng_form_instance_project` (`project_id`),
  KEY `idx_pms_eng_form_instance_template` (`template_id`),
  KEY `idx_pms_eng_form_instance_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='准备数据表单实例';

-- =============================================================================
-- 菜单：工程交底书 + 表单模板 + 表单实例（父菜单 18000）
-- ID 19196~19220，避免与 V25（19171~19195）冲突
-- =============================================================================
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- ========== 工程交底书菜单（sort 75）==========
(19196, '工程交底', 'pms:eng-briefing:query', 2, 75, 18000, 'eng-briefing', 'ep:document',
 'pms/engineering/briefing/index', 'PmsEngBriefing', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19197, '交底创建', 'pms:eng-briefing:create', 3, 1, 19196, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19198, '交底修改', 'pms:eng-briefing:update', 3, 2, 19196, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19199, '交底删除', 'pms:eng-briefing:delete', 3, 3, 19196, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19200, '交底生成', 'pms:eng-briefing:generate', 3, 4, 19196, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19201, '交底审核', 'pms:eng-briefing:audit', 3, 5, 19196, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19202, '交底发布', 'pms:eng-briefing:publish', 3, 6, 19196, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 准备数据表单模板菜单（sort 76）==========
(19203, '表单模板', 'pms:eng-form-template:query', 2, 76, 18000, 'eng-form-template', 'ep:form',
 'pms/engineering/form-template/index', 'PmsEngFormTemplate', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19204, '模板创建', 'pms:eng-form-template:create', 3, 1, 19203, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19205, '模板修改', 'pms:eng-form-template:update', 3, 2, 19203, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19206, '模板删除', 'pms:eng-form-template:delete', 3, 3, 19203, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19207, '模板发布', 'pms:eng-form-template:publish', 3, 4, 19203, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 表单实例菜单（sort 77）==========
(19208, '表单填报', 'pms:eng-form-instance:query', 2, 77, 18000, 'eng-form-instance', 'ep:edit-pen',
 'pms/engineering/form-instance/index', 'PmsEngFormInstance', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19209, '实例创建', 'pms:eng-form-instance:create', 3, 1, 19208, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19210, '实例修改', 'pms:eng-form-instance:update', 3, 2, 19208, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19211, '实例删除', 'pms:eng-form-instance:delete', 3, 3, 19208, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19212, '实例提交', 'pms:eng-form-instance:submit', 3, 4, 19208, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19213, '实例审核', 'pms:eng-form-instance:audit', 3, 5, 19208, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
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
  AND m.id BETWEEN 19196 AND 19213;
