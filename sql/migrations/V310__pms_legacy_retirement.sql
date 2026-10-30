-- =============================================================================
-- V310: pms_* 旧域整体退役（专项授权）
-- 背景：proj_/cus_/ast_/cut_/srv_/sol_/acc_ 等新域模型已承接业务，pms_* 旧表
--       及其业务入口全部退役。
-- 方案：
--   1) 全部 pms_* 表原子重命名，统一追加 `_retired` 后缀；
--   2) 为每张退役表创建 BEFORE INSERT/UPDATE/DELETE 触发器，拒绝一切 DML，
--      数据库层面强制"仅允许查询"；历史数据原样冻结，不做任何改写。
-- 边界：
--   - 触发器不拦截 TRUNCATE/DROP/ALTER 等 DDL；环境级防护需配合
--     REVOKE DROP, ALTER ON npdms.pms_*_retired FROM '<app_user>'（各环境执行）。
--   - 读路径（含迁移对账 LegacyCutover* 的源表读取）指向 `_retired` 表名后继续可用。
--   - 旧入口（控制器/页面/菜单）保持只读现状，不做任何功能调整。
-- =============================================================================

-- 1. 原子重命名（单语句保证全部成功或全部不执行）
RENAME TABLE
  pms_acc_maintenance_transition TO pms_acc_maintenance_transition_retired,
  pms_customer TO pms_customer_retired,
  pms_customer_contact TO pms_customer_contact_retired,
  pms_cut_execution TO pms_cut_execution_retired,
  pms_cut_observation TO pms_cut_observation_retired,
  pms_cut_plan TO pms_cut_plan_retired,
  pms_cut_risk TO pms_cut_risk_retired,
  pms_cut_task TO pms_cut_task_retired,
  pms_equipment TO pms_equipment_retired,
  pms_equipment_version TO pms_equipment_version_retired,
  pms_plan_change_phase_snapshot TO pms_plan_change_phase_snapshot_retired,
  pms_plan_change_request TO pms_plan_change_request_retired,
  pms_project TO pms_project_retired,
  pms_project_phase TO pms_project_phase_retired,
  pms_project_phase_template TO pms_project_phase_template_retired,
  pms_project_sync_batch TO pms_project_sync_batch_retired,
  pms_project_sync_detail TO pms_project_sync_detail_retired,
  pms_project_task TO pms_project_task_retired,
  pms_project_task_dependency TO pms_project_task_dependency_retired,
  pms_project_team_member TO pms_project_team_member_retired,
  pms_project_template TO pms_project_template_retired,
  pms_project_tree_change_batch TO pms_project_tree_change_batch_retired,
  pms_srv_execution TO pms_srv_execution_retired,
  pms_srv_issue TO pms_srv_issue_retired,
  pms_srv_maintenance TO pms_srv_maintenance_retired,
  pms_srv_offline_file TO pms_srv_offline_file_retired,
  pms_srv_report TO pms_srv_report_retired,
  pms_srv_rule TO pms_srv_rule_retired,
  pms_srv_task TO pms_srv_task_retired;

-- 2. 只读强制触发器（INSERT/UPDATE/DELETE 一律拒绝）
CREATE TRIGGER trg_pms_acc_maintenance_transition_ro_insert BEFORE INSERT ON pms_acc_maintenance_transition_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_acc_maintenance_transition insert forbidden';

CREATE TRIGGER trg_pms_acc_maintenance_transition_ro_update BEFORE UPDATE ON pms_acc_maintenance_transition_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_acc_maintenance_transition update forbidden';

CREATE TRIGGER trg_pms_acc_maintenance_transition_ro_delete BEFORE DELETE ON pms_acc_maintenance_transition_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_acc_maintenance_transition delete forbidden';

CREATE TRIGGER trg_pms_customer_ro_insert BEFORE INSERT ON pms_customer_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_customer insert forbidden';

CREATE TRIGGER trg_pms_customer_ro_update BEFORE UPDATE ON pms_customer_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_customer update forbidden';

CREATE TRIGGER trg_pms_customer_ro_delete BEFORE DELETE ON pms_customer_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_customer delete forbidden';

