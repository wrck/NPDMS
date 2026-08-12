-- =============================================================================
-- V20__pms_test_data_expansion.sql
-- 目的：扩充测试数据，使每个项目(id 1001-1010)的每个关联实体表不少于 3 条记录。
-- 策略：基于 INSERT...SELECT 的"补齐到 3"模式，按项目当前记录数动态补充，
--       使每个项目在每个表最终达到 3 条（V19 已有 + V20 补充）。
-- 新数据 id 范围：2001+，避免与 V19(id 1001-1100) 冲突。
-- 幂等性：文件开头 DELETE id>=2001，保证可重复执行。
-- 审计字段统一：creator/updater='admin'，时间 '2026-07-30 10:00:00'，
--               deleted=b'0'，tenant_id=1。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. 幂等清理：删除本脚本历史数据（id>=2001）
-- -----------------------------------------------------------------------------
DELETE FROM pms_acc_acceptance              WHERE id >= 2001;
DELETE FROM pms_acc_archive_document        WHERE id >= 2001;
DELETE FROM pms_acc_completion_certificate  WHERE id >= 2001;
DELETE FROM pms_acc_deliverable_checklist   WHERE id >= 2001;
DELETE FROM pms_acc_maintenance_transition  WHERE id >= 2001;
DELETE FROM pms_acc_project_closure         WHERE id >= 2001;
DELETE FROM pms_cut_execution               WHERE id >= 2001;
DELETE FROM pms_cut_observation             WHERE id >= 2001;
DELETE FROM pms_cut_plan                    WHERE id >= 2001;
DELETE FROM pms_cut_risk                    WHERE id >= 2001;
DELETE FROM pms_cut_task                    WHERE id >= 2001;
DELETE FROM pms_eng_arrival                 WHERE id >= 2001;
DELETE FROM pms_eng_configuration           WHERE id >= 2001;
DELETE FROM pms_eng_deliverable             WHERE id >= 2001;
DELETE FROM pms_eng_installation            WHERE id >= 2001;
DELETE FROM pms_eng_issue                   WHERE id >= 2001;
DELETE FROM pms_eng_joint_test              WHERE id >= 2001;
DELETE FROM pms_eng_requirement             WHERE id >= 2001;
DELETE FROM pms_eng_resource_ready          WHERE id >= 2001;
DELETE FROM pms_eng_site_survey             WHERE id >= 2001;
DELETE FROM pms_eng_solution                WHERE id >= 2001;
DELETE FROM pms_equipment                   WHERE id >= 2001;
DELETE FROM pms_srv_execution               WHERE id >= 2001;
DELETE FROM pms_srv_issue                   WHERE id >= 2001;
DELETE FROM pms_srv_maintenance             WHERE id >= 2001;
DELETE FROM pms_srv_offline_file            WHERE id >= 2001;
DELETE FROM pms_srv_report                  WHERE id >= 2001;
DELETE FROM pms_srv_rule                    WHERE id >= 2001;
DELETE FROM pms_srv_task                    WHERE id >= 2001;

