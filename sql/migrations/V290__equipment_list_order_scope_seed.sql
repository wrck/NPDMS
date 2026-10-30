-- V290: 设备清单数据源切换配套种子
-- 背景：项目详情"设备清单"语义为订单信息（PRD：ERP销售订单同步补全项目商务信息之设备清单），
--       数据源为 com_delivery_scope(+detail 组合维度明细)；此前误复用序列号档案页展示。
-- 序列号信息由 ast_device(F-AST-001) 承载（见 V285），两条链路分别展示。
-- 幂等：显式高段主键 + ON DUPLICATE KEY UPDATE；creator 统一 pms_equipment_seed。

-- 1. 销售订单主档（tenant_id=1：前端会话租户可见；既有 fcom001 种子为 tenant 0 验收夹具，不混用）
INSERT INTO `com_sales_order`
(`id`, `tenant_id`, `source_system`, `source_record_key`, `source_version`,
 `company_code`, `company_name`, `order_type`, `order_no`,
 `authority_status`, `source_lifecycle_status`, `status`, `version`, `creator`, `updater`, `deleted`)
VALUES
(993109105001, 1, 'SEED', 'seed:equipment-order-demo', '1',
 'DPTECH-DEMO', '迪普科技示例公司', 'SEED', 'SO-EQUIP-DEMO',
 'CONFIRMED', 'ACTIVE', 'ENABLED', 0, 'pms_equipment_seed', 'pms_equipment_seed', 0)
ON DUPLICATE KEY UPDATE `company_name`=VALUES(`company_name`), `updater`='pms_equipment_seed',
 `update_time`=NOW(3), `deleted`=0;

-- 2. 订单行
INSERT INTO `com_sales_order_line`
(`id`, `tenant_id`, `order_id`, `source_system`, `source_record_key`, `source_version`,
 `company_code`, `company_name`, `order_type`, `order_no`, `line_no`, `item_code`, `item_desc`,
 `product_code`, `order_qty`, `open_qty`, `delivered_qty`, `unit_code`, `unit_scale`,
 `quantity_status`, `source_lifecycle_status`, `status`, `version`, `creator`, `updater`, `deleted`)
VALUES
(993109110001, 1, 993109105001, 'SEED', 'seed:equipment-line-1010', '1',
 'DPTECH-DEMO', '迪普科技示例公司', 'SEED', 'SO-EQUIP-DEMO', 'LINE-EQUIP-1010',
 'ITEM-SEC-DEPLOY', '成都智慧城市安全部署项目设备清单', 'AR6280', 7, 7, 0, 'SET', 1,
 'CONFIRMED', 'ACTIVE', 'ENABLED', 0, 'pms_equipment_seed', 'pms_equipment_seed', 0),
(993109110002, 1, 993109105001, 'SEED', 'seed:equipment-line-1009', '1',
 'DPTECH-DEMO', '迪普科技示例公司', 'SEED', 'SO-EQUIP-DEMO', 'LINE-EQUIP-1009',
 'ITEM-FIN-DC', '深圳金融数据中台迁移升级项目设备清单', 'USG6530E', 2, 2, 0, 'SET', 1,
 'CONFIRMED', 'ACTIVE', 'ENABLED', 0, 'pms_equipment_seed', 'pms_equipment_seed', 0)
ON DUPLICATE KEY UPDATE `item_desc`=VALUES(`item_desc`), `updater`='pms_equipment_seed',
 `update_time`=NOW(3), `deleted`=0;

-- 2. 交付范围（订单行 → 项目 的数量分配；project_code/project_name 取自项目主数据）
INSERT INTO `com_delivery_scope`
(`id`, `tenant_id`, `project_id`, `project_code`, `project_name`, `order_line_id`,
 `order_source_system`, `order_company_code`, `order_company_name`, `order_type`, `order_no`,
 `line_no`, `item_code`, `item_desc`, `allocated_qty`, `scope_status`, `allocation_version`,
 `allocation_source`, `change_reason`, `office_department_id`, `office_department_code`,
 `office_department_name`, `office_department_version`, `source_evidence`, `effective_from`,
 `status`, `version`, `creator`, `updater`, `deleted`)
SELECT 993109120001, 1, p.`id`, p.`project_code`, p.`project_name`, 993109110001,
 'SEED', 'DPTECH-DEMO', '迪普科技示例公司', 'SEED', 'SO-EQUIP-DEMO',
 'LINE-EQUIP-1010', 'ITEM-SEC-DEPLOY', '成都智慧城市安全部署项目设备清单', 7, 'ACTIVE', 1,
 'SEED', '设备清单数据源切换演示种子', 930851, 'OFFICE-HZ-DEMO', '杭州示例办事处', 0,
 'SEED-EQUIP-LIST-1010', NOW(3), 'ENABLED', 0, 'pms_equipment_seed', 'pms_equipment_seed', 0