CREATE TRIGGER trg_pms_customer_contact_ro_insert BEFORE INSERT ON pms_customer_contact_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_customer_contact insert forbidden';

CREATE TRIGGER trg_pms_customer_contact_ro_update BEFORE UPDATE ON pms_customer_contact_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_customer_contact update forbidden';

CREATE TRIGGER trg_pms_customer_contact_ro_delete BEFORE DELETE ON pms_customer_contact_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_customer_contact delete forbidden';

CREATE TRIGGER trg_pms_cut_execution_ro_insert BEFORE INSERT ON pms_cut_execution_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_cut_execution insert forbidden';

CREATE TRIGGER trg_pms_cut_execution_ro_update BEFORE UPDATE ON pms_cut_execution_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_cut_execution update forbidden';

CREATE TRIGGER trg_pms_cut_execution_ro_delete BEFORE DELETE ON pms_cut_execution_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_cut_execution delete forbidden';

CREATE TRIGGER trg_pms_cut_observation_ro_insert BEFORE INSERT ON pms_cut_observation_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_cut_observation insert forbidden';

CREATE TRIGGER trg_pms_cut_observation_ro_update BEFORE UPDATE ON pms_cut_observation_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_cut_observation update forbidden';

CREATE TRIGGER trg_pms_cut_observation_ro_delete BEFORE DELETE ON pms_cut_observation_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_cut_observation delete forbidden';

CREATE TRIGGER trg_pms_cut_plan_ro_insert BEFORE INSERT ON pms_cut_plan_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_cut_plan insert forbidden';

CREATE TRIGGER trg_pms_cut_plan_ro_update BEFORE UPDATE ON pms_cut_plan_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_cut_plan update forbidden';

CREATE TRIGGER trg_pms_cut_plan_ro_delete BEFORE DELETE ON pms_cut_plan_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_cut_plan delete forbidden';

CREATE TRIGGER trg_pms_cut_risk_ro_insert BEFORE INSERT ON pms_cut_risk_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_cut_risk insert forbidden';

CREATE TRIGGER trg_pms_cut_risk_ro_update BEFORE UPDATE ON pms_cut_risk_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_cut_risk update forbidden';

CREATE TRIGGER trg_pms_cut_risk_ro_delete BEFORE DELETE ON pms_cut_risk_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_cut_risk delete forbidden';

CREATE TRIGGER trg_pms_cut_task_ro_insert BEFORE INSERT ON pms_cut_task_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_cut_task insert forbidden';

CREATE TRIGGER trg_pms_cut_task_ro_update BEFORE UPDATE ON pms_cut_task_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_cut_task update forbidden';

CREATE TRIGGER trg_pms_cut_task_ro_delete BEFORE DELETE ON pms_cut_task_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_cut_task delete forbidden';

CREATE TRIGGER trg_pms_equipment_ro_insert BEFORE INSERT ON pms_equipment_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_equipment insert forbidden';

CREATE TRIGGER trg_pms_equipment_ro_update BEFORE UPDATE ON pms_equipment_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_equipment update forbidden';

CREATE TRIGGER trg_pms_equipment_ro_delete BEFORE DELETE ON pms_equipment_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_equipment delete forbidden';

CREATE TRIGGER trg_pms_equipment_version_ro_insert BEFORE INSERT ON pms_equipment_version_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_equipment_version insert forbidden';

CREATE TRIGGER trg_pms_equipment_version_ro_update BEFORE UPDATE ON pms_equipment_version_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_equipment_version update forbidden';

CREATE TRIGGER trg_pms_equipment_version_ro_delete BEFORE DELETE ON pms_equipment_version_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_equipment_version delete forbidden';

CREATE TRIGGER trg_pms_plan_change_phase_snapshot_ro_insert BEFORE INSERT ON pms_plan_change_phase_snapshot_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_plan_change_phase_snapshot insert forbidden';

CREATE TRIGGER trg_pms_plan_change_phase_snapshot_ro_update BEFORE UPDATE ON pms_plan_change_phase_snapshot_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_plan_change_phase_snapshot update forbidden';

CREATE TRIGGER trg_pms_plan_change_phase_snapshot_ro_delete BEFORE DELETE ON pms_plan_change_phase_snapshot_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_plan_change_phase_snapshot delete forbidden';

