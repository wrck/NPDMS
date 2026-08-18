-- =============================================================================
-- V59: F-PM01 手工创建匹配示例数据补充（同优先级多匹配 / 人工选择路径覆盖）
--
-- 依据：
--   * 工程规则：初始化示例数据必须覆盖关键维度组合（含精确命中、部分限定、
--     优先级让位、无匹配与停用不参与等场景）。
--   * V54 已覆盖：唯一命中、优先级让位、无匹配、停用不参与；但手工创建
--     BR-2 三维均为必填，全三维组合下无"同优先级多匹配"种子，导致
--     MANUAL_SELECTED（人工选模板）路径 UI 不可达。
--   * 本迁移补充 1 个与 910004 同优先级(p20)且实施方式精确限定的 ACTIVE
--     模板，使 CHANNEL_SIGN+GENERAL+DIRECT_SERVICE 组合命中两个 p20 候选：
--     910004（实施不限，部分限定）与 910008（实施=原厂直服，精确命中）
--     → MULTI_MATCH 阻断自动选模，强制人工选择（PM-03 规则4 / BR-4）。
--
-- 组合效果（V54 + V59）：
--   CHANNEL_SIGN+GENERAL+DIRECT_SERVICE → 910004(p20)+910008(p20)+910005(p90)
--                                       → 同优先级多匹配（人工选择演示）
--   CHANNEL_SIGN+GENERAL+SUPERVISION    → 910004(p20)+910005(p90) → 唯一命中（让位）
--   DIRECT_SIGN+ENGINEERING+*           → 910001/910002/910003 精确命中
--   CHANNEL_SIGN+ENGINEERING+*          → 无匹配阻断
--
-- 幂等与留痕：沿 V54 约定（creator='seed'、高段 ID 910008+/911015+、
--   ON DUPLICATE KEY UPDATE no-op，不覆盖业务事实）。
-- =============================================================================

-- 1. 模板身份（1 行）
INSERT INTO `proj_project_template`
(`id`, `code`, `name`, `status`, `match_priority`, `description`, `system_reserved`, `creator`, `updater`, `tenant_id`)
VALUES
(910008, 'TPL-CHANNEL-GEN-DIRECT', '非直签普通类原厂直服（示例）', 'ACTIVE', 20,
 '示例：非直签+普通类+原厂直服，与 TPL-CHANNEL-GEN-STD 同优先级演示多匹配人工选择', b'0', 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `id` = `id`;

-- 2. 模板版本（草稿0 + 已发布 v1）
INSERT INTO `proj_project_template_revision`
(`id`, `template_id`, `revision_no`, `status`,
 `signing_method`, `project_category`, `implementation_method`, `major_project_level`,
 `process_definition_key`, `process_definition_version`, `validation_summary`,
 `published_by`, `published_time`, `creator`, `updater`, `tenant_id`)
VALUES
(911015, 910008, 0, 'DRAFT',     'CHANNEL_SIGN', 'GENERAL', 'DIRECT_SERVICE', NULL, 'PROC-PMS-DELIVERY-STD', 'v1', NULL, NULL, NULL, 'seed', 'seed', 0),
(911016, 910008, 1, 'PUBLISHED', 'CHANNEL_SIGN', 'GENERAL', 'DIRECT_SERVICE', NULL, 'PROC-PMS-DELIVERY-STD', 'v1', '示例种子：发布校验口径合规', '1', NOW(), 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `id` = `id`;

-- 3. 阶段定义（最小合规两阶段，沿 911009 口径）
INSERT INTO `proj_project_template_stage_definition`
(`template_revision_id`, `stage_code`, `name`, `sort_order`, `entry_criteria`, `exit_criteria`, `creator`, `updater`, `tenant_id`)
VALUES
(911016, 'S0', '项目启动', 0, '输入齐备', '开工确认', 'seed', 'seed', 0),
(911016, 'S1', '实施交付', 1, '开工确认', '交付执行报告确认', 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

-- 4. 任务定义（2 行）
INSERT INTO `proj_project_template_task_definition`
(`template_revision_id`, `task_code`, `name`, `parent_task_code`, `stage_code`, `priority`, `sort_order`, `estimated_hours`, `description`, `creator`, `updater`, `tenant_id`)
VALUES
(911016, 'T-INIT-CHK',     '环境与输入核对', NULL, 'S0', 2, 0, 4.0,  '核对交付输入与环境', 'seed', 'seed', 0),
(911016, 'T-DELIVER-EXEC', '交付执行',       NULL, 'S1', 2, 0, 24.0, '执行标准交付动作',   'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

-- 5. 里程碑定义（1 行，普通类=开工确认）
INSERT INTO `proj_project_template_milestone_definition`
(`template_revision_id`, `milestone_code`, `name`, `stage_code`, `timing`, `criteria`, `creator`, `updater`, `tenant_id`)
VALUES
(911016, 'M-START', '开工确认', 'S0', '环境核对完成后', '开工条件核对通过', 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

-- 6. 交付件定义（1 行）
INSERT INTO `proj_project_template_deliverable_definition`
(`template_revision_id`, `deliverable_code`, `name`, `stage_code`, `task_code`, `required`, `creator`, `updater`, `tenant_id`)
VALUES
(911016, 'D-EXEC-REPORT', '交付执行报告', 'S1', 'T-DELIVER-EXEC', b'1', 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

-- 7. 门禁定义（ENTRY+EXIT 各一）
INSERT INTO `proj_project_template_gate_definition`
(`template_revision_id`, `gate_code`, `name`, `gate_type`, `stage_code`, `description`, `creator`, `updater`, `tenant_id`)
VALUES
(911016, 'G-S1-ENTRY', '实施交付准入', 'ENTRY', 'S1', '开工确认后进入交付', 'seed', 'seed', 0),
(911016, 'G-S1-EXIT',  '实施交付准出', 'EXIT',  'S1', '交付执行报告确认',   'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

-- 8. 门禁引用行（STATE/DELIVERABLE 各一）
INSERT INTO `proj_project_template_gate_reference`
(`template_revision_id`, `gate_code`, `ref_type`, `ref_code`, `ref_version`, `creator`, `updater`, `tenant_id`)
VALUES
(911016, 'G-S1-ENTRY', 'STATE',       'S0_COMPLETED',  NULL, 'seed', 'seed', 0),
(911016, 'G-S1-EXIT',  'DELIVERABLE', 'D-EXEC-REPORT', NULL, 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;
