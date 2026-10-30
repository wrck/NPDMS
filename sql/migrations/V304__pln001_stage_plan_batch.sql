-- P0-6 阶段施工计划（3.1 / PLN-01/04）批次与审批（V304，前向、幂等）
-- 背景：系统仅有单一工期基线（V90）与已冻结的工期倒排（V22，仅历史查询），
--       缺 8 行阶段×计划时间表、按建议工期占比自动推算、提交审批/生效基线/驳回重提
--       （核对记录 docs/reference/2026-09-19-项目交付全流程操作清单核对.md P0-6）。
-- 阶段事实经 ProjectStagePlanApi 只读；审批生效后经受控写入 applyPlanDates 回写阶段计划日期。

CREATE TABLE IF NOT EXISTS sol_stage_plan_batch (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '阶段施工计划批次ID',
  project_id bigint NOT NULL COMMENT 'PROJ项目ID',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1审批中 2已生效 3已驳回',
  duration_revision_id bigint DEFAULT NULL COMMENT '推算基准工期版本ID（空=按阶段建议时间）',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  bpm_process_key varchar(64) DEFAULT NULL COMMENT '审批流程定义Key',
  bpm_process_instance_id varchar(64) DEFAULT NULL COMMENT '审批流程实例ID',
  submitted_at datetime(3) DEFAULT NULL COMMENT '提交时间',
  submitted_by bigint DEFAULT NULL COMMENT '提交人',
  effective_at datetime(3) DEFAULT NULL COMMENT '审批生效时间',
  effective_by bigint DEFAULT NULL COMMENT '生效执行人（BPM回调）',
  reject_reason varchar(500) DEFAULT NULL COMMENT '驳回原因',
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_sol_stage_plan_batch_project (project_id),
  KEY idx_sol_stage_plan_batch_status (project_id, status),
  KEY idx_sol_stage_plan_batch_bpm (bpm_process_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阶段施工计划批次（PLN-01/04）';

CREATE TABLE IF NOT EXISTS sol_stage_plan_item (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '阶段计划明细ID',
  batch_id bigint NOT NULL COMMENT '批次ID',
  phase_id bigint NOT NULL COMMENT '项目阶段ID（pms_project_phase）',
  phase_code varchar(64) DEFAULT NULL COMMENT '阶段编码快照',
  phase_name varchar(128) NOT NULL COMMENT '阶段名称快照',
  sort int NOT NULL DEFAULT 0 COMMENT '阶段顺序',
  suggested_start date DEFAULT NULL COMMENT '建议开始时间快照',
  suggested_end date DEFAULT NULL COMMENT '建议结束时间快照',
  plan_start date NOT NULL COMMENT '计划开始时间',
  plan_end date NOT NULL COMMENT '计划结束时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_sol_stage_plan_item_batch (batch_id),
  KEY idx_sol_stage_plan_item_phase (phase_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阶段施工计划明细';

-- 菜单：页面 + 操作按钮（挂在项目交付目录 18000 下，紧邻施工计划相关入口）
INSERT IGNORE INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(993109180007, '阶段施工计划（3.1）', 'pms:imp-stage-plan:query', 2, 63, 18000, 'imp-stage-plan', 'ep:calendar',
 'pms/engineering/stage-plan/index', 'PmsImpStagePlan', 0, b'1', b'1', b'1',
 'p006_v304', NOW(), 'p006_v304', NOW(), b'0'),
(993109180008, '阶段计划创建', 'pms:imp-stage-plan:create', 3, 1, 993109180007, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'p006_v304', NOW(), 'p006_v304', NOW(), b'0'),
(993109180009, '阶段计划调整', 'pms:imp-stage-plan:update', 3, 2, 993109180007, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'p006_v304', NOW(), 'p006_v304', NOW(), b'0'),
(993109180010, '阶段计划提交审批', 'pms:imp-stage-plan:submit', 3, 3, 993109180007, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'p006_v304', NOW(), 'p006_v304', NOW(), b'0');

-- 已可见交付件归集菜单（19018）的角色同步可见阶段施工计划页与按钮，幂等
INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT source.role_id, m.menu_id, 'p006_v304', NOW(), 'p006_v304', NOW(), b'0', source.tenant_id
FROM `system_role_menu` source
JOIN (SELECT 993109180007 AS menu_id
      UNION SELECT 993109180008 UNION SELECT 993109180009
      UNION SELECT 993109180010) m
WHERE source.`menu_id` = 19018 AND source.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.`tenant_id` = source.`tenant_id`
                    AND existing.`role_id` = source.`role_id`
                    AND existing.`menu_id` = m.menu_id AND existing.`deleted` = b'0');