CREATE TRIGGER trg_pms_plan_change_request_ro_insert BEFORE INSERT ON pms_plan_change_request_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_plan_change_request insert forbidden';

CREATE TRIGGER trg_pms_plan_change_request_ro_update BEFORE UPDATE ON pms_plan_change_request_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_plan_change_request update forbidden';

CREATE TRIGGER trg_pms_plan_change_request_ro_delete BEFORE DELETE ON pms_plan_change_request_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_plan_change_request delete forbidden';

CREATE TRIGGER trg_pms_project_ro_insert BEFORE INSERT ON pms_project_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project insert forbidden';

CREATE TRIGGER trg_pms_project_ro_update BEFORE UPDATE ON pms_project_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project update forbidden';

CREATE TRIGGER trg_pms_project_ro_delete BEFORE DELETE ON pms_project_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project delete forbidden';

CREATE TRIGGER trg_pms_project_phase_ro_insert BEFORE INSERT ON pms_project_phase_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_phase insert forbidden';

CREATE TRIGGER trg_pms_project_phase_ro_update BEFORE UPDATE ON pms_project_phase_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_phase update forbidden';

CREATE TRIGGER trg_pms_project_phase_ro_delete BEFORE DELETE ON pms_project_phase_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_phase delete forbidden';

CREATE TRIGGER trg_pms_project_phase_template_ro_insert BEFORE INSERT ON pms_project_phase_template_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_phase_template insert forbidden';

CREATE TRIGGER trg_pms_project_phase_template_ro_update BEFORE UPDATE ON pms_project_phase_template_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_phase_template update forbidden';

CREATE TRIGGER trg_pms_project_phase_template_ro_delete BEFORE DELETE ON pms_project_phase_template_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_phase_template delete forbidden';

CREATE TRIGGER trg_pms_project_sync_batch_ro_insert BEFORE INSERT ON pms_project_sync_batch_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_sync_batch insert forbidden';

CREATE TRIGGER trg_pms_project_sync_batch_ro_update BEFORE UPDATE ON pms_project_sync_batch_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_sync_batch update forbidden';

CREATE TRIGGER trg_pms_project_sync_batch_ro_delete BEFORE DELETE ON pms_project_sync_batch_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_sync_batch delete forbidden';

CREATE TRIGGER trg_pms_project_sync_detail_ro_insert BEFORE INSERT ON pms_project_sync_detail_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_sync_detail insert forbidden';

CREATE TRIGGER trg_pms_project_sync_detail_ro_update BEFORE UPDATE ON pms_project_sync_detail_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_sync_detail update forbidden';

CREATE TRIGGER trg_pms_project_sync_detail_ro_delete BEFORE DELETE ON pms_project_sync_detail_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_sync_detail delete forbidden';

CREATE TRIGGER trg_pms_project_task_ro_insert BEFORE INSERT ON pms_project_task_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_task insert forbidden';

CREATE TRIGGER trg_pms_project_task_ro_update BEFORE UPDATE ON pms_project_task_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_task update forbidden';

CREATE TRIGGER trg_pms_project_task_ro_delete BEFORE DELETE ON pms_project_task_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_task delete forbidden';

CREATE TRIGGER trg_pms_project_task_dependency_ro_insert BEFORE INSERT ON pms_project_task_dependency_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_task_dependency insert forbidden';

CREATE TRIGGER trg_pms_project_task_dependency_ro_update BEFORE UPDATE ON pms_project_task_dependency_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_task_dependency update forbidden';

CREATE TRIGGER trg_pms_project_task_dependency_ro_delete BEFORE DELETE ON pms_project_task_dependency_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_task_dependency delete forbidden';

CREATE TRIGGER trg_pms_project_team_member_ro_insert BEFORE INSERT ON pms_project_team_member_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_team_member insert forbidden';

CREATE TRIGGER trg_pms_project_team_member_ro_update BEFORE UPDATE ON pms_project_team_member_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_team_member update forbidden';

CREATE TRIGGER trg_pms_project_team_member_ro_delete BEFORE DELETE ON pms_project_team_member_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_team_member delete forbidden';

