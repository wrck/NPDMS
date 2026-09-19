-- V254: 无承接旧链原地对齐领域模型（冻结方案修订：仅保留有明确承接新实现的旧链）
-- 承接判定依据 08a 分册 + 新实现存在性核验；表名 RENAME 不改变字段与业务语义。
-- 有承接保持冻结的旧表（本脚本不动）：pms_acc_acceptance、pms_acc_deliverable_checklist、
--   pms_acc_project_closure、pms_customer、pms_customer_contact、pms_project、pms_project_task、
--   pms_project_team_member、pms_project_phase、pms_project_risk、pms_cut_*、pms_plan_change_* 等。

RENAME TABLE
  -- ACC 域（归档文档/完工证明无新模型承接，09目标表名未占用，原地改 acc_ 前缀）
  `pms_acc_archive_document` TO `acc_archive_document`,
  `pms_acc_completion_certificate` TO `acc_completion_certificate`,
  -- PROJ 域（组合/治理/批量变更为 CURRENT_FORWARD 当前承载，无新承接，原地改 proj_ 前缀）
  `pms_project_portfolio` TO `proj_project_portfolio`,
  `pms_project_portfolio_rule` TO `proj_project_portfolio_rule`,
  `pms_project_portfolio_member` TO `proj_project_portfolio_member`,
  `pms_project_governance_action` TO `proj_project_governance_action`,
  `pms_team_batch_change` TO `proj_team_batch_change`,
  `pms_team_batch_change_item` TO `proj_team_batch_change_item`,
  -- CUS 域（服务等级 Owner 为 CUS，CUS-02 NOT_STARTED 无承接；代码已迁 pms-module-customer）
  `pms_customer_service_level` TO `cus_customer_service_level`;

-- 服务等级菜单 component 随前端目录归位（URL/权限不变）
UPDATE `system_menu`
SET `component` = 'pms/customer/service-level/index', `update_time` = NOW()
WHERE `component` = 'pms/project/service-level/index' AND `deleted` = b'0';
