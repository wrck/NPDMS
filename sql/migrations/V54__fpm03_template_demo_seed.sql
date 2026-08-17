-- =============================================================================
-- V54: F-PM03 项目模板基座示例数据（初始化数据补充 / 组合覆盖）
--
-- 依据：
--   * 工程规则：功能模块完成后须补充初始化数据；无明确定义内容的以示例数据
--     落地，且必须涵盖组合情况。
--   * V52 约束：模板内容不做"官方代码种子"（Feature Spec Out of Scope），
--     本迁移提供的是"示例数据"（creator='seed'、编码 TPL-* 前缀、高段 ID
--     910001+），用于演示/验收匹配组合，不占用系统保留编码语义。
--   * V52:189 重大项目级别为 CRM 权威来源属性映射，不预置字典取值；
--     因此示例模板该维一律 NULL（=不限），组合覆盖在已定义值域内完成。
--
-- 组合覆盖矩阵（签约2 x 类别2 x 实施3 = 12 组合，按优先级+兜底语义收敛为 5 个
-- ACTIVE 候选 + 1 个 RETIRED（演示停用不参与匹配）+ 1 个 DRAFT（演示空内容
-- 发布拦截））：
--   910001 TPL-DIRECT-ENG-STD    ACTIVE p10  DIRECT_SIGN+ENGINEERING+DIRECT_SERVICE  双版本(v1/v2 演示演进)
--   910002 TPL-DIRECT-ENG-SUP    ACTIVE p10  DIRECT_SIGN+ENGINEERING+SUPERVISION
--   910003 TPL-DIRECT-ENG-AGENT  ACTIVE p10  DIRECT_SIGN+ENGINEERING+AGENT_SELF_SERVICE
--   910004 TPL-CHANNEL-GEN-STD   ACTIVE p20  CHANNEL_SIGN+GENERAL+NULL(实施不限)
--   910005 TPL-GEN-FALLBACK      ACTIVE p90  NULL+GENERAL+NULL(兜底，演示优先级让位)
--   910006 TPL-DIRECT-GEN-LEGACY RETIRED p50 DIRECT_SIGN+GENERAL+NULL(有PUBLISHED但停用)
--   910007 TPL-NEW-DRAFT         DRAFT  p100 全NULL(空草稿，发布应被校验拦截)
--
-- 幂等与留痕（SDS 18-deployment §种子要求）：
--   * ON DUPLICATE KEY UPDATE no-op：重复执行不产生重复有效事实，
--     且不覆盖已发布业务版本。
--   * 带 PUBLISHED 版本的示例不可经界面删除（BR-8 留痕）；演示清理用
--     "停用"（RETIRED 只阻新匹配），物理清理需 DBA 前向脚本。
-- =============================================================================