-- -----------------------------------------------------------------------------
-- 1. pms_equipment（serial_number 唯一）—— 被多表引用，先补充
-- -----------------------------------------------------------------------------
INSERT INTO pms_equipment (
    id, serial_number, name, project_id, customer_id, status, location,
    warranty_start_date, warranty_end_date, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n,
    CONCAT('SN-EQP-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充设备', n.n),
    p.id,
    1001 + MOD(p.id - 1001, 5),
    CASE n.n WHEN 1 THEN 1 WHEN 2 THEN 0 ELSE 4 END,
    CONCAT('扩充设备位置-', p.id, '-', n.n),
    '2026-01-01', '2027-12-31',
    CONCAT('扩充设备备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_equipment x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_equipment y WHERE y.serial_number = CONCAT('SN-EQP-EXP-', p.id, '-', n.n));

-- -----------------------------------------------------------------------------
-- 2. pms_cut_task / pms_srv_task（被子表通过 task_id 引用，先补充）
-- -----------------------------------------------------------------------------
INSERT INTO pms_cut_task (
    id, project_id, code, name, cutover_type, network_mode, source_type, risk_level,
    scheduled_time, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n,
    p.id,
    CONCAT('CUT-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充割接任务', n.n),
    CASE n.n WHEN 1 THEN 'REPLACE' WHEN 2 THEN 'UPGRADE' ELSE 'CONFIG' END,
    CASE n.n WHEN 1 THEN 'DUAL' WHEN 2 THEN 'SINGLE' ELSE 'CLUSTER' END,
    'MANUAL',
    CASE n.n WHEN 1 THEN 'B' WHEN 2 THEN 'C' ELSE 'A' END,
    '2026-08-15 02:00:00',
    CASE n.n WHEN 1 THEN 2 WHEN 2 THEN 1 ELSE 0 END,
    CONCAT('扩充割接任务备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_cut_task x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_cut_task y WHERE y.code = CONCAT('CUT-EXP-', p.id, '-', n.n));

INSERT INTO pms_srv_task (
    id, project_id, equipment_id, code, name, inspection_mode, source_type,
    scheduled_time, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n,
    p.id,
    (SELECT e.id FROM pms_equipment e WHERE e.project_id = p.id AND e.deleted = b'0' ORDER BY e.id LIMIT 1),
    CONCAT('INSPECT-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充巡检任务', n.n),
    CASE n.n WHEN 1 THEN 'ONLINE' WHEN 2 THEN 'OFFLINE' ELSE 'ONLINE' END,
    'MANUAL',
    '2026-08-10 09:00:00',
    CASE n.n WHEN 1 THEN 2 WHEN 2 THEN 1 ELSE 0 END,
    CONCAT('扩充巡检任务备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_srv_task x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_srv_task y WHERE y.code = CONCAT('INSPECT-EXP-', p.id, '-', n.n));

-- -----------------------------------------------------------------------------
-- 3. pms_acc_acceptance（被 pms_acc_deliverable_checklist 通过 acceptance_id 引用）
-- -----------------------------------------------------------------------------
INSERT INTO pms_acc_acceptance (
    id, project_id, code, name, acceptance_type, acceptance_date, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n,
    p.id,
    CONCAT('ACC-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充验收', n.n),
    CASE n.n WHEN 1 THEN 'PRELIMINARY' WHEN 2 THEN 'FINAL' ELSE 'PHASE' END,
    CURDATE(),
    CASE n.n WHEN 1 THEN 3 WHEN 2 THEN 5 ELSE 1 END,
    CONCAT('扩充验收备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_acc_acceptance x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_acc_acceptance y WHERE y.code = CONCAT('ACC-EXP-', p.id, '-', n.n));

-- -----------------------------------------------------------------------------
-- 4. 工程实施模块 pms_eng_*（均含 project_id）
-- -----------------------------------------------------------------------------
-- 4.1 pms_eng_site_survey
INSERT INTO pms_eng_site_survey (
    id, project_id, code, name, survey_date, location, conclusion, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n, p.id,
    CONCAT('SURVEY-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充工勘', n.n),
    CURDATE(),
    CONCAT('扩充站点-', p.id, '-', n.n),
    CONCAT('工勘结论-', n.n),
    CASE n.n WHEN 1 THEN 3 WHEN 2 THEN 2 ELSE 1 END,
    CONCAT('扩充工勘备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_eng_site_survey x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_eng_site_survey y WHERE y.code = CONCAT('SURVEY-EXP-', p.id, '-', n.n));

-- 4.2 pms_eng_requirement
INSERT INTO pms_eng_requirement (
    id, project_id, code, name, requirement_type, background, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n, p.id,
    CONCAT('REQ-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充需求', n.n),
    CASE n.n WHEN 1 THEN 'BUSINESS' WHEN 2 THEN 'TECHNICAL' ELSE 'BUSINESS' END,
    CONCAT('扩充需求背景-', n.n),
    CASE n.n WHEN 1 THEN 3 WHEN 2 THEN 2 ELSE 1 END,
    CONCAT('扩充需求备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_eng_requirement x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_eng_requirement y WHERE y.code = CONCAT('REQ-EXP-', p.id, '-', n.n));

-- 4.3 pms_eng_solution
INSERT INTO pms_eng_solution (
    id, project_id, code, name, solution_type, target, review_level, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n, p.id,
    CONCAT('SOL-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充方案', n.n),
    'IMPLEMENTATION',
    CONCAT('扩充方案目标-', n.n),
    CASE n.n WHEN 1 THEN 1 WHEN 2 THEN 2 ELSE 0 END,
    CASE n.n WHEN 1 THEN 3 WHEN 2 THEN 2 ELSE 1 END,
    CONCAT('扩充方案备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_eng_solution x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_eng_solution y WHERE y.code = CONCAT('SOL-EXP-', p.id, '-', n.n));

-- 4.4 pms_eng_arrival
INSERT INTO pms_eng_arrival (
    id, project_id, code, arrival_time, equipment_id, quantity, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n, p.id,
    CONCAT('ARR-EXP-', p.id, '-', n.n),
    '2026-07-30 10:00:00',
    (SELECT e.id FROM pms_equipment e WHERE e.project_id = p.id AND e.deleted = b'0' ORDER BY e.id LIMIT 1),
    n.n,
    CASE n.n WHEN 1 THEN 1 WHEN 2 THEN 2 ELSE 0 END,
    CONCAT('扩充到货备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_eng_arrival x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_eng_arrival y WHERE y.code = CONCAT('ARR-EXP-', p.id, '-', n.n));

-- 4.5 pms_eng_configuration（equipment_id 非空）
INSERT INTO pms_eng_configuration (
    id, project_id, code, equipment_id, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n, p.id,
    CONCAT('CONF-EXP-', p.id, '-', n.n),
    (SELECT e.id FROM pms_equipment e WHERE e.project_id = p.id AND e.deleted = b'0' ORDER BY e.id LIMIT 1),
    CASE n.n WHEN 1 THEN 2 WHEN 2 THEN 3 ELSE 1 END,
    CONCAT('扩充配置备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_eng_configuration x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_eng_configuration y WHERE y.code = CONCAT('CONF-EXP-', p.id, '-', n.n));

-- 4.6 pms_eng_deliverable（deliverable_type 非空）
INSERT INTO pms_eng_deliverable (
    id, project_id, code, name, deliverable_type, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n, p.id,
    CONCAT('DELIV-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充交付物', n.n),
    CASE n.n WHEN 1 THEN 'RECEIPT' WHEN 2 THEN 'TEST' ELSE 'CONFIG' END,
    CASE n.n WHEN 1 THEN 1 WHEN 2 THEN 2 ELSE 0 END,
    CONCAT('扩充交付物备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_eng_deliverable x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_eng_deliverable y WHERE y.code = CONCAT('DELIV-EXP-', p.id, '-', n.n));

-- 4.7 pms_eng_installation（equipment_id 非空）
INSERT INTO pms_eng_installation (
    id, project_id, code, equipment_id, install_location, install_time, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n, p.id,
    CONCAT('INST-EXP-', p.id, '-', n.n),
    (SELECT e.id FROM pms_equipment e WHERE e.project_id = p.id AND e.deleted = b'0' ORDER BY e.id LIMIT 1),
    CONCAT('扩充安装位置-', p.id, '-', n.n),
    '2026-07-30 10:00:00',
    CASE n.n WHEN 1 THEN 2 WHEN 2 THEN 3 ELSE 1 END,
    CONCAT('扩充安装备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_eng_installation x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_eng_installation y WHERE y.code = CONCAT('INST-EXP-', p.id, '-', n.n));

-- 4.8 pms_eng_issue
INSERT INTO pms_eng_issue (
    id, project_id, code, name, description, source, severity, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n, p.id,
    CONCAT('ENG-ISSUE-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充工程问题', n.n),
    CONCAT('扩充问题描述-', n.n),
    CASE n.n WHEN 1 THEN 'CONFIGURATION' WHEN 2 THEN 'INSTALLATION' ELSE 'JOINT_TEST' END,
    CASE n.n WHEN 1 THEN 2 WHEN 2 THEN 3 ELSE 1 END,
    CASE n.n WHEN 1 THEN 3 WHEN 2 THEN 1 ELSE 2 END,
    CONCAT('扩充工程问题备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_eng_issue x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_eng_issue y WHERE y.code = CONCAT('ENG-ISSUE-EXP-', p.id, '-', n.n));

-- 4.9 pms_eng_joint_test（test_case 非空）
INSERT INTO pms_eng_joint_test (
    id, project_id, code, test_case, equipment_id, test_time, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n, p.id,
    CONCAT('TEST-EXP-', p.id, '-', n.n),
    CONCAT('扩充联调测试用例-', n.n),
    (SELECT e.id FROM pms_equipment e WHERE e.project_id = p.id AND e.deleted = b'0' ORDER BY e.id LIMIT 1),
    '2026-07-30 10:00:00',
    CASE n.n WHEN 1 THEN 2 WHEN 2 THEN 3 ELSE 1 END,
    CONCAT('扩充联调备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_eng_joint_test x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_eng_joint_test y WHERE y.code = CONCAT('TEST-EXP-', p.id, '-', n.n));

-- 4.10 pms_eng_resource_ready（resource_type 非空）
INSERT INTO pms_eng_resource_ready (
    id, project_id, code, name, resource_type, equipment_id, quantity, ready_status, ready_time, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n, p.id,
    CONCAT('RES-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充资源就绪', n.n),
    CASE n.n WHEN 1 THEN 'PEOPLE' WHEN 2 THEN 'TOOL' ELSE 'EQUIPMENT' END,
    (SELECT e.id FROM pms_equipment e WHERE e.project_id = p.id AND e.deleted = b'0' ORDER BY e.id LIMIT 1),
    n.n,
    CASE n.n WHEN 1 THEN 1 WHEN 2 THEN 1 ELSE 0 END,
    '2026-07-30 10:00:00',
    CONCAT('扩充资源备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_eng_resource_ready x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_eng_resource_ready y WHERE y.code = CONCAT('RES-EXP-', p.id, '-', n.n));

-- -----------------------------------------------------------------------------
-- 5. 验收模块 pms_acc_*（均含 project_id）
-- -----------------------------------------------------------------------------
-- 5.1 pms_acc_archive_document
INSERT INTO pms_acc_archive_document (
    id, project_id, code, name, document_type, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n, p.id,
    CONCAT('DOC-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充归档文档', n.n),
    CASE n.n WHEN 1 THEN 'ACCEPTANCE' WHEN 2 THEN 'TECHNICAL' ELSE 'BUSINESS' END,
    CASE n.n WHEN 1 THEN 2 WHEN 2 THEN 3 ELSE 1 END,
    CONCAT('扩充归档备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_acc_archive_document x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_acc_archive_document y WHERE y.code = CONCAT('DOC-EXP-', p.id, '-', n.n));

-- 5.2 pms_acc_completion_certificate
INSERT INTO pms_acc_completion_certificate (
    id, project_id, code, name, certificate_no, completion_date, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n, p.id,
    CONCAT('CERT-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充竣工证书', n.n),
    CONCAT('CERT-NO-EXP-', p.id, '-', n.n),
    CURDATE(),
    CASE n.n WHEN 1 THEN 3 WHEN 2 THEN 5 ELSE 1 END,
    CONCAT('扩充竣工备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_acc_completion_certificate x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_acc_completion_certificate y WHERE y.code = CONCAT('CERT-EXP-', p.id, '-', n.n));

-- 5.3 pms_acc_deliverable_checklist（acceptance_id 可空，取项目首条验收）
INSERT INTO pms_acc_deliverable_checklist (
    id, project_id, code, name, acceptance_id, deliverable_type, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n, p.id,
    CONCAT('CHECK-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充交付物清单', n.n),
    (SELECT a.id FROM pms_acc_acceptance a WHERE a.project_id = p.id AND a.deleted = b'0' ORDER BY a.id LIMIT 1),
    'REQUIRED',
    CASE n.n WHEN 1 THEN 2 WHEN 2 THEN 3 ELSE 1 END,
    CONCAT('扩充清单备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_acc_deliverable_checklist x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_acc_deliverable_checklist y WHERE y.code = CONCAT('CHECK-EXP-', p.id, '-', n.n));

-- 5.4 pms_acc_maintenance_transition
INSERT INTO pms_acc_maintenance_transition (
    id, project_id, code, name, equipment_id, maintenance_years, start_date, end_date, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n, p.id,
    CONCAT('MAINT-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充维保移交', n.n),
    (SELECT e.id FROM pms_equipment e WHERE e.project_id = p.id AND e.deleted = b'0' ORDER BY e.id LIMIT 1),
    n.n,
    CURDATE(),
    DATE_ADD(CURDATE(), INTERVAL n.n YEAR),
    CASE n.n WHEN 1 THEN 2 WHEN 2 THEN 3 ELSE 1 END,
    CONCAT('扩充维保备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_acc_maintenance_transition x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_acc_maintenance_transition y WHERE y.code = CONCAT('MAINT-EXP-', p.id, '-', n.n));

-- 5.5 pms_acc_project_closure
INSERT INTO pms_acc_project_closure (
    id, project_id, code, name, closure_type, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n, p.id,
    CONCAT('CLOSURE-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充项目结项', n.n),
    CASE n.n WHEN 1 THEN 'NORMAL' WHEN 2 THEN 'ADVANCE' ELSE 'NORMAL' END,
    CASE n.n WHEN 1 THEN 3 WHEN 2 THEN 5 ELSE 1 END,
    CONCAT('扩充结项备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_acc_project_closure x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_acc_project_closure y WHERE y.code = CONCAT('CLOSURE-EXP-', p.id, '-', n.n));

-- -----------------------------------------------------------------------------
-- 6. 割接模块子表（task_id 关联 pms_cut_task，按项目补齐到 3）
-- -----------------------------------------------------------------------------
-- 6.1 pms_cut_execution（step_name 非空）
INSERT INTO pms_cut_execution (
    id, task_id, code, step_name, operation_time, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n,
    (SELECT t.id FROM pms_cut_task t WHERE t.project_id = p.id AND t.deleted = b'0' ORDER BY t.id LIMIT 1),
    CONCAT('CUTE-EXP-', p.id, '-', n.n),
    CONCAT('扩充割接执行步骤-', n.n),
    '2026-08-15 02:30:00',
    CASE n.n WHEN 1 THEN 2 WHEN 2 THEN 3 ELSE 1 END,
    CONCAT('扩充执行备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_cut_execution x JOIN pms_cut_task pt ON x.task_id = pt.id WHERE pt.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_cut_execution y WHERE y.code = CONCAT('CUTE-EXP-', p.id, '-', n.n));

-- 6.2 pms_cut_observation
INSERT INTO pms_cut_observation (
    id, task_id, code, observation_start, observation_end, leftover_status, conclusion, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n,
    (SELECT t.id FROM pms_cut_task t WHERE t.project_id = p.id AND t.deleted = b'0' ORDER BY t.id LIMIT 1),
    CONCAT('CUTO-EXP-', p.id, '-', n.n),
    '2026-08-15 03:00:00',
    '2026-08-15 06:00:00',
    CASE n.n WHEN 1 THEN 0 ELSE 1 END,
    CONCAT('扩充观察结论-', n.n),
    CASE n.n WHEN 1 THEN 3 WHEN 2 THEN 2 ELSE 1 END,
    CONCAT('扩充观察备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_cut_observation x JOIN pms_cut_task pt ON x.task_id = pt.id WHERE pt.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_cut_observation y WHERE y.code = CONCAT('CUTO-EXP-', p.id, '-', n.n));

-- 6.3 pms_cut_plan（name 非空）
INSERT INTO pms_cut_plan (
    id, task_id, code, name, level, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n,
    (SELECT t.id FROM pms_cut_task t WHERE t.project_id = p.id AND t.deleted = b'0' ORDER BY t.id LIMIT 1),
    CONCAT('CUTP-EXP-', p.id, '-', n.n),
    CONCAT(p.name, '-扩充割接方案', n.n),
    CASE n.n WHEN 1 THEN 'B' WHEN 2 THEN 'A' ELSE 'C' END,
    CASE n.n WHEN 1 THEN 2 WHEN 2 THEN 3 ELSE 1 END,
    CONCAT('扩充方案备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_cut_plan x JOIN pms_cut_task pt ON x.task_id = pt.id WHERE pt.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_cut_plan y WHERE y.code = CONCAT('CUTP-EXP-', p.id, '-', n.n));

-- 6.4 pms_cut_risk（name 非空）
INSERT INTO pms_cut_risk (
    id, task_id, code, name, risk_type, description, impact, mitigation, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n,
    (SELECT t.id FROM pms_cut_task t WHERE t.project_id = p.id AND t.deleted = b'0' ORDER BY t.id LIMIT 1),
    CONCAT('CUTR-EXP-', p.id, '-', n.n),
    CONCAT('扩充割接风险-', n.n),
    'RISK',
    CONCAT('扩充风险描述-', n.n),
    CONCAT('扩充风险影响-', n.n),
    CONCAT('扩充风险缓解-', n.n),
    CASE n.n WHEN 1 THEN 2 WHEN 2 THEN 3 ELSE 1 END,
    CONCAT('扩充风险备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_cut_risk x JOIN pms_cut_task pt ON x.task_id = pt.id WHERE pt.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_cut_risk y WHERE y.code = CONCAT('CUTR-EXP-', p.id, '-', n.n));

-- -----------------------------------------------------------------------------
-- 7. 服务模块子表（task_id 关联 pms_srv_task，按项目补齐到 3）
-- -----------------------------------------------------------------------------
-- 7.1 pms_srv_execution
INSERT INTO pms_srv_execution (
    id, task_id, code, rule_id, execution_time, result, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n,
    (SELECT t.id FROM pms_srv_task t WHERE t.project_id = p.id AND t.deleted = b'0' ORDER BY t.id LIMIT 1),
    CONCAT('EXE-EXP-', p.id, '-', n.n),
    (SELECT r.id FROM pms_srv_rule r WHERE r.deleted = b'0' ORDER BY r.id LIMIT 1),
    '2026-08-10 10:00:00',
    CONCAT('扩充执行结果-', n.n),
    CASE n.n WHEN 1 THEN 2 WHEN 2 THEN 3 ELSE 1 END,
    CONCAT('扩充执行备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_srv_execution x JOIN pms_srv_task pt ON x.task_id = pt.id WHERE pt.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_srv_execution y WHERE y.code = CONCAT('EXE-EXP-', p.id, '-', n.n));

-- 7.2 pms_srv_issue（severity varchar）
INSERT INTO pms_srv_issue (
    id, task_id, code, name, description, severity, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n,
    (SELECT t.id FROM pms_srv_task t WHERE t.project_id = p.id AND t.deleted = b'0' ORDER BY t.id LIMIT 1),
    CONCAT('SRV-ISSUE-EXP-', p.id, '-', n.n),
    CONCAT('扩充巡检问题-', n.n),
    CONCAT('扩充问题描述-', n.n),
    CASE n.n WHEN 1 THEN 'H' WHEN 2 THEN 'M' ELSE 'L' END,
    CASE n.n WHEN 1 THEN 3 WHEN 2 THEN 1 ELSE 2 END,
    CONCAT('扩充巡检问题备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_srv_issue x JOIN pms_srv_task pt ON x.task_id = pt.id WHERE pt.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_srv_issue y WHERE y.code = CONCAT('SRV-ISSUE-EXP-', p.id, '-', n.n));

-- 7.3 pms_srv_offline_file（file_url 非空）
INSERT INTO pms_srv_offline_file (
    id, task_id, code, file_url, parse_status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n,
    (SELECT t.id FROM pms_srv_task t WHERE t.project_id = p.id AND t.deleted = b'0' ORDER BY t.id LIMIT 1),
    CONCAT('OFFFILE-EXP-', p.id, '-', n.n),
    CONCAT('/offline/exp-', p.id, '-', n.n, '.cfg'),
    CASE n.n WHEN 1 THEN 2 WHEN 2 THEN 1 ELSE 0 END,
    CONCAT('扩充离线文件备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_srv_offline_file x JOIN pms_srv_task pt ON x.task_id = pt.id WHERE pt.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_srv_offline_file y WHERE y.code = CONCAT('OFFFILE-EXP-', p.id, '-', n.n));

-- 7.4 pms_srv_report
INSERT INTO pms_srv_report (
    id, task_id, code, report_type, content, generated_time, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n,
    (SELECT t.id FROM pms_srv_task t WHERE t.project_id = p.id AND t.deleted = b'0' ORDER BY t.id LIMIT 1),
    CONCAT('REPORT-EXP-', p.id, '-', n.n),
    CASE n.n WHEN 1 THEN 'STANDARD' WHEN 2 THEN 'SNAPSHOT' ELSE 'STANDARD' END,
    CONCAT('扩充报告内容-', n.n),
    '2026-08-10 11:00:00',
    CASE n.n WHEN 1 THEN 2 WHEN 2 THEN 3 ELSE 1 END,
    CONCAT('扩充报告备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_srv_report x JOIN pms_srv_task pt ON x.task_id = pt.id WHERE pt.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_srv_report y WHERE y.code = CONCAT('REPORT-EXP-', p.id, '-', n.n));

-- 7.5 pms_srv_maintenance（equipment_id 非空，按 project_id 统计补齐）
INSERT INTO pms_srv_maintenance (
    id, equipment_id, project_id, code, start_date, end_date, maintenance_status, service_level,
    auto_calculated, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT
    2000 + (p.id - 1001) * 3 + n.n,
    (SELECT e.id FROM pms_equipment e WHERE e.project_id = p.id AND e.deleted = b'0' ORDER BY e.id LIMIT 1),
    p.id,
    CONCAT('SRV-MAINT-EXP-', p.id, '-', n.n),
    CURDATE(),
    DATE_ADD(CURDATE(), INTERVAL n.n YEAR),
    CASE n.n WHEN 1 THEN 1 WHEN 2 THEN 2 ELSE 0 END,
    CASE n.n WHEN 1 THEN 'STANDARD' WHEN 2 THEN 'PREMIUM' ELSE 'BASIC' END,
    b'1',
    CONCAT('扩充维保备注-', n.n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM pms_project p
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE p.id BETWEEN 1001 AND 1010
    AND n.n <= (3 - (SELECT COUNT(*) FROM pms_srv_maintenance x WHERE x.project_id = p.id AND x.deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_srv_maintenance y WHERE y.code = CONCAT('SRV-MAINT-EXP-', p.id, '-', n.n));

-- -----------------------------------------------------------------------------
-- 8. pms_srv_rule（无 project_id，code 唯一；当前 3 条，补齐到 30 条）
-- -----------------------------------------------------------------------------
INSERT INTO pms_srv_rule (
    id, code, name, rule_type, rule_version, content, status, remark,
    version, creator, create_time, updater, update_time, deleted, tenant_id
)
WITH RECURSIVE nums(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM nums WHERE n < 30
)
SELECT
    2000 + n,
    CONCAT('RULE-EXP-', LPAD(n, 3, '0')),
    CONCAT('扩充巡检规则-', n),
    CASE WHEN MOD(n, 2) = 0 THEN 'OFFLINE' ELSE 'ONLINE' END,
    '1.0.0',
    CONCAT('扩充规则内容-', n),
    1,
    CONCAT('扩充规则备注-', n),
    0, 'admin', '2026-07-30 10:00:00', 'admin', '2026-07-30 10:00:00', b'0', 1
FROM nums
WHERE n <= (30 - (SELECT COUNT(*) FROM pms_srv_rule WHERE deleted = b'0'))
    AND NOT EXISTS (SELECT 1 FROM pms_srv_rule y WHERE y.code = CONCAT('RULE-EXP-', LPAD(n, 3, '0')));