CREATE TRIGGER trg_pms_project_template_ro_insert BEFORE INSERT ON pms_project_template_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_template insert forbidden';

CREATE TRIGGER trg_pms_project_template_ro_update BEFORE UPDATE ON pms_project_template_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_template update forbidden';

CREATE TRIGGER trg_pms_project_template_ro_delete BEFORE DELETE ON pms_project_template_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_template delete forbidden';

CREATE TRIGGER trg_pms_project_tree_change_batch_ro_insert BEFORE INSERT ON pms_project_tree_change_batch_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_tree_change_batch insert forbidden';

CREATE TRIGGER trg_pms_project_tree_change_batch_ro_update BEFORE UPDATE ON pms_project_tree_change_batch_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_tree_change_batch update forbidden';

CREATE TRIGGER trg_pms_project_tree_change_batch_ro_delete BEFORE DELETE ON pms_project_tree_change_batch_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_project_tree_change_batch delete forbidden';

CREATE TRIGGER trg_pms_srv_execution_ro_insert BEFORE INSERT ON pms_srv_execution_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_execution insert forbidden';

CREATE TRIGGER trg_pms_srv_execution_ro_update BEFORE UPDATE ON pms_srv_execution_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_execution update forbidden';

CREATE TRIGGER trg_pms_srv_execution_ro_delete BEFORE DELETE ON pms_srv_execution_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_execution delete forbidden';

CREATE TRIGGER trg_pms_srv_issue_ro_insert BEFORE INSERT ON pms_srv_issue_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_issue insert forbidden';

CREATE TRIGGER trg_pms_srv_issue_ro_update BEFORE UPDATE ON pms_srv_issue_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_issue update forbidden';

CREATE TRIGGER trg_pms_srv_issue_ro_delete BEFORE DELETE ON pms_srv_issue_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_issue delete forbidden';

CREATE TRIGGER trg_pms_srv_maintenance_ro_insert BEFORE INSERT ON pms_srv_maintenance_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_maintenance insert forbidden';

CREATE TRIGGER trg_pms_srv_maintenance_ro_update BEFORE UPDATE ON pms_srv_maintenance_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_maintenance update forbidden';

CREATE TRIGGER trg_pms_srv_maintenance_ro_delete BEFORE DELETE ON pms_srv_maintenance_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_maintenance delete forbidden';

CREATE TRIGGER trg_pms_srv_offline_file_ro_insert BEFORE INSERT ON pms_srv_offline_file_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_offline_file insert forbidden';

CREATE TRIGGER trg_pms_srv_offline_file_ro_update BEFORE UPDATE ON pms_srv_offline_file_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_offline_file update forbidden';

CREATE TRIGGER trg_pms_srv_offline_file_ro_delete BEFORE DELETE ON pms_srv_offline_file_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_offline_file delete forbidden';

CREATE TRIGGER trg_pms_srv_report_ro_insert BEFORE INSERT ON pms_srv_report_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_report insert forbidden';

CREATE TRIGGER trg_pms_srv_report_ro_update BEFORE UPDATE ON pms_srv_report_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_report update forbidden';

CREATE TRIGGER trg_pms_srv_report_ro_delete BEFORE DELETE ON pms_srv_report_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_report delete forbidden';

CREATE TRIGGER trg_pms_srv_rule_ro_insert BEFORE INSERT ON pms_srv_rule_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_rule insert forbidden';

CREATE TRIGGER trg_pms_srv_rule_ro_update BEFORE UPDATE ON pms_srv_rule_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_rule update forbidden';

CREATE TRIGGER trg_pms_srv_rule_ro_delete BEFORE DELETE ON pms_srv_rule_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_rule delete forbidden';

CREATE TRIGGER trg_pms_srv_task_ro_insert BEFORE INSERT ON pms_srv_task_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_task insert forbidden';

CREATE TRIGGER trg_pms_srv_task_ro_update BEFORE UPDATE ON pms_srv_task_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_task update forbidden';

CREATE TRIGGER trg_pms_srv_task_ro_delete BEFORE DELETE ON pms_srv_task_retired
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pms legacy table retired (read-only): pms_srv_task delete forbidden';

