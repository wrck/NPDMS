-- =============================================================================
-- V23: T-V2-PROJ-003 项目计划变更审批（FR-PROJ-020）+ 项目回退与直接关闭（FR-PROJ-022）
-- 表：pms_plan_change_request / pms_plan_change_phase_snapshot
--      pms_project_governance_action
-- 菜单：计划变更审批、项目治理（父菜单 18000），ID 19151~19162
-- 注：V22 已使用 19140~19150，本迁移顺延避免冲突
-- =============================================================================

-- 1. 计划变更审批主表（FR-PROJ-020）
-- 状态机：0草稿 → 1已提交 → 2审批中 → 3已通过 → 4已驳回 → 5已撤回 → 6已终止
-- 通过后生成新基线，未通过恢复为可修订状态
CREATE TABLE pms_plan_change_request (
  id bigint NOT NULL AUTO_INCREMENT,
  project_id bigint NOT NULL COMMENT '所属项目编号',
  change_no varchar(64) NOT NULL COMMENT '变更单号，全局唯一',
  title varchar(200) NOT NULL COMMENT '变更标题',
  change_type varchar(32) NOT NULL DEFAULT 'PLAN_ADJUST' COMMENT '变更类型 PLAN_ADJUST 计划调整 / SCOPE_CHANGE 范围变更 / DATE_SHIFT 工期顺延 / OTHER 其他',
  reason varchar(2000) NOT NULL COMMENT '变更原因',
  customer_proof_files varchar(2000) DEFAULT NULL COMMENT '客户证明材料文件URL列表（JSON数组）',
  applicant_user_id bigint NOT NULL COMMENT '申请人编号',
  apply_time datetime NOT NULL COMMENT '申请时间',
  approver_user_id bigint DEFAULT NULL COMMENT '审批人编号',
  approve_time datetime DEFAULT NULL COMMENT '审批时间',
  approve_opinion varchar(1000) DEFAULT NULL COMMENT '审批意见',
  approve_action varchar(32) DEFAULT NULL COMMENT '审批动作 PASS 通过 / REJECT 驳回 / RETURN 退回 / TRANSFER 转办 / COUNTERSIGN 加签',
  baseline_version int NOT NULL DEFAULT 0 COMMENT '当前基线版本号',
  new_baseline_version int DEFAULT NULL COMMENT '审批通过后生成的新基线版本号',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已提交 2审批中 3已通过 4已驳回 5已撤回 6已终止',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_plan_change_no (change_no),
  KEY idx_pms_plan_change_project (project_id),
  KEY idx_pms_plan_change_status (status),
  KEY idx_pms_plan_change_applicant (applicant_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS 项目计划变更审批';

-- 2. 阶段计划快照表（FR-PROJ-020）
-- 记录变更前后阶段计划时间，便于版本回溯和差异比较
CREATE TABLE pms_plan_change_phase_snapshot (
  id bigint NOT NULL AUTO_INCREMENT,
  change_request_id bigint NOT NULL COMMENT '变更申请编号',
  phase_id bigint NOT NULL COMMENT '项目阶段编号',
  phase_name varchar(128) DEFAULT NULL COMMENT '阶段名称（冗余）',
  before_plan_start datetime DEFAULT NULL COMMENT '变更前计划开始时间',
  before_plan_end datetime DEFAULT NULL COMMENT '变更前计划结束时间',
  after_plan_start datetime DEFAULT NULL COMMENT '变更后计划开始时间',
  after_plan_end datetime DEFAULT NULL COMMENT '变更后计划结束时间',
  change_remark varchar(500) DEFAULT NULL COMMENT '阶段变更说明',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_pms_plan_change_snap_request (change_request_id),
  KEY idx_pms_plan_change_snap_phase (phase_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS 计划变更阶段快照';

-- 3. 项目治理动作表（FR-PROJ-022）
-- 支持项目回退（退回总部重新指派）和直接关闭
-- action_type: ROLLBACK 回退总部 / DIRECT_CLOSE 直接关闭
-- 状态机：0草稿 → 1已提交 → 2已审批中 → 3已执行 → 4已驳回 → 5已撤回
CREATE TABLE pms_project_governance_action (
  id bigint NOT NULL AUTO_INCREMENT,
  project_id bigint NOT NULL COMMENT '所属项目编号',
  action_no varchar(64) NOT NULL COMMENT '治理动作单号，全局唯一',
  action_type varchar(32) NOT NULL COMMENT '动作类型 ROLLBACK 回退总部 / DIRECT_CLOSE 直接关闭',
  reason varchar(2000) NOT NULL COMMENT '回退/关闭原因',
  proof_files varchar(2000) DEFAULT NULL COMMENT '证明材料文件URL列表（JSON数组）',
  applicant_user_id bigint NOT NULL COMMENT '申请人编号',
  apply_time datetime NOT NULL COMMENT '申请时间',
  approver_user_id bigint DEFAULT NULL COMMENT '审批人编号',
  approve_time datetime DEFAULT NULL COMMENT '审批时间',
  approve_opinion varchar(1000) DEFAULT NULL COMMENT '审批意见',
  before_project_status int DEFAULT NULL COMMENT '执行前项目状态',
  after_project_status int DEFAULT NULL COMMENT '执行后项目状态',
  before_manager_user_id bigint DEFAULT NULL COMMENT '执行前项目经理',
  after_manager_user_id bigint DEFAULT NULL COMMENT '执行后项目经理（回退时置空）',
  execute_time datetime DEFAULT NULL COMMENT '执行时间',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已提交 2审批中 3已执行 4已驳回 5已撤回',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_governance_action_no (action_no),
  KEY idx_pms_governance_action_project (project_id),
  KEY idx_pms_governance_action_type (action_type),
  KEY idx_pms_governance_action_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS 项目治理动作（回退/关闭）';

-- =============================================================================
-- 菜单：计划变更审批 + 项目治理（父菜单 18000）
-- ID 19151~19162
-- =============================================================================
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- ========== 计划变更审批菜单（sort 95）==========
(19151, '计划变更审批', 'pms:plan-change:query', 2, 95, 18000, 'plan-change', 'ep:edit-outline',
 'pms/project/plan-change/index', 'PmsPlanChange', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19152, '计划变更创建', 'pms:plan-change:create', 3, 1, 19151, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19153, '计划变更修改', 'pms:plan-change:update', 3, 2, 19151, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19154, '计划变更删除', 'pms:plan-change:delete', 3, 3, 19151, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19155, '计划变更提交', 'pms:plan-change:submit', 3, 4, 19151, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19156, '计划变更审批', 'pms:plan-change:audit', 3, 5, 19151, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 项目治理菜单（sort 96）==========
(19157, '项目治理', 'pms:project-governance:query', 2, 96, 18000, 'project-governance', 'ep:set-up',
 'pms/project/project-governance/index', 'PmsProjectGovernance', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19158, '治理动作创建', 'pms:project-governance:create', 3, 1, 19157, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19159, '治理动作修改', 'pms:project-governance:update', 3, 2, 19157, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19160, '治理动作删除', 'pms:project-governance:delete', 3, 3, 19157, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19161, '治理动作提交', 'pms:project-governance:submit', 3, 4, 19157, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19162, '治理动作审批执行', 'pms:project-governance:audit', 3, 5, 19157, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
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
  AND m.id BETWEEN 19151 AND 19162;
