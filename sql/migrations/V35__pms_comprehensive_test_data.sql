-- =============================================================================
-- V35__pms_comprehensive_test_data.sql
-- 目的：补全 NPMS 项目测试数据，覆盖空表、异常态/回退态、并行触发链路、
--       项目治理动作及端到端全链路样本。
-- 依赖：V5(项目同步) / V10(工程实施) / V12(割接) / V14(巡检) / V17(验收)
--       / V22(批量变更/工期倒排) / V23(治理) / V25(外包/领料/外采/换货)
--       / V19/V20(现有测试数据，项目ID 1001-1040，tenant_id=1)
-- ID范围：30001+，避免与 V19(1001-1100) / V20(2001+) / V24(1001+) 冲突
-- 幂等：文件开头 DELETE id>=30001，保证可重复执行
-- 审计字段：creator/updater='admin'，deleted=b'0'，tenant_id=1
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 0. 幂等清理：删除本脚本历史数据（id>=30001）
-- -----------------------------------------------------------------------------
DELETE FROM pms_team_batch_change            WHERE id >= 30001;
DELETE FROM pms_team_batch_change_item       WHERE id >= 30001;
DELETE FROM pms_schedule_backward            WHERE id >= 30001;
DELETE FROM pms_schedule_backward_item       WHERE id >= 30001;
DELETE FROM pms_project_sync_batch           WHERE id >= 30001;
DELETE FROM pms_project_sync_detail          WHERE id >= 30001;
DELETE FROM pms_eng_site_survey              WHERE id >= 30001;
DELETE FROM pms_eng_requirement              WHERE id >= 30001;
DELETE FROM pms_eng_solution                 WHERE id >= 30001;
DELETE FROM pms_eng_resource_ready           WHERE id >= 30001;
DELETE FROM pms_eng_arrival                  WHERE id >= 30001;
DELETE FROM pms_eng_installation             WHERE id >= 30001;
DELETE FROM pms_eng_configuration            WHERE id >= 30001;
DELETE FROM pms_eng_joint_test               WHERE id >= 30001;
DELETE FROM pms_eng_deliverable              WHERE id >= 30001;
DELETE FROM pms_eng_outsource_request        WHERE id >= 30001;
DELETE FROM pms_eng_material_requisition     WHERE id >= 30001;
DELETE FROM pms_eng_external_procurement     WHERE id >= 30001;
DELETE FROM pms_eng_material_exchange        WHERE id >= 30001;
DELETE FROM pms_cut_task                     WHERE id >= 30001;
DELETE FROM pms_cut_execution                WHERE id >= 30001;
DELETE FROM pms_cut_observation              WHERE id >= 30001;
DELETE FROM pms_srv_execution                WHERE id >= 30001;
DELETE FROM pms_srv_offline_file             WHERE id >= 30001;
DELETE FROM pms_acc_completion_certificate   WHERE id >= 30001;
DELETE FROM pms_acc_acceptance               WHERE id >= 30001;
DELETE FROM pms_acc_deliverable_checklist    WHERE id >= 30001;
DELETE FROM pms_acc_project_closure          WHERE id >= 30001;
DELETE FROM pms_acc_archive_document         WHERE id >= 30001;
DELETE FROM pms_acc_maintenance_transition   WHERE id >= 30001;
DELETE FROM pms_project_governance_action    WHERE id >= 30001;

