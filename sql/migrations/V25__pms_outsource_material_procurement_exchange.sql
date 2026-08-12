-- =============================================================================
-- T-V2-ENG-001 / FR-ENG-002 / FR-ENG-003
-- 工程实施域 V2：条件触发外包、OA领料、外采审批 + 物料不适配与换货协同
-- 状态机：0草稿 → 1已提交 → 2审批中 → 3已通过 / 4已驳回 / 5已撤回 / 6已终止
-- 注：长文本字段统一使用 TEXT，避免 MySQL 行大小限制。
-- =============================================================================

-- 1. 外包申请单（FR-ENG-002）
CREATE TABLE pms_eng_outsource_request (
  id bigint NOT NULL AUTO_INCREMENT,
  project_id bigint NOT NULL COMMENT '所属项目编号',
  code varchar(64) NOT NULL COMMENT '外包单号，全局唯一',
  name varchar(200) NOT NULL COMMENT '外包名称',
  outsource_type varchar(32) NOT NULL DEFAULT 'LABOR' COMMENT '外包类型 LABOR 劳务 / SERVICE 服务 / PROJECT_SUBPROJ 子项目',
  work_content text NOT NULL COMMENT '工作内容（富文本）',
  work_quantity decimal(12,2) DEFAULT NULL COMMENT '工作量',
  work_unit varchar(32) DEFAULT NULL COMMENT '工作量单位',
  planned_start_date date DEFAULT NULL COMMENT '计划开始日期',
  planned_end_date date DEFAULT NULL COMMENT '计划结束日期',
  estimated_cost decimal(14,2) DEFAULT NULL COMMENT '预估费用',
  actual_cost decimal(14,2) DEFAULT NULL COMMENT '实际费用',
  currency varchar(8) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  vendor_id bigint DEFAULT NULL COMMENT '服务商编号',
  vendor_name varchar(200) DEFAULT NULL COMMENT '服务商名称',
  contact_user_id bigint DEFAULT NULL COMMENT '联系人编号',
  contact_phone varchar(64) DEFAULT NULL COMMENT '联系电话',
  attachment_files varchar(2000) DEFAULT NULL COMMENT '附件URL列表（JSON数组）',
  trigger_source varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '触发来源 MANUAL 手动 / RESOURCE_READY 资源就绪 / SOLUTION 实施方案',
  trigger_ref_id bigint DEFAULT NULL COMMENT '触发来源业务编号',
  applicant_user_id bigint NOT NULL COMMENT '申请人编号',
  apply_time datetime NOT NULL COMMENT '申请时间',
  approver_user_id bigint DEFAULT NULL COMMENT '审批人编号',
  approve_time datetime DEFAULT NULL COMMENT '审批时间',
  approve_opinion varchar(1000) DEFAULT NULL COMMENT '审批意见',
  approve_action varchar(32) DEFAULT NULL COMMENT '审批动作 PASS/REJECT/RETURN/TRANSFER/COUNTERSIGN',
  bpm_process_instance_id varchar(64) DEFAULT NULL COMMENT 'BPM流程实例ID',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已提交 2审批中 3已通过 4已驳回 5已撤回 6已终止',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_eng_outsource_code (code),
  KEY idx_pms_eng_outsource_project (project_id),
  KEY idx_pms_eng_outsource_status (status),
  KEY idx_pms_eng_outsource_applicant (applicant_user_id),
  KEY idx_pms_eng_outsource_trigger (trigger_source, trigger_ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS 外包申请单（FR-ENG-002）';

-- 2. OA 领料申请单（FR-ENG-002）
CREATE TABLE pms_eng_material_requisition (
  id bigint NOT NULL AUTO_INCREMENT,
  project_id bigint NOT NULL COMMENT '所属项目编号',
  code varchar(64) NOT NULL COMMENT '领料单号，全局唯一',
  name varchar(200) NOT NULL COMMENT '领料名称',
  requisition_type varchar(32) NOT NULL DEFAULT 'SPARE' COMMENT '领料类型 SPARE 备件 / TOOL 工具 / CONSUMABLE 耗材',
  equipment_id bigint DEFAULT NULL COMMENT '关联设备编号',
  material_name varchar(200) NOT NULL COMMENT '物料名称',
  material_code varchar(64) DEFAULT NULL COMMENT '物料编码',
  specification varchar(200) DEFAULT NULL COMMENT '规格型号',
  quantity decimal(12,2) NOT NULL COMMENT '数量',
  unit varchar(32) NOT NULL DEFAULT '个' COMMENT '单位',
  needed_date date DEFAULT NULL COMMENT '需求日期',
  warehouse_id bigint DEFAULT NULL COMMENT '备件库编号',
  warehouse_name varchar(200) DEFAULT NULL COMMENT '备件库名称',
  stock_status varchar(32) DEFAULT NULL COMMENT '库存状态 IN_STOCK 有库存 / OUT_OF_STOCK 缺货 / RESERVED 已预留',
  attachment_files varchar(2000) DEFAULT NULL COMMENT '附件URL列表（JSON数组）',
  trigger_source varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '触发来源 MANUAL / RESOURCE_READY / SOLUTION',
  trigger_ref_id bigint DEFAULT NULL COMMENT '触发来源业务编号',
  applicant_user_id bigint NOT NULL COMMENT '申请人编号',
  apply_time datetime NOT NULL COMMENT '申请时间',
  approver_user_id bigint DEFAULT NULL COMMENT '审批人编号',
  approve_time datetime DEFAULT NULL COMMENT '审批时间',
  approve_opinion varchar(1000) DEFAULT NULL COMMENT '审批意见',
  approve_action varchar(32) DEFAULT NULL COMMENT '审批动作 PASS/REJECT/RETURN/TRANSFER/COUNTERSIGN',
  bpm_process_instance_id varchar(64) DEFAULT NULL COMMENT 'BPM流程实例ID',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已提交 2审批中 3已通过 4已驳回 5已撤回 6已终止',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_eng_material_req_code (code),
  KEY idx_pms_eng_material_req_project (project_id),
  KEY idx_pms_eng_material_req_status (status),
  KEY idx_pms_eng_material_req_applicant (applicant_user_id),
  KEY idx_pms_eng_material_req_equipment (equipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS OA领料申请单（FR-ENG-002）';

-- 3. 外采申请单（FR-ENG-002）
CREATE TABLE pms_eng_external_procurement (
  id bigint NOT NULL AUTO_INCREMENT,
  project_id bigint NOT NULL COMMENT '所属项目编号',
  code varchar(64) NOT NULL COMMENT '外采单号，全局唯一',
  name varchar(200) NOT NULL COMMENT '外采名称',
  procurement_type varchar(32) NOT NULL DEFAULT 'GOODS' COMMENT '外采类型 GOODS 物资 / SERVICE 服务',
  material_name varchar(200) NOT NULL COMMENT '物料/服务名称',
  material_code varchar(64) DEFAULT NULL COMMENT '物料编码',
  specification varchar(200) DEFAULT NULL COMMENT '规格型号',
  brand varchar(100) DEFAULT NULL COMMENT '品牌',
  model varchar(100) DEFAULT NULL COMMENT '型号',
  quantity decimal(12,2) NOT NULL COMMENT '数量',
  unit varchar(32) NOT NULL DEFAULT '个' COMMENT '单位',
  unit_price decimal(14,2) DEFAULT NULL COMMENT '单价',
  total_price decimal(14,2) DEFAULT NULL COMMENT '总价',
  currency varchar(8) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  supplier_name varchar(200) DEFAULT NULL COMMENT '供应商名称',
  supplier_contact varchar(100) DEFAULT NULL COMMENT '供应商联系人',
  supplier_phone varchar(64) DEFAULT NULL COMMENT '供应商电话',
  needed_date date DEFAULT NULL COMMENT '需求日期',
  expected_delivery_date date DEFAULT NULL COMMENT '预计到货日期',
  attachment_files varchar(2000) DEFAULT NULL COMMENT '附件URL列表（JSON数组）',
  trigger_source varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '触发来源 MANUAL / RESOURCE_READY / SOLUTION',
  trigger_ref_id bigint DEFAULT NULL COMMENT '触发来源业务编号',
  applicant_user_id bigint NOT NULL COMMENT '申请人编号',
  apply_time datetime NOT NULL COMMENT '申请时间',
  approver_user_id bigint DEFAULT NULL COMMENT '审批人编号',
  approve_time datetime DEFAULT NULL COMMENT '审批时间',
  approve_opinion varchar(1000) DEFAULT NULL COMMENT '审批意见',
  approve_action varchar(32) DEFAULT NULL COMMENT '审批动作 PASS/REJECT/RETURN/TRANSFER/COUNTERSIGN',
  bpm_process_instance_id varchar(64) DEFAULT NULL COMMENT 'BPM流程实例ID',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已提交 2审批中 3已通过 4已驳回 5已撤回 6已终止',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_eng_ext_proc_code (code),
  KEY idx_pms_eng_ext_proc_project (project_id),
  KEY idx_pms_eng_ext_proc_status (status),
  KEY idx_pms_eng_ext_proc_applicant (applicant_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS 外采申请单（FR-ENG-002）';

-- 4. 物料不适配与换货协同单（FR-ENG-003）
CREATE TABLE pms_eng_material_exchange (
  id bigint NOT NULL AUTO_INCREMENT,
  project_id bigint NOT NULL COMMENT '所属项目编号',
  code varchar(64) NOT NULL COMMENT '换货单号，全局唯一',
  name varchar(200) NOT NULL COMMENT '换货名称',
  exchange_type varchar(32) NOT NULL DEFAULT 'INCOMPATIBLE' COMMENT '换货类型 INCOMPATIBLE 物料不适配 / DAMAGE 到货损坏 / WRONG 发货错误 / OTHER 其他',
  equipment_id bigint DEFAULT NULL COMMENT '受影响设备编号',
  material_name varchar(200) NOT NULL COMMENT '物料名称',
  material_code varchar(64) DEFAULT NULL COMMENT '物料编码',
  specification varchar(200) DEFAULT NULL COMMENT '规格型号',
  quantity decimal(12,2) NOT NULL COMMENT '换货数量',
  unit varchar(32) NOT NULL DEFAULT '个' COMMENT '单位',
  original_order_no varchar(64) DEFAULT NULL COMMENT '原订单号',
  reason text NOT NULL COMMENT '换货原因（富文本）',
  reason_files varchar(2000) DEFAULT NULL COMMENT '原因附件URL列表（JSON数组）',
  crm_push_status varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'CRM推送状态 PENDING 待推送 / SENT 已推送 / RECEIVED 已接收 / CLOSED 已关闭',
  crm_push_time datetime DEFAULT NULL COMMENT 'CRM推送时间',
  crm_order_no varchar(64) DEFAULT NULL COMMENT '新订单号（CRM回填）',
  new_equipment_id bigint DEFAULT NULL COMMENT '换货后新设备编号',
  exchange_progress text DEFAULT NULL COMMENT '换货进展记录（JSON数组）',
  applicant_user_id bigint NOT NULL COMMENT '申请人编号',
  apply_time datetime NOT NULL COMMENT '申请时间',
  approver_user_id bigint DEFAULT NULL COMMENT '审批人编号',
  approve_time datetime DEFAULT NULL COMMENT '审批时间',
  approve_opinion varchar(1000) DEFAULT NULL COMMENT '审批意见',
  approve_action varchar(32) DEFAULT NULL COMMENT '审批动作 PASS/REJECT/RETURN/TRANSFER/COUNTERSIGN',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已提交 2审批中 3已通过 4已驳回 5已撤回 6已终止',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_eng_material_exch_code (code),
  KEY idx_pms_eng_material_exch_project (project_id),
  KEY idx_pms_eng_material_exch_status (status),
  KEY idx_pms_eng_material_exch_equipment (equipment_id),
  KEY idx_pms_eng_material_exch_crm (crm_push_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS 物料不适配与换货协同（FR-ENG-003）';

-- =============================================================================
-- 菜单：外包/领料/外采/换货（父菜单 18000）
-- ID 19171~19200，避免与 V23（19151~19162）冲突
-- =============================================================================
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- ========== 外包申请单菜单（sort 71）==========
(19171, '外包申请', 'pms:eng-outsource:query', 2, 71, 18000, 'eng-outsource', 'ep:office-building',
 'pms/engineering/outsource/index', 'PmsEngOutsource', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19172, '外包创建', 'pms:eng-outsource:create', 3, 1, 19171, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19173, '外包修改', 'pms:eng-outsource:update', 3, 2, 19171, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19174, '外包删除', 'pms:eng-outsource:delete', 3, 3, 19171, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19175, '外包提交', 'pms:eng-outsource:submit', 3, 4, 19171, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19176, '外包审批', 'pms:eng-outsource:audit', 3, 5, 19171, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== OA领料申请单菜单（sort 72）==========
(19177, 'OA领料', 'pms:eng-material-req:query', 2, 72, 18000, 'eng-material-req', 'ep:shopping-cart',
 'pms/engineering/material-req/index', 'PmsEngMaterialReq', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19178, '领料创建', 'pms:eng-material-req:create', 3, 1, 19177, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19179, '领料修改', 'pms:eng-material-req:update', 3, 2, 19177, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19180, '领料删除', 'pms:eng-material-req:delete', 3, 3, 19177, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19181, '领料提交', 'pms:eng-material-req:submit', 3, 4, 19177, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19182, '领料审批', 'pms:eng-material-req:audit', 3, 5, 19177, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 外采申请单菜单（sort 73）==========
(19183, '外采申请', 'pms:eng-ext-proc:query', 2, 73, 18000, 'eng-ext-proc', 'ep:goods',
 'pms/engineering/ext-proc/index', 'PmsEngExtProc', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19184, '外采创建', 'pms:eng-ext-proc:create', 3, 1, 19183, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19185, '外采修改', 'pms:eng-ext-proc:update', 3, 2, 19183, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19186, '外采删除', 'pms:eng-ext-proc:delete', 3, 3, 19183, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19187, '外采提交', 'pms:eng-ext-proc:submit', 3, 4, 19183, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19188, '外采审批', 'pms:eng-ext-proc:audit', 3, 5, 19183, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 物料换货协同菜单（sort 74）==========
(19189, '换货协同', 'pms:eng-material-exch:query', 2, 74, 18000, 'eng-material-exch', 'ep:refresh',
 'pms/engineering/material-exch/index', 'PmsEngMaterialExch', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19190, '换货创建', 'pms:eng-material-exch:create', 3, 1, 19189, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19191, '换货修改', 'pms:eng-material-exch:update', 3, 2, 19189, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19192, '换货删除', 'pms:eng-material-exch:delete', 3, 3, 19189, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19193, '换货提交', 'pms:eng-material-exch:submit', 3, 4, 19189, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19194, '换货审批', 'pms:eng-material-exch:audit', 3, 5, 19189, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19195, '换货CRM推送', 'pms:eng-material-exch:push-crm', 3, 6, 19189, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
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
  AND m.id BETWEEN 19171 AND 19195;
