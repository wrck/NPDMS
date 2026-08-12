-- =============================================================================
-- V30: 单机风险识别 + 技术公告与停产停维预检查 + 授权与借货准备
-- FR-ENG-008: 单机风险识别与反馈（risk）
-- FR-ENG-009: 技术公告与停产停维预检查（announcement + announcement_check）
-- FR-ENG-010: 授权与借货准备（authorization）
-- 父菜单 18000，菜单 ID 19221~19246（V27 已使用 19196~19220）
-- =============================================================================

-- ========== 1. 单机风险 pms_eng_risk ==========
CREATE TABLE IF NOT EXISTS `pms_eng_risk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` VARCHAR(64) NOT NULL COMMENT '风险编号（如 RK-2026-001）',
  `project_id` BIGINT NOT NULL COMMENT '关联项目ID',
  `name` VARCHAR(200) NOT NULL COMMENT '风险名称',
  `risk_type` VARCHAR(32) NOT NULL DEFAULT 'SINGLE_DEVICE' COMMENT '风险类型：SINGLE_DEVICE单机/SCENARIO场景',
  `device_id` BIGINT DEFAULT NULL COMMENT '关联设备ID',
  `device_serial` VARCHAR(64) DEFAULT NULL COMMENT '设备序列号',
  `device_model` VARCHAR(128) DEFAULT NULL COMMENT '设备型号',
  `scenario` TEXT COMMENT '风险场景描述',
  `risk_level` VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' COMMENT '风险等级：HIGH高/MEDIUM中/LOW低',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0草稿/1已识别/2已确认/3已同步CRM/4已关闭',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `crm_synced` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否已同步CRM',
  `crm_sync_time` DATETIME DEFAULT NULL COMMENT 'CRM同步时间',
  `handler_user_id` BIGINT DEFAULT NULL COMMENT '处理人',
  `handle_opinion` VARCHAR(500) DEFAULT NULL COMMENT '处理意见',
  `handle_time` DATETIME DEFAULT NULL COMMENT '处理时间',
  `creator_user_id` BIGINT DEFAULT NULL COMMENT '创建人',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `creator` VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pms_eng_risk_code` (`code`, `tenant_id`),
  KEY `idx_pms_eng_risk_project` (`project_id`),
  KEY `idx_pms_eng_risk_status` (`status`),
  KEY `idx_pms_eng_risk_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单机风险识别';