-- =============================================================================
-- 第1部分：6张空表数据补全
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1.1 团队批量变更（pms_team_batch_change + pms_team_batch_change_item）
-- 字段参考 V22；状态：0处理中 1成功 2部分成功 3失败
-- 明细状态：0待处理 1成功 2失败
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO pms_team_batch_change
(id, batch_no, source_user_id, target_user_id, scope_type, reason, status, total_count, success_count, failure_count, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
-- 30001: 全部成功
(30001, 'TB-V35-001', 1001, 1002, 'ALL', '项目经理调岗，全部项目角色批量移交', 1, 10, 10, 0, '批量变更全部成功', 1, 'admin', '2026-07-01 09:00:00', 'admin', '2026-07-01 09:30:00', b'0', 1),
-- 30002: 部分成功
(30002, 'TB-V35-002', 1003, 1004, 'SELECTED', '技术负责人离职，指定项目角色转移', 2, 8, 6, 2, '2个项目团队成员已锁定，变更失败', 1, 'admin', '2026-07-05 10:00:00', 'admin', '2026-07-05 10:45:00', b'0', 1),
-- 30003: 全部失败
(30003, 'TB-V35-003', 1005, 1006, 'SELECTED', '跨部门角色调整', 3, 5, 0, 5, '目标用户无对应角色权限，全部失败', 1, 'admin', '2026-07-10 14:00:00', 'admin', '2026-07-10 14:20:00', b'0', 1),
-- 30004: 处理中
(30004, 'TB-V35-004', 1001, 1007, 'ALL', '组织架构调整，批量更新项目经理', 0, 12, 5, 0, '批量变更执行中，已完成5个', 1, 'admin', '2026-07-20 09:00:00', 'admin', '2026-07-20 09:00:00', b'0', 1),
-- 30005: 全部成功
(30005, 'TB-V35-005', 1008, 1009, 'SELECTED', '指定项目运维角色移交', 1, 3, 3, 0, '3个项目角色变更全部成功', 1, 'admin', '2026-07-25 11:00:00', 'admin', '2026-07-25 11:15:00', b'0', 1);

-- 明细（25条，每批次5条，覆盖0/1/2状态）
INSERT IGNORE INTO pms_team_batch_change_item
(id, batch_id, project_id, project_name, team_member_id, before_role, after_role, status, error_message, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
-- 批次30001（全部成功）
(30001, 30001, 1001, '北京华盛金融核心网络集成项目', 1001, 'PM', 'PM', 1, NULL, 'admin', '2026-07-01 09:05:00', 'admin', '2026-07-01 09:30:00', b'0', 1),
(30002, 30001, 1002, '上海云数据中心安全部署项目', 1002, 'TECH_LEAD', 'TECH_LEAD', 1, NULL, 'admin', '2026-07-01 09:05:00', 'admin', '2026-07-01 09:30:00', b'0', 1),
(30003, 30001, 1003, '广州智能制造运维服务项目', 1003, 'ENGINEER', 'ENGINEER', 1, NULL, 'admin', '2026-07-01 09:05:00', 'admin', '2026-07-01 09:30:00', b'0', 1),
(30004, 30001, 1004, '深圳金融核心系统迁移升级项目', 1004, 'PM', 'PM', 1, NULL, 'admin', '2026-07-01 09:05:00', 'admin', '2026-07-01 09:30:00', b'0', 1),
(30005, 30001, 1005, '成都智慧城市网络集成项目', 1005, 'ENGINEER', 'ENGINEER', 1, NULL, 'admin', '2026-07-01 09:05:00', 'admin', '2026-07-01 09:30:00', b'0', 1),
-- 批次30002（部分成功：3成功2失败）
(30006, 30002, 1001, '北京华盛金融核心网络集成项目', 1006, 'ENGINEER', 'TECH_LEAD', 1, NULL, 'admin', '2026-07-05 10:05:00', 'admin', '2026-07-05 10:45:00', b'0', 1),
(30007, 30002, 1002, '上海云数据中心安全部署项目', 1007, 'ENGINEER', 'TECH_LEAD', 1, NULL, 'admin', '2026-07-05 10:05:00', 'admin', '2026-07-05 10:45:00', b'0', 1),
(30008, 30002, 1003, '广州智能制造运维服务项目', 1008, 'ENGINEER', 'TECH_LEAD', 1, NULL, 'admin', '2026-07-05 10:05:00', 'admin', '2026-07-05 10:45:00', b'0', 1),
(30009, 30002, 1004, '深圳金融核心系统迁移升级项目', 1009, 'ENGINEER', 'TECH_LEAD', 2, '团队成员记录已锁定，无法变更角色', 'admin', '2026-07-05 10:05:00', 'admin', '2026-07-05 10:45:00', b'0', 1),
(30010, 30002, 1005, '成都智慧城市网络集成项目', 1010, 'ENGINEER', 'TECH_LEAD', 2, '目标用户1004无TECH_LEAD角色权限', 'admin', '2026-07-05 10:05:00', 'admin', '2026-07-05 10:45:00', b'0', 1),
-- 批次30003（全部失败）
(30011, 30003, 1006, '北京华盛办公网络安全部署项目', 1011, 'PM', 'PM', 2, '目标用户1006无PM角色权限', 'admin', '2026-07-10 14:05:00', 'admin', '2026-07-10 14:20:00', b'0', 1),
(30012, 30003, 1007, '上海云数据运维服务项目', 1012, 'TECH_LEAD', 'TECH_LEAD', 2, '团队成员记录已锁定，无法变更', 'admin', '2026-07-10 14:05:00', 'admin', '2026-07-10 14:20:00', b'0', 1),
(30013, 30003, 1008, '广州智能制造核心网络集成项目', 1013, 'ENGINEER', 'ENGINEER', 2, '目标用户1006无ENGINEER角色权限', 'admin', '2026-07-10 14:05:00', 'admin', '2026-07-10 14:20:00', b'0', 1),
(30014, 30003, 1009, '深圳金融数据中台迁移升级项目', 1014, 'ENGINEER', 'ENGINEER', 2, '项目已关闭，不允许角色变更', 'admin', '2026-07-10 14:05:00', 'admin', '2026-07-10 14:20:00', b'0', 1),
(30015, 30003, 1010, '成都智慧城市安全部署项目', 1015, 'PM', 'PM', 2, '目标用户1006无PM角色权限', 'admin', '2026-07-10 14:05:00', 'admin', '2026-07-10 14:20:00', b'0', 1),
-- 批次30004（处理中：2成功3待处理）
(30016, 30004, 1001, '北京华盛金融核心网络集成项目', 1001, 'PM', 'PM', 1, NULL, 'admin', '2026-07-20 09:05:00', 'admin', '2026-07-20 09:30:00', b'0', 1),
(30017, 30004, 1002, '上海云数据中心安全部署项目', 1002, 'PM', 'PM', 1, NULL, 'admin', '2026-07-20 09:05:00', 'admin', '2026-07-20 09:30:00', b'0', 1),
(30018, 30004, 1003, '广州智能制造运维服务项目', 1003, 'PM', 'PM', 0, NULL, 'admin', '2026-07-20 09:05:00', 'admin', '2026-07-20 09:00:00', b'0', 1),
(30019, 30004, 1004, '深圳金融核心系统迁移升级项目', 1004, 'PM', 'PM', 0, NULL, 'admin', '2026-07-20 09:05:00', 'admin', '2026-07-20 09:00:00', b'0', 1),
(30020, 30004, 1005, '成都智慧城市网络集成项目', 1005, 'PM', 'PM', 0, NULL, 'admin', '2026-07-20 09:05:00', 'admin', '2026-07-20 09:00:00', b'0', 1),
-- 批次30005（全部成功）
(30021, 30005, 1007, '上海云数据运维服务项目', 1016, 'OPS_ENGINEER', 'OPS_LEAD', 1, NULL, 'admin', '2026-07-25 11:05:00', 'admin', '2026-07-25 11:15:00', b'0', 1),
(30022, 30005, 1008, '广州智能制造核心网络集成项目', 1017, 'OPS_ENGINEER', 'OPS_LEAD', 1, NULL, 'admin', '2026-07-25 11:05:00', 'admin', '2026-07-25 11:15:00', b'0', 1),
(30023, 30005, 1010, '成都智慧城市安全部署项目', 1018, 'OPS_ENGINEER', 'OPS_LEAD', 1, NULL, 'admin', '2026-07-25 11:05:00', 'admin', '2026-07-25 11:15:00', b'0', 1),
(30024, 30005, 1001, '北京华盛金融核心网络集成项目', 1019, 'OPS_ENGINEER', 'OPS_LEAD', 1, NULL, 'admin', '2026-07-25 11:05:00', 'admin', '2026-07-25 11:15:00', b'0', 1),
(30025, 30005, 1002, '上海云数据中心安全部署项目', 1020, 'OPS_ENGINEER', 'OPS_LEAD', 1, NULL, 'admin', '2026-07-25 11:05:00', 'admin', '2026-07-25 11:15:00', b'0', 1);

-- -----------------------------------------------------------------------------
-- 1.2 工期倒排（pms_schedule_backward + pms_schedule_backward_item）
-- 字段参考 V22；状态：0草稿 1已计算 2已应用 3已驳回
-- 项目类型：DIRECT 直签 / INDIRECT 非直签
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO pms_schedule_backward
(id, project_id, target_date, project_type, status, conflict_summary, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 1001, '2026-06-30', 'DIRECT',   2, NULL, '工期倒排已应用，各阶段按建议日期调整', 1, 'admin', '2026-05-01 09:00:00', 'admin', '2026-05-10 14:00:00', b'0', 1),
(30002, 1002, '2026-07-15', 'INDIRECT', 1, '阶段3(配置调试)计划结束日期2026-07-20晚于目标日期2026-07-15，存在5天冲突', '已计算，存在冲突待处理', 1, 'admin', '2026-06-01 10:00:00', 'admin', '2026-06-05 16:00:00', b'0', 1),
(30003, 1003, '2026-05-31', 'DIRECT',   3, NULL, '倒排方案被驳回，目标日期不合理', 1, 'admin', '2026-04-01 11:00:00', 'admin', '2026-04-10 15:00:00', b'0', 1),
(30004, 1004, '2026-08-31', 'INDIRECT', 0, NULL, '草稿状态，尚未计算', 1, 'admin', '2026-07-15 14:00:00', 'admin', '2026-07-15 14:00:00', b'0', 1),
(30005, 1005, '2026-07-31', 'DIRECT',   2, NULL, '工期倒排已应用，无冲突', 1, 'admin', '2026-06-20 09:30:00', 'admin', '2026-06-28 17:00:00', b'0', 1);

-- 倒排阶段明细（25条，每条倒排5个阶段，至少1条冲突）
INSERT IGNORE INTO pms_schedule_backward_item
(id, backward_id, phase_id, phase_name, planned_start_date, planned_end_date, recommended_latest_date, has_conflict, conflict_reason, sort, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
-- 倒排30001（已应用，无冲突）
(30001, 30001, NULL, '工勘阶段',   '2026-05-01', '2026-05-10', '2026-05-10', b'0', NULL, 1, 'admin', '2026-05-01 09:00:00', 'admin', '2026-05-10 14:00:00', b'0', 1),
(30002, 30001, NULL, '需求分析',   '2026-05-11', '2026-05-20', '2026-05-20', b'0', NULL, 2, 'admin', '2026-05-01 09:00:00', 'admin', '2026-05-10 14:00:00', b'0', 1),
(30003, 30001, NULL, '实施方案',   '2026-05-21', '2026-06-05', '2026-06-05', b'0', NULL, 3, 'admin', '2026-05-01 09:00:00', 'admin', '2026-05-10 14:00:00', b'0', 1),
(30004, 30001, NULL, '工程实施',   '2026-06-06', '2026-06-25', '2026-06-25', b'0', NULL, 4, 'admin', '2026-05-01 09:00:00', 'admin', '2026-05-10 14:00:00', b'0', 1),
(30005, 30001, NULL, '验收闭环',   '2026-06-26', '2026-06-30', '2026-06-30', b'1', '验收阶段工期紧张，仅5天可用', 5, 'admin', '2026-05-01 09:00:00', 'admin', '2026-05-10 14:00:00', b'0', 1),
-- 倒排30002（已计算，阶段3有冲突）
(30006, 30002, NULL, '工勘阶段',   '2026-06-01', '2026-06-10', '2026-06-10', b'0', NULL, 1, 'admin', '2026-06-01 10:00:00', 'admin', '2026-06-05 16:00:00', b'0', 1),
(30007, 30002, NULL, '需求分析',   '2026-06-11', '2026-06-20', '2026-06-20', b'0', NULL, 2, 'admin', '2026-06-01 10:00:00', 'admin', '2026-06-05 16:00:00', b'0', 1),
(30008, 30002, NULL, '配置调试',   '2026-06-21', '2026-07-20', '2026-07-10', b'1', '计划结束日期2026-07-20晚于目标日期2026-07-15，超出5天', 3, 'admin', '2026-06-01 10:00:00', 'admin', '2026-06-05 16:00:00', b'0', 1),
(30009, 30002, NULL, '割接上线',   '2026-07-21', '2026-07-25', '2026-07-13', b'0', NULL, 4, 'admin', '2026-06-01 10:00:00', 'admin', '2026-06-05 16:00:00', b'0', 1),
(30010, 30002, NULL, '验收闭环',   '2026-07-26', '2026-07-31', '2026-07-15', b'0', NULL, 5, 'admin', '2026-06-01 10:00:00', 'admin', '2026-06-05 16:00:00', b'0', 1),
-- 倒排30003（已驳回）
(30011, 30003, NULL, '工勘阶段',   '2026-03-01', '2026-03-15', '2026-03-15', b'0', NULL, 1, 'admin', '2026-04-01 11:00:00', 'admin', '2026-04-10 15:00:00', b'0', 1),
(30012, 30003, NULL, '需求分析',   '2026-03-16', '2026-04-05', '2026-04-05', b'0', NULL, 2, 'admin', '2026-04-01 11:00:00', 'admin', '2026-04-10 15:00:00', b'0', 1),
(30013, 30003, NULL, '工程实施',   '2026-04-06', '2026-05-20', '2026-05-15', b'1', '实施工期不足，无法在目标日期前完成', 3, 'admin', '2026-04-01 11:00:00', 'admin', '2026-04-10 15:00:00', b'0', 1),
(30014, 30003, NULL, '割接上线',   '2026-05-21', '2026-05-28', '2026-05-25', b'0', NULL, 4, 'admin', '2026-04-01 11:00:00', 'admin', '2026-04-10 15:00:00', b'0', 1),
(30015, 30003, NULL, '验收闭环',   '2026-05-29', '2026-06-05', '2026-05-31', b'0', NULL, 5, 'admin', '2026-04-01 11:00:00', 'admin', '2026-04-10 15:00:00', b'0', 1),
-- 倒排30004（草稿）
(30016, 30004, NULL, '工勘阶段',   '2026-07-01', '2026-07-15', NULL, b'0', NULL, 1, 'admin', '2026-07-15 14:00:00', 'admin', '2026-07-15 14:00:00', b'0', 1),
(30017, 30004, NULL, '需求分析',   '2026-07-16', '2026-07-30', NULL, b'0', NULL, 2, 'admin', '2026-07-15 14:00:00', 'admin', '2026-07-15 14:00:00', b'0', 1),
(30018, 30004, NULL, '实施方案',   '2026-07-31', '2026-08-10', NULL, b'0', NULL, 3, 'admin', '2026-07-15 14:00:00', 'admin', '2026-07-15 14:00:00', b'0', 1),
(30019, 30004, NULL, '工程实施',   '2026-08-11', '2026-08-25', NULL, b'0', NULL, 4, 'admin', '2026-07-15 14:00:00', 'admin', '2026-07-15 14:00:00', b'0', 1),
(30020, 30004, NULL, '验收闭环',   '2026-08-26', '2026-08-31', NULL, b'0', NULL, 5, 'admin', '2026-07-15 14:00:00', 'admin', '2026-07-15 14:00:00', b'0', 1),
-- 倒排30005（已应用，1条冲突）
(30021, 30005, NULL, '工勘阶段',   '2026-06-01', '2026-06-10', '2026-06-10', b'0', NULL, 1, 'admin', '2026-06-20 09:30:00', 'admin', '2026-06-28 17:00:00', b'0', 1),
(30022, 30005, NULL, '需求分析',   '2026-06-11', '2026-06-20', '2026-06-20', b'0', NULL, 2, 'admin', '2026-06-20 09:30:00', 'admin', '2026-06-28 17:00:00', b'0', 1),
(30023, 30005, NULL, '工程实施',   '2026-06-21', '2026-07-15', '2026-07-15', b'0', NULL, 3, 'admin', '2026-06-20 09:30:00', 'admin', '2026-06-28 17:00:00', b'0', 1),
(30024, 30005, NULL, '割接上线',   '2026-07-16', '2026-07-25', '2026-07-25', b'1', '割接窗口与客户其他项目冲突，需协调', 4, 'admin', '2026-06-20 09:30:00', 'admin', '2026-06-28 17:00:00', b'0', 1),
(30025, 30005, NULL, '验收闭环',   '2026-07-26', '2026-07-31', '2026-07-31', b'0', NULL, 5, 'admin', '2026-06-20 09:30:00', 'admin', '2026-06-28 17:00:00', b'0', 1);

-- -----------------------------------------------------------------------------
-- 1.3 项目同步（pms_project_sync_batch + pms_project_sync_detail）
-- 字段参考 V5；批次状态：0处理中 1成功 2部分成功 3失败
-- 明细状态：0失败 1成功
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO pms_project_sync_batch
(id, batch_no, source_system, idempotency_key, payload_hash, total_count, success_count, failure_count, status, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 'SB-V35-001', 'CRM', 'IDEM-CRM-20260720-001', 'a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6', 10, 10, 0, 1, 'admin', '2026-07-20 08:00:00', 'admin', '2026-07-20 08:15:00', b'0', 1),
(30002, 'SB-V35-002', 'ERP', 'IDEM-ERP-20260722-001', 'b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7', 15, 12, 3, 2, 'admin', '2026-07-22 09:00:00', 'admin', '2026-07-22 09:30:00', b'0', 1),
(30003, 'SB-V35-003', 'OA',  'IDEM-OA-20260725-001',  'c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8', 5,  0, 5, 3, 'admin', '2026-07-25 10:00:00', 'admin', '2026-07-25 10:20:00', b'0', 1);

-- 同步明细（30条，每批次10条，覆盖0失败/1成功）
INSERT IGNORE INTO pms_project_sync_detail
(id, batch_id, project_code, project_id, status, error_code, error_message, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
-- 批次30001（全部成功）
(30001, 30001, 'PROJ-2026-001', 1001, 1, NULL, NULL, 'admin', '2026-07-20 08:05:00', 'admin', '2026-07-20 08:15:00', b'0', 1),
(30002, 30001, 'PROJ-2026-002', 1002, 1, NULL, NULL, 'admin', '2026-07-20 08:05:00', 'admin', '2026-07-20 08:15:00', b'0', 1),
(30003, 30001, 'PROJ-2026-003', 1003, 1, NULL, NULL, 'admin', '2026-07-20 08:05:00', 'admin', '2026-07-20 08:15:00', b'0', 1),
(30004, 30001, 'PROJ-2026-004', 1004, 1, NULL, NULL, 'admin', '2026-07-20 08:05:00', 'admin', '2026-07-20 08:15:00', b'0', 1),
(30005, 30001, 'PROJ-2026-005', 1005, 1, NULL, NULL, 'admin', '2026-07-20 08:05:00', 'admin', '2026-07-20 08:15:00', b'0', 1),
(30006, 30001, 'PROJ-2026-006', 1006, 1, NULL, NULL, 'admin', '2026-07-20 08:05:00', 'admin', '2026-07-20 08:15:00', b'0', 1),
(30007, 30001, 'PROJ-2026-007', 1007, 1, NULL, NULL, 'admin', '2026-07-20 08:05:00', 'admin', '2026-07-20 08:15:00', b'0', 1),
(30008, 30001, 'PROJ-2026-008', 1008, 1, NULL, NULL, 'admin', '2026-07-20 08:05:00', 'admin', '2026-07-20 08:15:00', b'0', 1),
(30009, 30001, 'PROJ-2026-009', 1009, 1, NULL, NULL, 'admin', '2026-07-20 08:05:00', 'admin', '2026-07-20 08:15:00', b'0', 1),
(30010, 30001, 'PROJ-2026-010', 1010, 1, NULL, NULL, 'admin', '2026-07-20 08:05:00', 'admin', '2026-07-20 08:15:00', b'0', 1),
-- 批次30002（部分成功：7成功3失败）
(30011, 30002, 'PROJ-2026-011', 1011, 1, NULL, NULL, 'admin', '2026-07-22 09:05:00', 'admin', '2026-07-22 09:30:00', b'0', 1),
(30012, 30002, 'PROJ-2026-012', 1012, 1, NULL, NULL, 'admin', '2026-07-22 09:05:00', 'admin', '2026-07-22 09:30:00', b'0', 1),
(30013, 30002, 'PROJ-2026-013', 1013, 1, NULL, NULL, 'admin', '2026-07-22 09:05:00', 'admin', '2026-07-22 09:30:00', b'0', 1),
(30014, 30002, 'PROJ-2026-014', 1014, 1, NULL, NULL, 'admin', '2026-07-22 09:05:00', 'admin', '2026-07-22 09:30:00', b'0', 1),
(30015, 30002, 'PROJ-2026-015', 1015, 1, NULL, NULL, 'admin', '2026-07-22 09:05:00', 'admin', '2026-07-22 09:30:00', b'0', 1),
(30016, 30002, 'PROJ-2026-016', 1016, 1, NULL, NULL, 'admin', '2026-07-22 09:05:00', 'admin', '2026-07-22 09:30:00', b'0', 1),
(30017, 30002, 'PROJ-2026-017', 1017, 1, NULL, NULL, 'admin', '2026-07-22 09:05:00', 'admin', '2026-07-22 09:30:00', b'0', 1),
(30018, 30002, 'PROJ-2026-018', NULL, 0, 'DUPLICATE_CODE', '项目编码PROJ-2026-018已存在，不允许重复创建', 'admin', '2026-07-22 09:05:00', 'admin', '2026-07-22 09:30:00', b'0', 1),
(30019, 30002, 'PROJ-2026-019', NULL, 0, 'INVALID_CUSTOMER', '客户编号2001不存在，无法创建项目', 'admin', '2026-07-22 09:05:00', 'admin', '2026-07-22 09:30:00', b'0', 1),
(30020, 30002, 'PROJ-2026-020', NULL, 0, 'MISSING_CONTRACT', '合同编号CONTRACT-2026-020不存在，无法创建项目', 'admin', '2026-07-22 09:05:00', 'admin', '2026-07-22 09:30:00', b'0', 1),
-- 批次30003（全部失败）
(30021, 30003, 'PROJ-2026-021', NULL, 0, 'DUPLICATE_CODE', '项目编码PROJ-2026-021已存在', 'admin', '2026-07-25 10:05:00', 'admin', '2026-07-25 10:20:00', b'0', 1),
(30022, 30003, 'PROJ-2026-022', NULL, 0, 'INVALID_CUSTOMER', '客户编号3001不存在', 'admin', '2026-07-25 10:05:00', 'admin', '2026-07-25 10:20:00', b'0', 1),
(30023, 30003, 'PROJ-2026-023', NULL, 0, 'MISSING_CONTRACT', '合同编号CONTRACT-2026-023不存在', 'admin', '2026-07-25 10:05:00', 'admin', '2026-07-25 10:20:00', b'0', 1),
(30024, 30003, 'PROJ-2026-024', NULL, 0, 'INVALID_OFFICE', '办公机构编号99不存在', 'admin', '2026-07-25 10:05:00', 'admin', '2026-07-25 10:20:00', b'0', 1),
(30025, 30003, 'PROJ-2026-025', NULL, 0, 'PAYLOAD_INVALID', '同步报文字段缺失：sales_user_id为空', 'admin', '2026-07-25 10:05:00', 'admin', '2026-07-25 10:20:00', b'0', 1),
(30026, 30003, 'PROJ-2026-026', NULL, 0, 'DUPLICATE_CODE', '项目编码PROJ-2026-026已存在', 'admin', '2026-07-25 10:05:00', 'admin', '2026-07-25 10:20:00', b'0', 1),
(30027, 30003, 'PROJ-2026-027', NULL, 0, 'INVALID_CUSTOMER', '客户编号3002不存在', 'admin', '2026-07-25 10:05:00', 'admin', '2026-07-25 10:20:00', b'0', 1),
(30028, 30003, 'PROJ-2026-028', NULL, 0, 'MISSING_CONTRACT', '合同编号CONTRACT-2026-028不存在', 'admin', '2026-07-25 10:05:00', 'admin', '2026-07-25 10:20:00', b'0', 1),
(30029, 30003, 'PROJ-2026-029', NULL, 0, 'INVALID_OFFICE', '办公机构编号98不存在', 'admin', '2026-07-25 10:05:00', 'admin', '2026-07-25 10:20:00', b'0', 1),
(30030, 30003, 'PROJ-2026-030', NULL, 0, 'PAYLOAD_INVALID', '同步报文字段缺失：project_type为空', 'admin', '2026-07-25 10:05:00', 'admin', '2026-07-25 10:20:00', b'0', 1);

-- =============================================================================
-- 第2部分：异常态/回退态样本补全
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 2.1 工程实施异常态（使用项目 1001-1009）
-- -----------------------------------------------------------------------------

-- pms_eng_resource_ready: ready_status=2异常，3条（ID 30001-30003）
INSERT IGNORE INTO pms_eng_resource_ready
(id, project_id, code, name, resource_type, equipment_id, quantity, ready_status, ready_time, ready_user_id, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 1001, 'RES-V35-001', '核心防火墙备件异常', 'SPARE', 1001, 2, 2, NULL, NULL, '备件到货后发现型号不匹配，无法就绪', 0, 'admin', '2026-07-03 09:00:00', 'admin', '2026-07-03 14:00:00', b'0', 1),
(30002, 1002, 'RES-V35-002', '测试环境搭建异常', 'TEST_ENV', 1002, 1, 2, NULL, NULL, '测试环境网络不通，无法完成就绪确认', 0, 'admin', '2026-07-05 10:00:00', 'admin', '2026-07-05 16:00:00', b'0', 1),
(30003, 1003, 'RES-V35-003', '施工工具缺失异常', 'TOOL', 1003, 5, 2, NULL, NULL, '光纤熔接工具未到位，影响施工进度', 0, 'admin', '2026-07-08 11:00:00', 'admin', '2026-07-08 15:00:00', b'0', 1);

-- pms_eng_arrival: status=2异常，3条（ID 30001-30003）
INSERT IGNORE INTO pms_eng_arrival
(id, project_id, code, arrival_time, receiver_user_id, equipment_id, quantity, inspection_result, exception_record, attachment_url, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 1004, 'ARR-V35-001', '2026-07-10 09:00:00', 1, 1004, 3, '外观检查通过，但型号与订单不符', '到货设备型号为S6850-56HF，订单要求S6850-48XT', NULL, 2, '型号不匹配，已发起换货流程', 0, 'admin', '2026-07-10 09:00:00', 'admin', '2026-07-10 14:00:00', b'0', 1),
(30002, 1005, 'ARR-V35-002', '2026-07-12 10:00:00', 2, 1005, 2, '外包装破损', '设备外包装严重破损，疑似运输损坏', NULL, 2, '包装破损，待供应商确认', 0, 'admin', '2026-07-12 10:00:00', 'admin', '2026-07-12 16:00:00', b'0', 1),
(30003, 1006, 'ARR-V35-003', '2026-07-15 14:00:00', 3, 1006, 1, '数量短缺', '订单要求3台，实际到货1台', NULL, 2, '数量短缺，已联系供应商补发', 0, 'admin', '2026-07-15 14:00:00', 'admin', '2026-07-15 17:00:00', b'0', 1);

-- pms_eng_installation: status=3异常，3条（ID 30001-30003）
INSERT IGNORE INTO pms_eng_installation
(id, project_id, code, equipment_id, install_location, install_time, installer_user_id, environment_check, spec_check, photo_url, result, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 1004, 'INST-V35-001', 1004, '深圳金融机房D04', '2026-07-16 09:00:00', 1, '机房温度偏高32度', '机柜空间不足，无法按规范安装', NULL, '安装中断，需调整机柜布局', 3, '机柜空间不足导致安装异常', 0, 'admin', '2026-07-16 09:00:00', 'admin', '2026-07-16 15:00:00', b'0', 1),
(30002, 1005, 'INST-V35-002', 1005, '成都智慧城市机房E05', '2026-07-18 10:00:00', 2, '环境检查通过', '设备支架规格不匹配', NULL, '支架孔位不对，需重新定制支架', 3, '支架不匹配导致安装异常', 0, 'admin', '2026-07-18 10:00:00', 'admin', '2026-07-18 16:00:00', b'0', 1),
(30003, 1006, 'INST-V35-003', 1006, '北京华盛机房A02', '2026-07-20 14:00:00', 3, '供电电压不稳定', '接地电阻超标', NULL, '接地不达标，设备无法上电', 3, '接地异常导致安装中断', 0, 'admin', '2026-07-20 14:00:00', 'admin', '2026-07-20 17:00:00', b'0', 1);

-- pms_eng_configuration: status=3异常，3条（ID 30001-30003）
INSERT IGNORE INTO pms_eng_configuration
(id, project_id, code, equipment_id, config_log_url, debug_result, debugger_user_id, debug_time, config_snapshot, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 1007, 'CONF-V35-001', 1007, '/api/file/conf-v35-001.log', '配置下发失败，设备返回权限不足错误', 1, '2026-07-22 09:00:00', NULL, 3, '设备账号权限不足，配置下发失败', 0, 'admin', '2026-07-22 09:00:00', 'admin', '2026-07-22 14:00:00', b'0', 1),
(30002, 1008, 'CONF-V35-002', 1008, '/api/file/conf-v35-002.log', 'OSPF邻居关系无法建立，区域配置不一致', 2, '2026-07-24 10:00:00', NULL, 3, '路由协议配置异常，邻居无法建立', 0, 'admin', '2026-07-24 10:00:00', 'admin', '2026-07-24 16:00:00', b'0', 1),
(30003, 1009, 'CONF-V35-003', 1009, '/api/file/conf-v35-003.log', 'VPN隧道频繁中断，IKE协商参数不匹配', 3, '2026-07-26 14:00:00', NULL, 3, 'VPN配置参数不匹配导致隧道异常', 0, 'admin', '2026-07-26 14:00:00', 'admin', '2026-07-26 17:00:00', b'0', 1);

-- pms_eng_joint_test: status=3失败，3条（ID 30001-30003）
INSERT IGNORE INTO pms_eng_joint_test
(id, project_id, code, test_case, equipment_id, participants, test_time, tester_user_id, result, exception_record, evidence_url, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 1007, 'TEST-V35-001', '核心业务连通性测试', 1007, '甲方运维团队、乙方实施团队', '2026-07-23 09:00:00', 1, '3条业务连通性测试失败', '跨网段业务流量无法正常转发，疑似ACL配置错误', '/api/file/test-v35-001.log', 3, '联调测试失败，需排查ACL配置', 0, 'admin', '2026-07-23 09:00:00', 'admin', '2026-07-23 16:00:00', b'0', 1),
(30002, 1008, 'TEST-V35-002', '高可用切换测试', 1008, '甲方网络组、乙方实施组', '2026-07-25 10:00:00', 2, '主备切换后业务中断超过30秒', 'HA心跳线连接异常，切换时间超预期', '/api/file/test-v35-002.log', 3, 'HA切换测试失败，心跳异常', 0, 'admin', '2026-07-25 10:00:00', 'admin', '2026-07-25 17:00:00', b'0', 1),
(30003, 1009, 'TEST-V35-003', 'IPSec VPN性能测试', 1009, '甲方安全部、乙方实施组', '2026-07-27 14:00:00', 3, 'VPN吞吐量仅达标的60%', 'IPSec加密性能不足，疑似硬件加速未启用', '/api/file/test-v35-003.log', 3, 'VPN性能测试不达标', 0, 'admin', '2026-07-27 14:00:00', 'admin', '2026-07-27 17:00:00', b'0', 1);

-- -----------------------------------------------------------------------------
-- 2.2 割接回退态（使用项目 1001-1004）
-- pms_cut_task: status 7已回退 / 8已终止
-- pms_cut_execution: status 3失败 / 4已回退
-- pms_cut_observation: status 2异常
-- -----------------------------------------------------------------------------

-- pms_cut_task: 2条已回退 + 2条已终止（ID 30001-30004）
INSERT IGNORE INTO pms_cut_task
(id, project_id, code, name, cutover_type, network_mode, source_type, risk_level, scheduled_time, actual_time, status, approval_opinion, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 1001, 'CUT-V35-001', '北京华盛核心网络割接-回退', 'REPLACE', 'DUAL', 'PROJECT', 'B', '2026-07-10 02:00:00', '2026-07-10 03:30:00', 7, '同意割接，但需做好回退准备', '割接执行失败已回退', 1, 'admin', '2026-07-05 09:00:00', 'admin', '2026-07-10 04:00:00', b'0', 1),
(30002, 1002, 'CUT-V35-002', '上海云数据防火墙割接-回退', 'UPGRADE', 'SINGLE', 'MANUAL', 'A', '2026-07-12 01:00:00', '2026-07-12 02:45:00', 7, '高风险割接，需A/B角双人在岗', '升级后业务异常已回退', 1, 'admin', '2026-07-08 10:00:00', 'admin', '2026-07-12 03:00:00', b'0', 1),
(30003, 1003, 'CUT-V35-003', '广州智造交换机割接-终止', 'ACCESS', 'CLUSTER', 'MANUAL', 'B', '2026-07-15 02:00:00', NULL, 8, '同意割接', '客户临时取消，已终止', 1, 'admin', '2026-07-10 11:00:00', 'admin', '2026-07-15 08:00:00', b'0', 1),
(30004, 1004, 'CUT-V35-004', '深圳金融迁移割接-终止', 'CONFIG', 'DUAL', 'PROJECT', 'C', '2026-07-18 03:00:00', NULL, 8, '同意配置变更割接', '前置检查未通过，已终止', 1, 'admin', '2026-07-12 14:00:00', 'admin', '2026-07-18 09:00:00', b'0', 1);

-- pms_cut_execution: 2条失败 + 2条已回退（ID 30001-30004）
INSERT IGNORE INTO pms_cut_execution
(id, task_id, code, step_name, operator_user_id, operation_time, result, exception_record, evidence_url, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 30001, 'CUTE-V35-001', '核心交换机替换', 1, '2026-07-10 02:30:00', '替换后业务不通', '新设备配置导入后OSPF邻居无法建立，业务中断超过15分钟', '/api/file/cute-v35-001.log', 3, '执行失败，触发回退', 0, 'admin', '2026-07-10 02:30:00', 'admin', '2026-07-10 03:30:00', b'0', 1),
(30002, 30002, 'CUTE-V35-002', '防火墙版本升级', 2, '2026-07-12 01:30:00', '升级后VPN隧道全部中断', '新版本IKEv2协商参数变更，导致已配置VPN隧道无法建立', '/api/file/cute-v35-002.log', 3, '执行失败，触发回退', 0, 'admin', '2026-07-12 01:30:00', 'admin', '2026-07-12 02:45:00', b'0', 1),
(30003, 30003, 'CUTE-V35-003', '交换机入网配置', 3, NULL, NULL, NULL, NULL, 4, '任务终止，执行记录标记已回退', 0, 'admin', '2026-07-15 02:00:00', 'admin', '2026-07-15 08:00:00', b'0', 1),
(30004, 30004, 'CUTE-V35-004', '路由配置变更', 1, NULL, NULL, NULL, NULL, 4, '任务终止，执行记录标记已回退', 0, 'admin', '2026-07-18 03:00:00', 'admin', '2026-07-18 09:00:00', b'0', 1);

-- pms_cut_observation: status=2异常，3条（ID 30001-30003）
INSERT IGNORE INTO pms_cut_observation
(id, task_id, code, observation_start, observation_end, observer_user_id, leftover_items, leftover_status, conclusion, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 30001, 'CUTO-V35-001', '2026-07-10 03:30:00', '2026-07-10 06:00:00', 1, '回退后部分区域路由收敛缓慢', 1, '回退后业务恢复，但观察期发现路由收敛异常', 2, '观察期发现路由收敛异常', 0, 'admin', '2026-07-10 03:30:00', 'admin', '2026-07-10 06:00:00', b'0', 1),
(30002, 30002, 'CUTO-V35-002', '2026-07-12 02:45:00', '2026-07-12 05:30:00', 2, 'VPN隧道需重新配置参数', 1, '回退后VPN恢复，但部分隧道性能下降', 2, '观察期发现VPN性能异常', 0, 'admin', '2026-07-12 02:45:00', 'admin', '2026-07-12 05:30:00', b'0', 1),
(30003, 30003, 'CUTO-V35-003', NULL, NULL, 3, NULL, 0, '任务终止，无需观察', 2, '任务已终止，观察记录标记异常', 0, 'admin', '2026-07-15 08:00:00', 'admin', '2026-07-15 08:00:00', b'0', 1);

-- -----------------------------------------------------------------------------
-- 2.3 巡检异常态（引用现有 pms_srv_task ID 1001-1005）
-- pms_srv_execution: status=3异常
-- pms_srv_offline_file: parse_status=3解析失败
-- -----------------------------------------------------------------------------

-- pms_srv_execution: status=3异常，3条（ID 30001-30003）
INSERT IGNORE INTO pms_srv_execution
(id, task_id, code, rule_id, execution_time, executor_user_id, result, exception_record, evidence_url, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 1001, 'EXE-V35-001', NULL, '2026-07-18 10:00:00', 1, '巡检执行失败', '设备SNMP连接超时，无法获取CPU/内存指标', '/api/file/exe-v35-001.log', 3, 'SNMP连接超时导致巡检异常', 0, 'admin', '2026-07-18 10:00:00', 'admin', '2026-07-18 12:00:00', b'0', 1),
(30002, 1002, 'EXE-V35-002', NULL, '2026-07-20 14:00:00', 2, '巡检结果异常', '设备CPU利用率持续95%以上，触发告警阈值', '/api/file/exe-v35-002.log', 3, 'CPU高利用率异常', 0, 'admin', '2026-07-20 14:00:00', 'admin', '2026-07-20 16:00:00', b'0', 1),
(30003, 1004, 'EXE-V35-003', NULL, '2026-07-22 09:00:00', 3, '巡检执行异常', 'SSH登录失败，设备账号可能已过期', '/api/file/exe-v35-003.log', 3, '账号过期导致巡检异常', 0, 'admin', '2026-07-22 09:00:00', 'admin', '2026-07-22 11:00:00', b'0', 1);

-- pms_srv_offline_file: parse_status=3解析失败，3条（ID 30001-30003）
INSERT IGNORE INTO pms_srv_offline_file
(id, task_id, code, file_url, file_size, file_checksum, parse_status, parse_result, error_detail, parsed_by, parsed_time, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 1003, 'OFF-V35-001', '/api/file/offline-v35-001.cfg', 51200, 'off001hash', 3, NULL, '文件格式不识别：期望JSON格式，实际为纯文本CLI输出', 1, '2026-07-19 10:00:00', '离线巡检文件格式不匹配', 0, 'admin', '2026-07-19 09:00:00', 'admin', '2026-07-19 10:00:00', b'0', 1),
(30002, 1004, 'OFF-V35-002', '/api/file/offline-v35-002.cfg', 0, NULL, 3, NULL, '文件大小为0，疑似上传失败', 2, '2026-07-21 14:00:00', '空文件导致解析失败', 0, 'admin', '2026-07-21 10:00:00', 'admin', '2026-07-21 14:00:00', b'0', 1),
(30003, 1005, 'OFF-V35-003', '/api/file/offline-v35-003.cfg', 102400, 'off003hash', 3, NULL, 'JSON解析失败：第15行缺少闭合括号，字段show_version值不完整', 3, '2026-07-23 09:00:00', 'JSON格式错误导致解析失败', 0, 'admin', '2026-07-23 08:00:00', 'admin', '2026-07-23 09:00:00', b'0', 1);

-- =============================================================================
-- 第3部分：并行触发链路样本
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 3.1 方案通过→资源就绪→外包/领料/外采并行触发（项目1001）
-- pms_eng_resource_ready 无 trigger_source 字段（V10），用 remark 标注
-- pms_eng_outsource_request / material_requisition / external_procurement
--   有 trigger_source 字段（V25）
-- -----------------------------------------------------------------------------

-- pms_eng_solution: status=3已通过（ID 30001）
INSERT IGNORE INTO pms_eng_solution
(id, project_id, code, name, solution_type, target, review_level, status, approved_by, approved_time, approval_opinion, baseline_version, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 1001, 'SOL-V35-001', '北京华盛金融核心网络集成实施方案V2', 'IMPLEMENTATION', '完成核心网络升级，确保业务高可用', 1, 3, 1, '2026-07-02 16:00:00', '方案审核通过，可进入资源就绪阶段', 1, '方案通过后触发资源就绪流程', 1, 'admin', '2026-06-20 09:00:00', 'admin', '2026-07-02 16:00:00', b'0', 1);

-- pms_eng_resource_ready: ready_status=1已就绪（ID 30004），remark标注触发来源
INSERT IGNORE INTO pms_eng_resource_ready
(id, project_id, code, name, resource_type, equipment_id, quantity, ready_status, ready_time, ready_user_id, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30004, 1001, 'RES-V35-004', '核心网络设备备件就绪', 'SPARE', 1001, 5, 1, '2026-07-03 10:00:00', 1, '触发来源: SOLUTION(方案30001)，方案通过后资源就绪确认', 1, 'admin', '2026-07-02 17:00:00', 'admin', '2026-07-03 10:00:00', b'0', 1);

-- pms_eng_outsource_request: trigger_source=RESOURCE_READY，status=3已通过（ID 30001）
INSERT IGNORE INTO pms_eng_outsource_request
(id, project_id, code, name, outsource_type, work_content, work_quantity, work_unit, planned_start_date, planned_end_date, estimated_cost, actual_cost, currency, trigger_source, trigger_ref_id, applicant_user_id, apply_time, approver_user_id, approve_time, approve_opinion, approve_action, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 1001, 'OS-V35-001', '核心网络设备安装外包', 'LABOR', '<p>核心交换机及防火墙安装劳务外包，含机柜上架、布线、基础配置</p>', 15.00, '人天', '2026-07-05', '2026-07-15', 45000.00, 43000.00, 'CNY', 'RESOURCE_READY', 30004, 1, '2026-07-03 11:00:00', 2, '2026-07-03 17:00:00', '同意外包', 'PASS', 3, '由资源就绪30004触发，外包安装服务', 1, 'admin', '2026-07-03 11:00:00', 'admin', '2026-07-03 17:00:00', b'0', 1);

-- pms_eng_material_requisition: trigger_source=RESOURCE_READY，status=3已通过（ID 30001）
INSERT IGNORE INTO pms_eng_material_requisition
(id, project_id, code, name, requisition_type, equipment_id, material_name, material_code, specification, quantity, unit, needed_date, trigger_source, trigger_ref_id, applicant_user_id, apply_time, approver_user_id, approve_time, approve_opinion, approve_action, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 1001, 'MR-V35-001', '核心网络光模块领料', 'SPARE', 1001, '万兆光模块', 'MM-SFP-10G-LR', '10Gbase-LR 单模', 20, '个', '2026-07-06', 'RESOURCE_READY', 30004, 1, '2026-07-03 11:30:00', 2, '2026-07-03 17:30:00', '同意领料', 'PASS', 3, '由资源就绪30004触发，领用光模块', 1, 'admin', '2026-07-03 11:30:00', 'admin', '2026-07-03 17:30:00', b'0', 1);

-- pms_eng_external_procurement: trigger_source=RESOURCE_READY，status=2审批中（ID 30001）
INSERT IGNORE INTO pms_eng_external_procurement
(id, project_id, code, name, procurement_type, material_name, material_code, specification, brand, model, quantity, unit, unit_price, total_price, currency, needed_date, expected_delivery_date, trigger_source, trigger_ref_id, applicant_user_id, apply_time, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 1001, 'EP-V35-001', '核心交换机堆叠线缆外采', 'GOODS', '堆叠线缆', 'STACK-CABLE-1M', '1米40G堆叠线缆', '华为', 'STACK-CABLE-1M', 8, '根', 1200.00, 9600.00, 'CNY', '2026-07-08', '2026-07-12', 'RESOURCE_READY', 30004, 1, '2026-07-03 14:00:00', 2, '由资源就绪30004触发，外采堆叠线缆', 1, 'admin', '2026-07-03 14:00:00', 'admin', '2026-07-03 14:00:00', b'0', 1);

-- -----------------------------------------------------------------------------
-- 3.2 物料换货链路（项目1002）
-- pms_eng_arrival: status=2异常，remark='型号不匹配'
-- pms_eng_material_exchange: exchange_type=INCOMPATIBLE，crm_push_status=CLOSED
-- -----------------------------------------------------------------------------

-- pms_eng_arrival: status=2异常（ID 30004），remark='型号不匹配'
INSERT IGNORE INTO pms_eng_arrival
(id, project_id, code, arrival_time, receiver_user_id, equipment_id, quantity, inspection_result, exception_record, attachment_url, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30004, 1002, 'ARR-V35-004', '2026-07-06 09:00:00', 2, 1002, 2, '外观检查通过，但型号与BOM不一致', '到货设备型号为IPS-5500，BOM要求IPS-5500-T（吞吐增强版）', NULL, 2, '型号不匹配，已发起换货流程', 0, 'admin', '2026-07-06 09:00:00', 'admin', '2026-07-06 14:00:00', b'0', 1);

-- pms_eng_material_exchange: exchange_type=INCOMPATIBLE，status=3已通过，crm_push_status=CLOSED（ID 30001）
INSERT IGNORE INTO pms_eng_material_exchange
(id, project_id, code, name, exchange_type, equipment_id, material_name, material_code, specification, quantity, unit, original_order_no, reason, crm_push_status, crm_push_time, crm_order_no, new_equipment_id, applicant_user_id, apply_time, approver_user_id, approve_time, approve_opinion, approve_action, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 1002, 'ME-V35-001', 'IPS-5500型号不匹配换货', 'INCOMPATIBLE', 1002, '深信服 IPS', 'IPS-5500', 'IPS-5500 标准版', 2, '台', 'PO-2026-006', '<p>到货设备型号为IPS-5500标准版，与BOM要求的IPS-5500-T（吞吐增强版）不匹配，无法满足业务吞吐需求，申请换货。</p>', 'CLOSED', '2026-07-06 16:00:00', 'PO-2026-006-R01', NULL, 2, '2026-07-06 15:00:00', 1, '2026-07-07 10:00:00', '同意换货，已推送CRM', 'PASS', 3, '到货型号不匹配，CRM已关闭换货单', 1, 'admin', '2026-07-06 15:00:00', 'admin', '2026-07-08 14:00:00', b'0', 1);

-- -----------------------------------------------------------------------------
-- 3.3 割接回退链路（项目1003）
-- pms_cut_task: status=7已回退
-- pms_cut_execution: status=4已回退
-- pms_cut_observation: status=2异常
-- -----------------------------------------------------------------------------

-- pms_cut_task: status=7已回退（ID 30005）
INSERT IGNORE INTO pms_cut_task
(id, project_id, code, name, cutover_type, network_mode, source_type, risk_level, scheduled_time, actual_time, status, approval_opinion, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30005, 1003, 'CUT-V35-005', '广州智造核心交换机割接-回退', 'REPLACE', 'CLUSTER', 'PROJECT', 'A', '2026-07-20 02:00:00', '2026-07-20 04:00:00', 7, '高风险割接，需总经理审批', '执行失败已回退', 1, 'admin', '2026-07-15 09:00:00', 'admin', '2026-07-20 05:00:00', b'0', 1);

-- pms_cut_execution: status=4已回退（ID 30005），remark='执行失败已回退'
INSERT IGNORE INTO pms_cut_execution
(id, task_id, code, step_name, operator_user_id, operation_time, result, exception_record, evidence_url, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30005, 30005, 'CUTE-V35-005', '核心交换机集群替换', 3, '2026-07-20 02:30:00', '替换后集群无法建立', '新交换机与旧设备集群协议版本不兼容，集群无法建立', '/api/file/cute-v35-005.log', 4, '执行失败已回退', 0, 'admin', '2026-07-20 02:30:00', 'admin', '2026-07-20 04:00:00', b'0', 1);

-- pms_cut_observation: status=2异常（ID 30004），remark='观察期发现异常'
INSERT IGNORE INTO pms_cut_observation
(id, task_id, code, observation_start, observation_end, observer_user_id, leftover_items, leftover_status, conclusion, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30004, 30005, 'CUTO-V35-004', '2026-07-20 04:00:00', '2026-07-20 06:30:00', 3, '集群协议版本需升级后重新割接', 1, '回退后业务恢复，但观察期发现部分流量仍走旧路径', 2, '观察期发现异常', 0, 'admin', '2026-07-20 04:00:00', 'admin', '2026-07-20 06:30:00', b'0', 1);

-- =============================================================================
-- 第4部分：项目状态补全
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 4.1 补充 status=3(已关闭) 的项目（1014、1015）
-- -----------------------------------------------------------------------------
UPDATE pms_project SET status = 3, updater = 'admin', update_time = NOW() WHERE id = 1014 AND deleted = b'0';
UPDATE pms_project SET status = 3, updater = 'admin', update_time = NOW() WHERE id = 1015 AND deleted = b'0';

-- -----------------------------------------------------------------------------
-- 4.2 项目治理 DIRECT_CLOSE 已执行样本（项目1015）
-- 字段参考 V23 pms_project_governance_action
-- 状态：0草稿 1已提交 2审批中 3已执行 4已驳回 5已撤回
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO pms_project_governance_action
(id, project_id, action_no, action_type, reason, proof_files, applicant_user_id, apply_time, approver_user_id, approve_time, approve_opinion, before_project_status, after_project_status, before_manager_user_id, after_manager_user_id, execute_time, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30001, 1015, 'GOV-V35-001', 'DIRECT_CLOSE', '客户因预算调整取消该项目子节点，申请直接关闭', '["https://files.pms.local/gov/v35-001-proof.pdf"]', 2, '2026-07-26 09:00:00', 1, '2026-07-27 14:00:00', '同意直接关闭，客户已书面确认', 1, 3, 2, NULL, '2026-07-27 15:00:00', 3, '直接关闭已执行，项目状态变更为已关闭', 1, 'admin', '2026-07-26 09:00:00', 'admin', '2026-07-27 15:00:00', b'0', 1);

-- =============================================================================
-- 第5部分：端到端全链路项目（项目1006）
-- 从工勘到验收关闭的完整生命周期，每个环节1条
-- ID 范围 30006-30021
-- =============================================================================

-- 30006: pms_eng_site_survey - status=3已归档
INSERT IGNORE INTO pms_eng_site_survey
(id, project_id, code, name, survey_date, surveyor_user_id, location, conclusion, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30006, 1006, 'SURVEY-V35-006', '北京华盛办公网络安全部署工勘', '2026-01-20', 3, '北京华盛办公区机房A02', '机房条件满足安全设备部署要求', 3, '工勘已完成并归档', 1, 'admin', '2026-01-20 09:00:00', 'admin', '2026-01-25 17:00:00', b'0', 1);

-- 30007: pms_eng_requirement - status=2已生效
INSERT IGNORE INTO pms_eng_requirement
(id, project_id, code, name, requirement_type, background, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30007, 1006, 'REQ-V35-006', '北京华盛办公网络安全部署需求', 'BUSINESS', '办公网络安全升级，部署下一代防火墙和上网行为管理', 2, '需求已生效', 1, 'admin', '2026-01-26 09:00:00', 'admin', '2026-02-01 17:00:00', b'0', 1);

-- 30008: pms_eng_solution - status=3已通过
INSERT IGNORE INTO pms_eng_solution
(id, project_id, code, name, solution_type, target, review_level, status, approved_by, approved_time, approval_opinion, baseline_version, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30008, 1006, 'SOL-V35-006', '北京华盛办公网络安全部署方案', 'IMPLEMENTATION', '完成办公网络安全设备部署与策略配置', 0, 3, 1, '2026-02-05 16:00:00', '方案通过', 1, '基线版本1', 1, 'admin', '2026-02-02 09:00:00', 'admin', '2026-02-05 16:00:00', b'0', 1);

-- 30009: pms_eng_resource_ready - ready_status=1已就绪
INSERT IGNORE INTO pms_eng_resource_ready
(id, project_id, code, name, resource_type, equipment_id, quantity, ready_status, ready_time, ready_user_id, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30009, 1006, 'RES-V35-006', '办公网络安全设备备件就绪', 'SPARE', 1006, 2, 1, '2026-02-08 10:00:00', 3, '防火墙及上网行为管理设备已就绪', 1, 'admin', '2026-02-06 09:00:00', 'admin', '2026-02-08 10:00:00', b'0', 1);

-- 30010: pms_eng_arrival - status=1已签收
INSERT IGNORE INTO pms_eng_arrival
(id, project_id, code, arrival_time, receiver_user_id, equipment_id, quantity, inspection_result, exception_record, attachment_url, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30010, 1006, 'ARR-V35-006', '2026-02-10 09:00:00', 3, 1006, 2, '外观完好，清单核对一致', NULL, '/api/file/arr-v35-006.pdf', 1, '设备已签收', 1, 'admin', '2026-02-10 09:00:00', 'admin', '2026-02-10 10:00:00', b'0', 1);

-- 30011: pms_eng_installation - status=2已完成
INSERT IGNORE INTO pms_eng_installation
(id, project_id, code, equipment_id, install_location, install_time, installer_user_id, environment_check, spec_check, photo_url, result, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30011, 1006, 'INST-V35-006', 1006, '北京华盛机房A02', '2026-02-12 09:00:00', 3, '环境检查通过', '安装规范检查通过', '/api/file/inst-v35-006.jpg', '设备安装完成，上架正常', 2, '安装已完成', 1, 'admin', '2026-02-12 09:00:00', 'admin', '2026-02-12 17:00:00', b'0', 1);

-- 30012: pms_eng_configuration - status=2已完成
INSERT IGNORE INTO pms_eng_configuration
(id, project_id, code, equipment_id, config_log_url, debug_result, debugger_user_id, debug_time, config_snapshot, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30012, 1006, 'CONF-V35-006', 1006, '/api/file/conf-v35-006.log', '配置下发成功，所有策略生效', 3, '2026-02-15 10:00:00', NULL, 2, '配置调试完成', 1, 'admin', '2026-02-13 09:00:00', 'admin', '2026-02-15 17:00:00', b'0', 1);

-- 30013: pms_eng_joint_test - status=2通过
INSERT IGNORE INTO pms_eng_joint_test
(id, project_id, code, test_case, equipment_id, participants, test_time, tester_user_id, result, exception_record, evidence_url, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30013, 1006, 'TEST-V35-006', '办公网络安全策略联调测试', 1006, '甲方IT部、乙方实施组', '2026-02-18 14:00:00', 3, '全部测试用例通过，安全策略生效', NULL, '/api/file/test-v35-006.log', 2, '联调测试通过', 1, 'admin', '2026-02-18 14:00:00', 'admin', '2026-02-18 17:00:00', b'0', 1);

-- 30014: pms_eng_deliverable - status=1已归集
INSERT IGNORE INTO pms_eng_deliverable
(id, project_id, code, name, deliverable_type, source_type, source_id, file_url, file_size, file_checksum, status, archived_time, archived_by, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30014, 1006, 'DELIV-V35-006', '北京华盛办公网络安全部署完工交付件', 'COMPLETION', 'JOINT_TEST', 30013, '/api/file/deliv-v35-006.pdf', 204800, 'deliv006hash', 1, '2026-02-20 10:00:00', 3, '交付件已归集', 1, 'admin', '2026-02-19 09:00:00', 'admin', '2026-02-20 10:00:00', b'0', 1);

-- 30015: pms_cut_task - status=6已完成
INSERT IGNORE INTO pms_cut_task
(id, project_id, code, name, cutover_type, network_mode, source_type, risk_level, scheduled_time, actual_time, status, approval_opinion, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30015, 1006, 'CUT-V35-006', '北京华盛办公网络安全割接', 'REPLACE', 'SINGLE', 'PROJECT', 'C', '2026-02-22 02:00:00', '2026-02-22 02:00:00', 6, '同意割接', '割接已完成', 1, 'admin', '2026-02-20 14:00:00', 'admin', '2026-02-22 06:00:00', b'0', 1);

-- 30016: pms_acc_completion_certificate - status=3已归档
INSERT IGNORE INTO pms_acc_completion_certificate
(id, project_id, code, name, certificate_no, customer_id, completion_date, customer_confirm_user_id, customer_confirm_time, archive_time, content, attachment_url, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30016, 1006, 'CERT-V35-006', '北京华盛办公网络安全部署完工证明', 'CERT-2026-V35-006', 1001, '2026-02-25', 1, '2026-02-25 15:00:00', '2026-02-26 10:00:00', '项目已完工，设备部署到位，策略配置完成', '/api/file/cert-v35-006.pdf', 3, '完工证明已归档', 1, 'admin', '2026-02-25 09:00:00', 'admin', '2026-02-26 10:00:00', b'0', 1);

-- 30017: pms_acc_acceptance - status=3已通过
INSERT IGNORE INTO pms_acc_acceptance
(id, project_id, code, name, acceptance_type, acceptance_date, applicant_user_id, apply_time, approver_user_id, approve_time, approve_opinion, archive_time, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30017, 1006, 'ACC-V35-006', '北京华盛办公网络安全部署终验', 'FINAL', '2026-02-28', 3, '2026-02-26 14:00:00', 1, '2026-02-28 16:00:00', '验收通过，各项指标达标', '2026-02-28 17:00:00', 3, '终验已通过', 1, 'admin', '2026-02-26 14:00:00', 'admin', '2026-02-28 17:00:00', b'0', 1);

-- 30018: pms_acc_deliverable_checklist - status=2已通过（schema: 0草稿 1已提交 2已通过 3已驳回）
INSERT IGNORE INTO pms_acc_deliverable_checklist
(id, project_id, code, name, acceptance_id, deliverable_type, deliverable_url, check_user_id, check_time, check_result, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30018, 1006, 'CHECK-V35-006', '北京华盛办公网络安全部署交付件检查', 30017, 'REQUIRED', '/api/file/check-v35-006.pdf', 1, '2026-02-28 15:00:00', '所有必交交付件齐全，检查通过', 2, '交付件检查已通过', 1, 'admin', '2026-02-27 09:00:00', 'admin', '2026-02-28 15:00:00', b'0', 1);

-- 30019: pms_acc_project_closure - status=3已通过
INSERT IGNORE INTO pms_acc_project_closure
(id, project_id, code, name, closure_type, applicant_user_id, apply_time, approver_user_id, approve_time, approve_opinion, legacy_issue_summary, archive_time, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30019, 1006, 'CLOSURE-V35-006', '北京华盛办公网络安全部署项目闭环', 'NORMAL', 3, '2026-03-01 09:00:00', 1, '2026-03-02 14:00:00', '同意闭环', '无遗留问题', '2026-03-02 15:00:00', 3, '项目闭环已通过', 1, 'admin', '2026-03-01 09:00:00', 'admin', '2026-03-02 15:00:00', b'0', 1);

-- 30020: pms_acc_archive_document - status=2已归档
INSERT IGNORE INTO pms_acc_archive_document
(id, project_id, code, name, document_type, document_url, version_no, archive_user_id, archive_time, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30020, 1006, 'DOC-V35-006', '北京华盛办公网络安全部署归档文档', 'ACCEPTANCE', '/api/file/doc-v35-006.pdf', 'v1.0', 3, '2026-03-03 10:00:00', 2, '归档文档已归档', 1, 'admin', '2026-03-03 09:00:00', 'admin', '2026-03-03 10:00:00', b'0', 1);

-- 30021: pms_acc_maintenance_transition - status=2生效中
INSERT IGNORE INTO pms_acc_maintenance_transition
(id, project_id, code, name, equipment_id, acceptance_id, maintenance_years, start_date, end_date, activate_user_id, activate_time, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30021, 1006, 'MAINT-V35-006', '北京华盛办公网络安全部署转维保', 1006, 30017, 3, '2026-03-03', '2029-03-02', 3, '2026-03-03 10:30:00', 2, '维保已生效', 1, 'admin', '2026-03-03 10:00:00', 'admin', '2026-03-03 10:30:00', b'0', 1);

SET FOREIGN_KEY_CHECKS = 1;
