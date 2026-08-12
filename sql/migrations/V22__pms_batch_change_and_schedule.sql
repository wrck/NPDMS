-- =============================================================================
-- V22: T-V2-PROJ-002 人员角色批量变更（FR-PROJ-014）+ 工期倒排与合理性校验（FR-PROJ-018）
-- 表：pms_team_batch_change / pms_team_batch_change_item
--      pms_schedule_backward / pms_schedule_backward_item
-- 菜单：批量变更、工期倒排（父菜单 18000），ID 19140~19150
-- 注：V21 已使用 19130~19138，本迁移顺延避免冲突
-- =============================================================================

-- 1. 批量变更批次表（FR-PROJ-014）
CREATE TABLE pms_team_batch_change (
  id bigint NOT NULL AUTO_INCREMENT,
  batch_no varchar(64) NOT NULL COMMENT '批次编号，全局唯一',
  source_user_id bigint NOT NULL COMMENT '源用户编号',
  target_user_id bigint NOT NULL COMMENT '目标用户编号',
  scope_type varchar(16) NOT NULL DEFAULT 'SELECTED' COMMENT '范围类型 ALL 全部项目 / SELECTED 指定项目',
  reason varchar(500) DEFAULT NULL COMMENT '变更原因',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0处理中 1成功 2部分成功 3失败',
  total_count int NOT NULL DEFAULT 0 COMMENT '总条数',
  success_count int NOT NULL DEFAULT 0 COMMENT '成功条数',
  failure_count int NOT NULL DEFAULT 0 COMMENT '失败条数',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_team_batch_change_no (batch_no),
  KEY idx_pms_team_batch_change_source (source_user_id),
  KEY idx_pms_team_batch_change_target (target_user_id),
  KEY idx_pms_team_batch_change_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS 团队批量变更批次';

-- 2. 批量变更明细表（FR-PROJ-014）
CREATE TABLE pms_team_batch_change_item (
  id bigint NOT NULL AUTO_INCREMENT,
  batch_id bigint NOT NULL COMMENT '批次编号',
  project_id bigint NOT NULL COMMENT '项目编号',
  project_name varchar(128) DEFAULT NULL COMMENT '项目名称（冗余）',
  team_member_id bigint NOT NULL COMMENT '团队成员编号',
  before_role varchar(64) DEFAULT NULL COMMENT '变更前角色编码',
  after_role varchar(64) DEFAULT NULL COMMENT '变更后角色编码',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0待处理 1成功 2失败',
  error_message varchar(500) DEFAULT NULL COMMENT '失败原因',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_pms_team_batch_item_batch (batch_id),
  KEY idx_pms_team_batch_item_project (project_id),
  KEY idx_pms_team_batch_item_member (team_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS 团队批量变更明细';

-- 3. 工期倒排记录表（FR-PROJ-018）
CREATE TABLE pms_schedule_backward (
  id bigint NOT NULL AUTO_INCREMENT,
  project_id bigint NOT NULL COMMENT '项目编号',
  target_date date NOT NULL COMMENT '目标完工日期',
  project_type varchar(16) NOT NULL DEFAULT 'DIRECT' COMMENT '项目类型 DIRECT 直签 / INDIRECT 非直签',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已计算 2已应用 3已驳回',
  conflict_summary varchar(1000) DEFAULT NULL COMMENT '冲突汇总',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_pms_schedule_backward_project (project_id),
  KEY idx_pms_schedule_backward_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS 工期倒排记录';

-- 4. 倒排阶段明细表（FR-PROJ-018）
CREATE TABLE pms_schedule_backward_item (
  id bigint NOT NULL AUTO_INCREMENT,
  backward_id bigint NOT NULL COMMENT '倒排记录编号',
  phase_id bigint DEFAULT NULL COMMENT '项目阶段编号',
  phase_name varchar(128) DEFAULT NULL COMMENT '阶段名称',
  planned_start_date date DEFAULT NULL COMMENT '计划开始日期',
  planned_end_date date DEFAULT NULL COMMENT '计划结束日期',
  recommended_latest_date date DEFAULT NULL COMMENT '建议最晚日期',
  has_conflict bit(1) NOT NULL DEFAULT b'0' COMMENT '是否存在冲突',
  conflict_reason varchar(500) DEFAULT NULL COMMENT '冲突原因',
  sort int NOT NULL DEFAULT 0 COMMENT '阶段排序',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_pms_schedule_backward_item_backward (backward_id),
  KEY idx_pms_schedule_backward_item_phase (phase_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS 工期倒排阶段明细';

-- =============================================================================
-- 菜单：批量变更 + 工期倒排（父菜单 18000）
-- ID 19140~19150
-- =============================================================================
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- ========== 批量变更菜单（sort 93）==========
(19140, '批量变更', 'pms:team-batch-change:query', 2, 93, 18000, 'batch-change', 'ep:swap',
 'pms/project/batch-change/index', 'PmsTeamBatchChange', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19141, '批量变更创建', 'pms:team-batch-change:create', 3, 1, 19140, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19142, '批量变更修改', 'pms:team-batch-change:update', 3, 2, 19140, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19143, '批量变更删除', 'pms:team-batch-change:delete', 3, 3, 19140, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19144, '批量变更执行', 'pms:team-batch-change:execute', 3, 4, 19140, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 工期倒排菜单（sort 94）==========
(19145, '工期倒排', 'pms:schedule-backward:query', 2, 94, 18000, 'schedule-backward', 'ep:calendar',
 'pms/project/schedule-backward/index', 'PmsScheduleBackward', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19146, '工期倒排创建', 'pms:schedule-backward:create', 3, 1, 19145, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19147, '工期倒排修改', 'pms:schedule-backward:update', 3, 2, 19145, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19148, '工期倒排删除', 'pms:schedule-backward:delete', 3, 3, 19145, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19149, '工期倒排计算', 'pms:schedule-backward:calculate', 3, 4, 19145, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19150, '工期倒排应用', 'pms:schedule-backward:apply', 3, 5, 19145, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
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
  AND m.id BETWEEN 19140 AND 19150;