FROM `proj_project` p WHERE p.`id` = 1010 AND p.`deleted` = b'0'
ON DUPLICATE KEY UPDATE `item_desc`=VALUES(`item_desc`), `updater`='pms_equipment_seed',
 `update_time`=NOW(3), `deleted`=0;

INSERT INTO `com_delivery_scope`
(`id`, `tenant_id`, `project_id`, `project_code`, `project_name`, `order_line_id`,
 `order_source_system`, `order_company_code`, `order_company_name`, `order_type`, `order_no`,
 `line_no`, `item_code`, `item_desc`, `allocated_qty`, `scope_status`, `allocation_version`,
 `allocation_source`, `change_reason`, `office_department_id`, `office_department_code`,
 `office_department_name`, `office_department_version`, `source_evidence`, `effective_from`,
 `status`, `version`, `creator`, `updater`, `deleted`)
SELECT 993109120002, 1, p.`id`, p.`project_code`, p.`project_name`, 993109110002,
 'SEED', 'DPTECH-DEMO', '迪普科技示例公司', 'SEED', 'SO-EQUIP-DEMO',
 'LINE-EQUIP-1009', 'ITEM-FIN-DC', '深圳金融数据中台迁移升级项目设备清单', 2, 'ACTIVE', 1,
 'SEED', '设备清单数据源切换演示种子', 930851, 'OFFICE-HZ-DEMO', '杭州示例办事处', 0,
 'SEED-EQUIP-LIST-1009', NOW(3), 'ENABLED', 0, 'pms_equipment_seed', 'pms_equipment_seed', 0
FROM `proj_project` p WHERE p.`id` = 1009 AND p.`deleted` = b'0'
ON DUPLICATE KEY UPDATE `item_desc`=VALUES(`item_desc`), `updater`='pms_equipment_seed',
 `update_time`=NOW(3), `deleted`=0;

-- 3. 组合维度明细（覆盖精确序列号命中 / 产品维度 / 设备类型兜底三场景；数量与主表分配一致）
INSERT INTO `com_delivery_scope_detail`
(`id`, `tenant_id`, `delivery_scope_id`, `detail_sequence`, `serial_no`, `product_code`,
 `product_name`, `device_type_code`, `device_type_name`, `allocated_qty`, `detail_status`,
 `source_record_key`, `source_snapshot`, `version`, `creator`, `updater`, `deleted`)
VALUES
(993109130001, 1, 993109120001, 1, 'SN-EQP-2026-010', 'AR6280', '华为AR6280路由器',
 NULL, NULL, 1, 'ACTIVE', 'seed:equipment-detail-1010-1',
 JSON_OBJECT('scenario', 'EQUIP_LIST_SERIAL_HIT'), 0, 'pms_equipment_seed', 'pms_equipment_seed', 0),
(993109130002, 1, 993109120001, 2, NULL, 'S5735-L48P4X', '48口千兆接入交换机',
 NULL, NULL, 2, 'ACTIVE', 'seed:equipment-detail-1010-2',
 JSON_OBJECT('scenario', 'EQUIP_LIST_PRODUCT'), 0, 'pms_equipment_seed', 'pms_equipment_seed', 0),
(993109130003, 1, 993109120001, 3, NULL, NULL, NULL,
 'WLAN-AP', '无线接入点', 4, 'ACTIVE', 'seed:equipment-detail-1010-3',
 JSON_OBJECT('scenario', 'EQUIP_LIST_DEVICE_TYPE'), 0, 'pms_equipment_seed', 'pms_equipment_seed', 0),
(993109130004, 1, 993109120002, 1, 'SN-EQP-EXP-1009-1', 'USG6530E', '防火墙',
 NULL, NULL, 1, 'ACTIVE', 'seed:equipment-detail-1009-1',
 JSON_OBJECT('scenario', 'EQUIP_LIST_SERIAL_HIT'), 0, 'pms_equipment_seed', 'pms_equipment_seed', 0),
(993109130005, 1, 993109120002, 2, 'SN-EQP-EXP-1009-2', 'USG6530E', '防火墙',
 NULL, NULL, 1, 'ACTIVE', 'seed:equipment-detail-1009-2',
 JSON_OBJECT('scenario', 'EQUIP_LIST_SERIAL_HIT'), 0, 'pms_equipment_seed', 'pms_equipment_seed', 0)
ON DUPLICATE KEY UPDATE `allocated_qty`=VALUES(`allocated_qty`), `detail_status`='ACTIVE',
 `updater`='pms_equipment_seed', `update_time`=NOW(3), `deleted`=0;