-- ========== 2. 技术公告 pms_eng_announcement ==========
CREATE TABLE IF NOT EXISTS `pms_eng_announcement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` VARCHAR(64) NOT NULL COMMENT '公告编号（如 TA-2026-001）',
  `title` VARCHAR(200) NOT NULL COMMENT '公告标题',
  `announcement_type` VARCHAR(32) NOT NULL DEFAULT 'TECH_NOTICE' COMMENT '公告类型：TECH_NOTICE技术公告/EOS停产/EOM停维',
  `product_model` VARCHAR(128) DEFAULT NULL COMMENT '适用设备型号',
  `affected_versions` TEXT COMMENT '影响版本范围JSON数组',
  `publish_date` DATE DEFAULT NULL COMMENT '发布日期',
  `effective_date` DATE DEFAULT NULL COMMENT '生效日期',
  `expire_date` DATE DEFAULT NULL COMMENT '失效日期',
  `severity` VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' COMMENT '严重等级：CRITICAL/HIGH/MEDIUM/LOW',
  `content` TEXT COMMENT '公告内容富文本',
  `handling_suggestion` TEXT COMMENT '处置建议',
  `file_url` VARCHAR(512) DEFAULT NULL COMMENT '附件URL',
  `file_name` VARCHAR(200) DEFAULT NULL COMMENT '附件名',
  `file_size` BIGINT DEFAULT NULL COMMENT '附件大小（字节）',
  `file_checksum` VARCHAR(64) DEFAULT NULL COMMENT '附件校验值',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0草稿/1已发布/2已停用',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `creator_user_id` BIGINT DEFAULT NULL COMMENT '创建人',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `creator` VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pms_eng_announcement_code` (`code`, `tenant_id`),
  KEY `idx_pms_eng_announcement_type` (`announcement_type`),
  KEY `idx_pms_eng_announcement_model` (`product_model`),
  KEY `idx_pms_eng_announcement_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技术公告与停产停维';

-- ========== 3. 公告预检查记录 pms_eng_announcement_check ==========
CREATE TABLE IF NOT EXISTS `pms_eng_announcement_check` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` VARCHAR(64) NOT NULL COMMENT '检查编号（如 PCH-2026-001）',
  `project_id` BIGINT NOT NULL COMMENT '关联项目ID',
  `announcement_id` BIGINT NOT NULL COMMENT '关联技术公告ID',
  `device_id` BIGINT DEFAULT NULL COMMENT '关联设备ID',
  `device_serial` VARCHAR(64) DEFAULT NULL COMMENT '设备序列号',
  `device_model` VARCHAR(128) DEFAULT NULL COMMENT '设备型号',
  `device_version` VARCHAR(64) DEFAULT NULL COMMENT '设备版本',
  `match_result` VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '匹配结果：HIT命中/MISS未命中/UNKNOWN未知',
  `eom_status` VARCHAR(16) DEFAULT NULL COMMENT 'EOS/EOM状态：EOS/EOM/NONE',
  `handling_suggestion` TEXT COMMENT '处置建议',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0待检查/1已检查/2已处置/3已忽略',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `checker_user_id` BIGINT DEFAULT NULL COMMENT '检查人',
  `check_time` DATETIME DEFAULT NULL COMMENT '检查时间',
  `handle_opinion` VARCHAR(500) DEFAULT NULL COMMENT '处理意见',
  `handle_time` DATETIME DEFAULT NULL COMMENT '处理时间',
  `creator_user_id` BIGINT DEFAULT NULL COMMENT '创建人',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `creator` VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pms_eng_announcement_check_code` (`code`, `tenant_id`),
  KEY `idx_pms_eng_announcement_check_project` (`project_id`),
  KEY `idx_pms_eng_announcement_check_announcement` (`announcement_id`),
  KEY `idx_pms_eng_announcement_check_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技术公告预检查记录';