-- 1. 模板身份（7 行）
INSERT INTO `proj_project_template`
(`id`, `code`, `name`, `status`, `match_priority`, `description`, `system_reserved`, `creator`, `updater`, `tenant_id`)
VALUES
(910001, 'TPL-DIRECT-ENG-STD',   '直签工程类标准交付（示例）',   'ACTIVE',  10,  '示例：直签+工程类+原厂直服，双版本演示模板演进', b'0', 'seed', 'seed', 0),
(910002, 'TPL-DIRECT-ENG-SUP',   '直签工程类原厂督导（示例）',   'ACTIVE',  10,  '示例：直签+工程类+原厂督导', b'0', 'seed', 'seed', 0),
(910003, 'TPL-DIRECT-ENG-AGENT', '直签工程类代理商自服（示例）', 'ACTIVE',  10,  '示例：直签+工程类+代理商自服', b'0', 'seed', 'seed', 0),
(910004, 'TPL-CHANNEL-GEN-STD',  '非直签普通类标准交付（示例）', 'ACTIVE',  20,  '示例：非直签+普通类，实施方式不限', b'0', 'seed', 'seed', 0),
(910005, 'TPL-GEN-FALLBACK',     '普通类通用兜底（示例）',       'ACTIVE',  90,  '示例：签约/实施不限，普通类兜底（优先级让位演示）', b'0', 'seed', 'seed', 0),
(910006, 'TPL-DIRECT-GEN-LEGACY','直签普通类旧版（停用示例）',   'RETIRED', 50,  '示例：已停用，不参与新项目匹配', b'0', 'seed', 'seed', 0),
(910007, 'TPL-NEW-DRAFT',        '新建草稿（示例）',             'DRAFT',   100, '示例：空草稿，发布校验应拦截', b'0', 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `id` = `id`;

-- 2. 模板版本（草稿0 + 已发布；910001 双发布 v1/v2）
INSERT INTO `proj_project_template_revision`
(`id`, `template_id`, `revision_no`, `status`,
 `signing_method`, `project_category`, `implementation_method`, `major_project_level`,
 `process_definition_key`, `process_definition_version`, `validation_summary`,
 `published_by`, `published_time`, `creator`, `updater`, `tenant_id`)
VALUES
-- 910001 直签工程类标准：草稿 + v1 + v2（最新生效）
(911001, 910001, 0, 'DRAFT',    'DIRECT_SIGN', 'ENGINEERING', 'DIRECT_SERVICE', NULL, 'PROC-PMS-DELIVERY-STD', 'v2', NULL, NULL, NULL, 'seed', 'seed', 0),
(911002, 910001, 1, 'PUBLISHED','DIRECT_SIGN', 'ENGINEERING', 'DIRECT_SERVICE', NULL, 'PROC-PMS-DELIVERY-STD', 'v1', '示例种子：发布校验口径合规', '1', NOW(), 'seed', 'seed', 0),
(911003, 910001, 2, 'PUBLISHED','DIRECT_SIGN', 'ENGINEERING', 'DIRECT_SERVICE', NULL, 'PROC-PMS-DELIVERY-STD', 'v2', '示例种子：发布校验口径合规', '1', NOW(), 'seed', 'seed', 0),
-- 910002/910003/910004/910005/910006：草稿 + v1
(911004, 910002, 0, 'DRAFT',    'DIRECT_SIGN', 'ENGINEERING', 'SUPERVISION', NULL, 'PROC-PMS-DELIVERY-STD', 'v1', NULL, NULL, NULL, 'seed', 'seed', 0),
(911005, 910002, 1, 'PUBLISHED','DIRECT_SIGN', 'ENGINEERING', 'SUPERVISION', NULL, 'PROC-PMS-DELIVERY-STD', 'v1', '示例种子：发布校验口径合规', '1', NOW(), 'seed', 'seed', 0),
(911006, 910003, 0, 'DRAFT',    'DIRECT_SIGN', 'ENGINEERING', 'AGENT_SELF_SERVICE', NULL, 'PROC-PMS-DELIVERY-STD', 'v1', NULL, NULL, NULL, 'seed', 'seed', 0),
(911007, 910003, 1, 'PUBLISHED','DIRECT_SIGN', 'ENGINEERING', 'AGENT_SELF_SERVICE', NULL, 'PROC-PMS-DELIVERY-STD', 'v1', '示例种子：发布校验口径合规', '1', NOW(), 'seed', 'seed', 0),
(911008, 910004, 0, 'DRAFT',    'CHANNEL_SIGN', 'GENERAL', NULL, NULL, 'PROC-PMS-DELIVERY-STD', 'v1', NULL, NULL, NULL, 'seed', 'seed', 0),
(911009, 910004, 1, 'PUBLISHED','CHANNEL_SIGN', 'GENERAL', NULL, NULL, 'PROC-PMS-DELIVERY-STD', 'v1', '示例种子：发布校验口径合规', '1', NOW(), 'seed', 'seed', 0),
(911010, 910005, 0, 'DRAFT',    NULL, 'GENERAL', NULL, NULL, 'PROC-PMS-DELIVERY-STD', 'v1', NULL, NULL, NULL, 'seed', 'seed', 0),
(911011, 910005, 1, 'PUBLISHED',NULL, 'GENERAL', NULL, NULL, 'PROC-PMS-DELIVERY-STD', 'v1', '示例种子：发布校验口径合规', '1', NOW(), 'seed', 'seed', 0),
(911012, 910006, 0, 'DRAFT',    'DIRECT_SIGN', 'GENERAL', NULL, NULL, 'PROC-PMS-DELIVERY-STD', 'v1', NULL, NULL, NULL, 'seed', 'seed', 0),
(911013, 910006, 1, 'PUBLISHED','DIRECT_SIGN', 'GENERAL', NULL, NULL, 'PROC-PMS-DELIVERY-STD', 'v1', '示例种子：发布校验口径合规', '1', NOW(), 'seed', 'seed', 0),
-- 910007 空草稿：无流程引用、无内容（发布校验应拦截）
(911014, 910007, 0, 'DRAFT',    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `id` = `id`;

-- 3. 阶段定义（每版本 2～3 阶段；911003 为完整链）
INSERT INTO `proj_project_template_stage_definition`
(`template_revision_id`, `stage_code`, `name`, `sort_order`, `entry_criteria`, `exit_criteria`, `creator`, `updater`, `tenant_id`)
VALUES
-- 911002 (910001 v1 精简)
(911002, 'S0', '项目启动', 0, '合同生效，输入齐备', '启动会纪要签认', 'seed', 'seed', 0),
(911002, 'S1', '实施交付', 1, '启动会完成', '交付件提交并确认', 'seed', 'seed', 0),
-- 911003 (910001 v2 完整链：三阶段)
(911003, 'S0', '项目启动', 0, '合同生效，输入齐备', '启动会纪要签认', 'seed', 'seed', 0),
(911003, 'S1', '实施交付', 1, '启动会完成', '实施方案与部署交付确认', 'seed', 'seed', 0),
(911003, 'S2', '验收收尾', 2, '实施交付确认', '验收清单核验通过', 'seed', 'seed', 0),
-- 911005/911007/911009/911011/911013（最小合规两阶段）
(911005, 'S0', '项目启动', 0, '输入齐备', '开工确认', 'seed', 'seed', 0),
(911005, 'S1', '实施交付', 1, '开工确认', '交付执行报告确认', 'seed', 'seed', 0),
(911007, 'S0', '项目启动', 0, '输入齐备', '开工确认', 'seed', 'seed', 0),
(911007, 'S1', '实施交付', 1, '开工确认', '交付执行报告确认', 'seed', 'seed', 0),
(911009, 'S0', '项目启动', 0, '输入齐备', '开工确认', 'seed', 'seed', 0),
(911009, 'S1', '实施交付', 1, '开工确认', '交付执行报告确认', 'seed', 'seed', 0),
(911011, 'S0', '项目启动', 0, '输入齐备', '开工确认', 'seed', 'seed', 0),
(911011, 'S1', '实施交付', 1, '开工确认', '交付执行报告确认', 'seed', 'seed', 0),
(911013, 'S0', '项目启动', 0, '输入齐备', '开工确认', 'seed', 'seed', 0),
(911013, 'S1', '实施交付', 1, '开工确认', '交付执行报告确认', 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

-- 4. 任务定义（911003 含父子任务演示）
INSERT INTO `proj_project_template_task_definition`
(`template_revision_id`, `task_code`, `name`, `parent_task_code`, `stage_code`, `priority`, `sort_order`, `estimated_hours`, `description`, `creator`, `updater`, `tenant_id`)
VALUES
-- 911002 (v1)
(911002, 'T-KICKOFF-PLAN', '启动会准备', NULL, 'S0', 2, 0, 8.0,  '编制启动会材料与议程', 'seed', 'seed', 0),
(911002, 'T-SURVEY-SCOPE', '现场勘查与范围确认', NULL, 'S1', 2, 0, 16.0, '确认实施范围与边界', 'seed', 'seed', 0),
-- 911003 (v2 完整链：含父子)
(911003, 'T-KICKOFF-PLAN',  '启动会准备',   NULL,             'S0', 2, 0, 8.0,  '编制启动会材料与议程', 'seed', 'seed', 0),
(911003, 'T-KICKOFF-HOLD',  '启动会召开',   'T-KICKOFF-PLAN', 'S0', 2, 1, 2.0,  '组织召开启动会并取签认', 'seed', 'seed', 0),
(911003, 'T-SURVEY-SCOPE',  '现场勘查与范围确认', NULL,        'S1', 2, 0, 16.0, '确认实施范围与边界', 'seed', 'seed', 0),
(911003, 'T-DEPLOY-EXEC',   '部署实施执行', 'T-SURVEY-SCOPE', 'S1', 1, 1, 40.0, '按实施方案执行部署', 'seed', 'seed', 0),
(911003, 'T-ACCEPT-SUPPORT','验收支持与移交', NULL,            'S2', 2, 0, 8.0,  '支持客户验收并移交', 'seed', 'seed', 0),
-- 最小合规集（每版本 2 任务）
(911005, 'T-INIT-CHK',      '环境与输入核对', NULL, 'S0', 2, 0, 4.0,  '核对交付输入与环境', 'seed', 'seed', 0),
(911005, 'T-DELIVER-EXEC',  '交付执行',       NULL, 'S1', 2, 0, 24.0, '执行标准交付动作', 'seed', 'seed', 0),
(911007, 'T-INIT-CHK',      '环境与输入核对', NULL, 'S0', 2, 0, 4.0,  '核对交付输入与环境', 'seed', 'seed', 0),
(911007, 'T-DELIVER-EXEC',  '交付执行',       NULL, 'S1', 2, 0, 24.0, '执行标准交付动作', 'seed', 'seed', 0),
(911009, 'T-INIT-CHK',      '环境与输入核对', NULL, 'S0', 2, 0, 4.0,  '核对交付输入与环境', 'seed', 'seed', 0),
(911009, 'T-DELIVER-EXEC',  '交付执行',       NULL, 'S1', 2, 0, 24.0, '执行标准交付动作', 'seed', 'seed', 0),
(911011, 'T-INIT-CHK',      '环境与输入核对', NULL, 'S0', 2, 0, 4.0,  '核对交付输入与环境', 'seed', 'seed', 0),
(911011, 'T-DELIVER-EXEC',  '交付执行',       NULL, 'S1', 2, 0, 24.0, '执行标准交付动作', 'seed', 'seed', 0),
(911013, 'T-INIT-CHK',      '环境与输入核对', NULL, 'S0', 2, 0, 4.0,  '核对交付输入与环境', 'seed', 'seed', 0),
(911013, 'T-DELIVER-EXEC',  '交付执行',       NULL, 'S1', 2, 0, 24.0, '执行标准交付动作', 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

-- 5. 里程碑定义（工程类=启动会；普通类=开工确认）
INSERT INTO `proj_project_template_milestone_definition`
(`template_revision_id`, `milestone_code`, `name`, `stage_code`, `timing`, `criteria`, `creator`, `updater`, `tenant_id`)
VALUES
(911002, 'M-KICKOFF', '启动会完成', 'S0', '启动会召开后', '客户签认启动会纪要', 'seed', 'seed', 0),
(911003, 'M-KICKOFF', '启动会完成', 'S0', '启动会召开后', '客户签认启动会纪要', 'seed', 'seed', 0),
(911005, 'M-KICKOFF', '启动会完成', 'S0', '启动会召开后', '客户签认启动会纪要', 'seed', 'seed', 0),
(911007, 'M-KICKOFF', '启动会完成', 'S0', '启动会召开后', '客户签认启动会纪要', 'seed', 'seed', 0),
(911009, 'M-START',   '开工确认',   'S0', '环境核对完成后', '开工条件核对通过', 'seed', 'seed', 0),
(911011, 'M-START',   '开工确认',   'S0', '环境核对完成后', '开工条件核对通过', 'seed', 'seed', 0),
(911013, 'M-START',   '开工确认',   'S0', '环境核对完成后', '开工条件核对通过', 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

-- 6. 交付件定义（911003 含"阶段级非必需"演示）
INSERT INTO `proj_project_template_deliverable_definition`
(`template_revision_id`, `deliverable_code`, `name`, `stage_code`, `task_code`, `required`, `creator`, `updater`, `tenant_id`)
VALUES
(911002, 'D-KICKOFF-MINUTES', '启动会纪要',     'S0', 'T-KICKOFF-PLAN', b'1', 'seed', 'seed', 0),
(911003, 'D-KICKOFF-MINUTES', '启动会纪要',     'S0', 'T-KICKOFF-HOLD', b'1', 'seed', 'seed', 0),
(911003, 'D-IMPL-PLAN',       '实施方案',       'S1', 'T-SURVEY-SCOPE', b'1', 'seed', 'seed', 0),
(911003, 'D-CHECKLIST-REPORT','验收检查清单报告','S2', NULL,             b'0', 'seed', 'seed', 0),
(911005, 'D-EXEC-REPORT',     '交付执行报告',   'S1', 'T-DELIVER-EXEC', b'1', 'seed', 'seed', 0),
(911007, 'D-EXEC-REPORT',     '交付执行报告',   'S1', 'T-DELIVER-EXEC', b'1', 'seed', 'seed', 0),
(911009, 'D-EXEC-REPORT',     '交付执行报告',   'S1', 'T-DELIVER-EXEC', b'1', 'seed', 'seed', 0),
(911011, 'D-EXEC-REPORT',     '交付执行报告',   'S1', 'T-DELIVER-EXEC', b'1', 'seed', 'seed', 0),
(911013, 'D-EXEC-REPORT',     '交付执行报告',   'S1', 'T-DELIVER-EXEC', b'1', 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

-- 7. 门禁定义（每版本 ENTRY+EXIT 各一，引用行见下）
INSERT INTO `proj_project_template_gate_definition`
(`template_revision_id`, `gate_code`, `name`, `gate_type`, `stage_code`, `description`, `creator`, `updater`, `tenant_id`)
VALUES
(911002, 'G-S1-ENTRY', '实施交付准入', 'ENTRY', 'S1', '启动会完成后方可进入实施', 'seed', 'seed', 0),
(911002, 'G-S1-EXIT',  '实施交付准出', 'EXIT',  'S1', '启动会纪要提交并确认', 'seed', 'seed', 0),
(911003, 'G-S1-ENTRY', '实施交付准入', 'ENTRY', 'S1', '启动会完成且勘查就绪', 'seed', 'seed', 0),
(911003, 'G-S1-EXIT',  '实施交付准出', 'EXIT',  'S1', '实施方案确认且部署完成', 'seed', 'seed', 0),
(911003, 'G-S2-ENTRY', '验收收尾准入', 'ENTRY', 'S2', '实施交付准出后进入验收', 'seed', 'seed', 0),
(911005, 'G-S1-ENTRY', '实施交付准入', 'ENTRY', 'S1', '开工确认后进入交付', 'seed', 'seed', 0),
(911005, 'G-S1-EXIT',  '实施交付准出', 'EXIT',  'S1', '交付执行报告确认', 'seed', 'seed', 0),
(911007, 'G-S1-ENTRY', '实施交付准入', 'ENTRY', 'S1', '开工确认后进入交付', 'seed', 'seed', 0),
(911007, 'G-S1-EXIT',  '实施交付准出', 'EXIT',  'S1', '交付执行报告确认', 'seed', 'seed', 0),
(911009, 'G-S1-ENTRY', '实施交付准入', 'ENTRY', 'S1', '开工确认后进入交付', 'seed', 'seed', 0),
(911009, 'G-S1-EXIT',  '实施交付准出', 'EXIT',  'S1', '交付执行报告确认', 'seed', 'seed', 0),
(911011, 'G-S1-ENTRY', '实施交付准入', 'ENTRY', 'S1', '开工确认后进入交付', 'seed', 'seed', 0),
(911011, 'G-S1-EXIT',  '实施交付准出', 'EXIT',  'S1', '交付执行报告确认', 'seed', 'seed', 0),
(911013, 'G-S1-ENTRY', '实施交付准入', 'ENTRY', 'S1', '开工确认后进入交付', 'seed', 'seed', 0),
(911013, 'G-S1-EXIT',  '实施交付准出', 'EXIT',  'S1', '交付执行报告确认', 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

-- 8. 门禁引用行（TASK/DELIVERABLE/STATE/PROCESS 四类全覆盖演示）
INSERT INTO `proj_project_template_gate_reference`
(`template_revision_id`, `gate_code`, `ref_type`, `ref_code`, `ref_version`, `creator`, `updater`, `tenant_id`)
VALUES
(911002, 'G-S1-ENTRY', 'STATE',      'S0_COMPLETED', NULL, 'seed', 'seed', 0),
(911002, 'G-S1-EXIT',  'DELIVERABLE','D-KICKOFF-MINUTES', NULL, 'seed', 'seed', 0),
(911002, 'G-S1-EXIT',  'PROCESS',    'PROC-PMS-DELIVERY-STD', 'v1', 'seed', 'seed', 0),
(911003, 'G-S1-ENTRY', 'STATE',      'S0_COMPLETED', NULL, 'seed', 'seed', 0),
(911003, 'G-S1-ENTRY', 'TASK',       'T-SURVEY-SCOPE', NULL, 'seed', 'seed', 0),
(911003, 'G-S1-EXIT',  'DELIVERABLE','D-IMPL-PLAN', NULL, 'seed', 'seed', 0),
(911003, 'G-S1-EXIT',  'PROCESS',    'PROC-PMS-DELIVERY-STD', 'v2', 'seed', 'seed', 0),
(911003, 'G-S2-ENTRY', 'STATE',      'S1_COMPLETED', NULL, 'seed', 'seed', 0),
(911005, 'G-S1-ENTRY', 'STATE',      'S0_COMPLETED', NULL, 'seed', 'seed', 0),
(911005, 'G-S1-EXIT',  'DELIVERABLE','D-EXEC-REPORT', NULL, 'seed', 'seed', 0),
(911007, 'G-S1-ENTRY', 'STATE',      'S0_COMPLETED', NULL, 'seed', 'seed', 0),
(911007, 'G-S1-EXIT',  'DELIVERABLE','D-EXEC-REPORT', NULL, 'seed', 'seed', 0),
(911009, 'G-S1-ENTRY', 'STATE',      'S0_COMPLETED', NULL, 'seed', 'seed', 0),
(911009, 'G-S1-EXIT',  'DELIVERABLE','D-EXEC-REPORT', NULL, 'seed', 'seed', 0),
(911011, 'G-S1-ENTRY', 'STATE',      'S0_COMPLETED', NULL, 'seed', 'seed', 0),
(911011, 'G-S1-EXIT',  'DELIVERABLE','D-EXEC-REPORT', NULL, 'seed', 'seed', 0),
(911013, 'G-S1-ENTRY', 'STATE',      'S0_COMPLETED', NULL, 'seed', 'seed', 0),
(911013, 'G-S1-EXIT',  'DELIVERABLE','D-EXEC-REPORT', NULL, 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;
