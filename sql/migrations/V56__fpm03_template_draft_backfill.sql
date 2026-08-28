-- =============================================================================
-- V56: F-PM03 已发布模板草稿回填（修复"草稿界面未回填最新版本数据"）
--
-- 背景：真实业务流中发布时草稿行保留发布内容（publishProjectTemplate 草稿→
-- 发布复制、草稿保留），ACTIVE 模板草稿不可能为空。V55 仅回填了 910001 的
-- 草稿（911001 = 911003 全链副本），910002~910005 的草稿行仍为空，导致界面
-- 打开这些 ACTIVE 模板"草稿内容"页签时无数据可编辑。
--
-- 修复：将 910002~910005 各自最新 PUBLISHED 版本（v1）的六类定义行复制到
-- 对应 DRAFT 草稿行，恢复"基于最新发布版编辑下一版"的业务形态：
--   911004 (910002 草稿) ← 911005 (910002 v1)
--   911006 (910003 草稿) ← 911007 (910003 v1)
--   911008 (910004 草稿) ← 911009 (910004 v1)
--   911010 (910005 草稿) ← 911011 (910005 v1)
-- 不处理：911001（V55 已回填）、911012（910006 RETIRED 停用冻结演示）、
--   911014（910007 空草稿，发布校验拦截演示）。
-- 草稿行四维条件与流程引用 V54 已与 PUBLISHED 版本对齐，无需更新版本行。
--
-- 幂等：对目标草稿行的 seed 子行先物理删除再复制（Delete+Insert 收敛，可
-- 重复执行）；若业务用户已编辑过草稿（非 seed 行），uk 冲突使迁移失败，
-- 属预期防护，避免静默覆盖业务数据。
-- =============================================================================

-- 0. 清理目标草稿行的 seed 子行（物理删除，仅 seed 演示数据）
DELETE FROM `proj_project_template_gate_reference`         WHERE `template_revision_id` IN (911004, 911006, 911008, 911010) AND `creator` = 'seed';
DELETE FROM `proj_project_template_gate_definition`        WHERE `template_revision_id` IN (911004, 911006, 911008, 911010) AND `creator` = 'seed';
DELETE FROM `proj_project_template_deliverable_definition` WHERE `template_revision_id` IN (911004, 911006, 911008, 911010) AND `creator` = 'seed';
DELETE FROM `proj_project_template_milestone_definition`   WHERE `template_revision_id` IN (911004, 911006, 911008, 911010) AND `creator` = 'seed';
DELETE FROM `proj_project_template_task_definition`        WHERE `template_revision_id` IN (911004, 911006, 911008, 911010) AND `creator` = 'seed';
DELETE FROM `proj_project_template_stage_definition`       WHERE `template_revision_id` IN (911004, 911006, 911008, 911010) AND `creator` = 'seed';

-- 1. 阶段定义（草稿 ← 最新发布版）
INSERT INTO `proj_project_template_stage_definition`
(`template_revision_id`, `stage_code`, `name`, `sort_order`, `entry_criteria`, `exit_criteria`, `creator`, `updater`, `tenant_id`)
SELECT m.draft_id, s.`stage_code`, s.`name`, s.`sort_order`, s.`entry_criteria`, s.`exit_criteria`, 'seed', 'seed', s.`tenant_id`
FROM (SELECT 911004 AS draft_id, 911005 AS src_id
      UNION ALL SELECT 911006, 911007
      UNION ALL SELECT 911008, 911009
      UNION ALL SELECT 911010, 911011) m
JOIN `proj_project_template_stage_definition` s
  ON s.`template_revision_id` = m.src_id AND s.`deleted` = b'0';

-- 2. 任务定义
INSERT INTO `proj_project_template_task_definition`
(`template_revision_id`, `task_code`, `name`, `parent_task_code`, `stage_code`, `priority`, `sort_order`, `estimated_hours`, `satisfaction_timing`, `description`, `creator`, `updater`, `tenant_id`)
SELECT m.draft_id, s.`task_code`, s.`name`, s.`parent_task_code`, s.`stage_code`, s.`priority`, s.`sort_order`, s.`estimated_hours`, s.`satisfaction_timing`, s.`description`, 'seed', 'seed', s.`tenant_id`
FROM (SELECT 911004 AS draft_id, 911005 AS src_id
      UNION ALL SELECT 911006, 911007
      UNION ALL SELECT 911008, 911009
      UNION ALL SELECT 911010, 911011) m
JOIN `proj_project_template_task_definition` s
  ON s.`template_revision_id` = m.src_id AND s.`deleted` = b'0';

-- 3. 里程碑定义
INSERT INTO `proj_project_template_milestone_definition`
(`template_revision_id`, `milestone_code`, `name`, `stage_code`, `timing`, `criteria`, `creator`, `updater`, `tenant_id`)
SELECT m.draft_id, s.`milestone_code`, s.`name`, s.`stage_code`, s.`timing`, s.`criteria`, 'seed', 'seed', s.`tenant_id`
FROM (SELECT 911004 AS draft_id, 911005 AS src_id
      UNION ALL SELECT 911006, 911007
      UNION ALL SELECT 911008, 911009
      UNION ALL SELECT 911010, 911011) m
JOIN `proj_project_template_milestone_definition` s
  ON s.`template_revision_id` = m.src_id AND s.`deleted` = b'0';

-- 4. 交付件定义
INSERT INTO `proj_project_template_deliverable_definition`
(`template_revision_id`, `deliverable_code`, `name`, `stage_code`, `task_code`, `required`, `creator`, `updater`, `tenant_id`)
SELECT m.draft_id, s.`deliverable_code`, s.`name`, s.`stage_code`, s.`task_code`, s.`required`, 'seed', 'seed', s.`tenant_id`
FROM (SELECT 911004 AS draft_id, 911005 AS src_id
      UNION ALL SELECT 911006, 911007
      UNION ALL SELECT 911008, 911009
      UNION ALL SELECT 911010, 911011) m
JOIN `proj_project_template_deliverable_definition` s
  ON s.`template_revision_id` = m.src_id AND s.`deleted` = b'0';

-- 5. 门禁定义
INSERT INTO `proj_project_template_gate_definition`
(`template_revision_id`, `gate_code`, `name`, `gate_type`, `stage_code`, `description`, `creator`, `updater`, `tenant_id`)
SELECT m.draft_id, s.`gate_code`, s.`name`, s.`gate_type`, s.`stage_code`, s.`description`, 'seed', 'seed', s.`tenant_id`
FROM (SELECT 911004 AS draft_id, 911005 AS src_id
      UNION ALL SELECT 911006, 911007
      UNION ALL SELECT 911008, 911009
      UNION ALL SELECT 911010, 911011) m
JOIN `proj_project_template_gate_definition` s
  ON s.`template_revision_id` = m.src_id AND s.`deleted` = b'0';

-- 6. 门禁引用行
INSERT INTO `proj_project_template_gate_reference`
(`template_revision_id`, `gate_code`, `ref_type`, `ref_code`, `ref_version`, `creator`, `updater`, `tenant_id`)
SELECT m.draft_id, s.`gate_code`, s.`ref_type`, s.`ref_code`, s.`ref_version`, 'seed', 'seed', s.`tenant_id`
FROM (SELECT 911004 AS draft_id, 911005 AS src_id
      UNION ALL SELECT 911006, 911007
      UNION ALL SELECT 911008, 911009
      UNION ALL SELECT 911010, 911011) m
JOIN `proj_project_template_gate_reference` s
  ON s.`template_revision_id` = m.src_id AND s.`deleted` = b'0';