-- ========== 4. 授权与借货 pms_eng_authorization ==========
CREATE TABLE IF NOT EXISTS `pms_eng_authorization` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` VARCHAR(64) NOT NULL COMMENT '授权编号（如 AUTH-2026-001）',
  `project_id` BIGINT NOT NULL COMMENT '关联项目ID',
  `name` VARCHAR(200) NOT NULL COMMENT '授权名称',
  `authorization_type` VARCHAR(32) NOT NULL DEFAULT 'TEMPORARY' COMMENT '授权类型：FORMAL正式/TEMPORARY临时/LOAN借货',
  `device_id` BIGINT DEFAULT NULL COMMENT '关联设备ID',
  `device_serial` VARCHAR(64) DEFAULT NULL COMMENT '设备序列号',
  `device_model` VARCHAR(128) DEFAULT NULL COMMENT '设备型号',
  `license_key` VARCHAR(256) DEFAULT NULL COMMENT '授权密钥',
  `license_type` VARCHAR(64) DEFAULT NULL COMMENT '授权类型描述',
  `apply_start_date` DATE DEFAULT NULL COMMENT '申请开始日期',
  `apply_end_date` DATE DEFAULT NULL COMMENT '申请结束日期',
  `actual_end_date` DATE DEFAULT NULL COMMENT '实际结束日期',
  `usage_limit` INT DEFAULT NULL COMMENT '使用次数限制',
  `used_count` INT NOT NULL DEFAULT 0 COMMENT '已使用次数',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0草稿/1已提交/2审批中/3已通过/4已驳回/5已撤回/6已终止',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `submit_user_id` BIGINT DEFAULT NULL COMMENT '提交人',
  `submit_time` DATETIME DEFAULT NULL COMMENT '提交时间',
  `approver_user_id` BIGINT DEFAULT NULL COMMENT '审批人',
  `approve_opinion` VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
  `approve_time` DATETIME DEFAULT NULL COMMENT '审批时间',
  `recall_user_id` BIGINT DEFAULT NULL COMMENT '撤回人',
  `recall_time` DATETIME DEFAULT NULL COMMENT '撤回时间',
  `process_instance_id` VARCHAR(64) DEFAULT NULL COMMENT 'BPM流程实例ID（预留）',
  `creator_user_id` BIGINT DEFAULT NULL COMMENT '创建人',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `creator` VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pms_eng_authorization_code` (`code`, `tenant_id`),
  KEY `idx_pms_eng_authorization_project` (`project_id`),
  KEY `idx_pms_eng_authorization_status` (`status`),
  KEY `idx_pms_eng_authorization_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='授权与借货准备';

-- =============================================================================
-- 菜单：单机风险 + 技术公告 + 公告预检查 + 授权管理（父菜单 18000）
-- ID 19221~19246，避免与 V27（19196~19220）冲突
-- =============================================================================
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- ========== 单机风险菜单（sort 78）==========
(19221, '单机风险', 'pms:eng-risk:query', 2, 78, 18000, 'eng-risk', 'ep:warning-filled',
 'pms/engineering/risk/index', 'PmsEngRisk', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19222, '风险创建', 'pms:eng-risk:create', 3, 1, 19221, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19223, '风险修改', 'pms:eng-risk:update', 3, 2, 19221, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19224, '风险删除', 'pms:eng-risk:delete', 3, 3, 19221, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19225, '风险确认', 'pms:eng-risk:confirm', 3, 4, 19221, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19226, '风险同步CRM', 'pms:eng-risk:sync', 3, 5, 19221, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19227, '风险关闭', 'pms:eng-risk:close', 3, 6, 19221, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 技术公告菜单（sort 79）==========
(19228, '技术公告', 'pms:eng-announcement:query', 2, 79, 18000, 'eng-announcement', 'ep:bell',
 'pms/engineering/announcement/index', 'PmsEngAnnouncement', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19229, '公告创建', 'pms:eng-announcement:create', 3, 1, 19228, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19230, '公告修改', 'pms:eng-announcement:update', 3, 2, 19228, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19231, '公告删除', 'pms:eng-announcement:delete', 3, 3, 19228, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19232, '公告发布', 'pms:eng-announcement:publish', 3, 4, 19228, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19233, '公告停用', 'pms:eng-announcement:disable', 3, 5, 19228, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 公告预检查菜单（sort 80）==========
(19234, '公告预检查', 'pms:eng-announcement-check:query', 2, 80, 18000, 'eng-announcement-check', 'ep:filter',
 'pms/engineering/announcement-check/index', 'PmsEngAnnouncementCheck', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19235, '检查创建', 'pms:eng-announcement-check:create', 3, 1, 19234, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19236, '检查修改', 'pms:eng-announcement-check:update', 3, 2, 19234, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19237, '检查删除', 'pms:eng-announcement-check:delete', 3, 3, 19234, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19238, '检查处置', 'pms:eng-announcement-check:handle', 3, 4, 19234, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 授权管理菜单（sort 81）==========
(19239, '授权管理', 'pms:eng-authorization:query', 2, 81, 18000, 'eng-authorization', 'ep:key',
 'pms/engineering/authorization/index', 'PmsEngAuthorization', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19240, '授权创建', 'pms:eng-authorization:create', 3, 1, 19239, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19241, '授权修改', 'pms:eng-authorization:update', 3, 2, 19239, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19242, '授权删除', 'pms:eng-authorization:delete', 3, 3, 19239, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19243, '授权提交', 'pms:eng-authorization:submit', 3, 4, 19239, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19244, '授权审批', 'pms:eng-authorization:audit', 3, 5, 19239, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19245, '授权撤回', 'pms:eng-authorization:recall', 3, 6, 19239, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19246, '授权终止', 'pms:eng-authorization:terminate', 3, 7, 19239, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
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
  AND m.id BETWEEN 19221 AND 19246;
