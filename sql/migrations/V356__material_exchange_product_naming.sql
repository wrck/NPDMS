-- 换货申请主子表命名对齐：plain 产品组/设备组（依据 docs/superpowers/specs/2026-09-24-material-exchange-master-detail-and-product-naming-design.md §4）
-- 主表：物料组更名产品组，编码多值加宽，名称/型号退出写入置 NULL（改可空）；设备组更名（V310 已退役 pms_equipment，设备链由 ast_device 承接）
ALTER TABLE imp_eng_material_exchange
  RENAME COLUMN material_code TO product_code;
ALTER TABLE imp_eng_material_exchange
  MODIFY COLUMN product_code varchar(500) NULL COMMENT '产品编码（订单行去重拼接）';
ALTER TABLE imp_eng_material_exchange
  RENAME COLUMN material_name TO product_name;
ALTER TABLE imp_eng_material_exchange
  MODIFY COLUMN product_name varchar(200) NULL COMMENT '产品名称';
ALTER TABLE imp_eng_material_exchange
  RENAME COLUMN specification TO product_model;
ALTER TABLE imp_eng_material_exchange
  MODIFY COLUMN product_model varchar(200) NULL COMMENT '产品型号';
ALTER TABLE imp_eng_material_exchange
  RENAME COLUMN equipment_id TO device_id;
ALTER TABLE imp_eng_material_exchange
  RENAME COLUMN new_equipment_id TO new_device_id;

-- 子表：name/equipment_id 更名，快照列 COMMENT 对齐，新增 product_id 引用（换货产品）
-- active_ref 生成列表达式引用 equipment_id，MySQL 不允许 RENAME 有生成列依赖的列（V356 首次应用失败修正）：
-- 先卸下唯一键与生成列，更名后按 device_id 表达式重建（设备组命名承接，引用键形态 V{device_id} 不变）。
ALTER TABLE imp_eng_material_exchange_serial
  DROP INDEX uk_exchange_active_ref;
ALTER TABLE imp_eng_material_exchange_serial
  DROP COLUMN active_ref;
ALTER TABLE imp_eng_material_exchange_serial
  RENAME COLUMN name TO product_name;
ALTER TABLE imp_eng_material_exchange_serial
  MODIFY COLUMN product_name varchar(255) NULL COMMENT '换货产品名称（快照）';
ALTER TABLE imp_eng_material_exchange_serial
  RENAME COLUMN equipment_id TO device_id;
ALTER TABLE imp_eng_material_exchange_serial
  MODIFY COLUMN product_code varchar(128) NULL COMMENT '换货产品编码（快照）';
ALTER TABLE imp_eng_material_exchange_serial
  MODIFY COLUMN product_model varchar(255) NULL COMMENT '换货产品型号（快照）';
ALTER TABLE imp_eng_material_exchange_serial
  ADD COLUMN product_id bigint NULL COMMENT '换货产品ID（产品信息引用）' AFTER device_id;
ALTER TABLE imp_eng_material_exchange_serial
  ADD COLUMN active_ref varchar(64) GENERATED ALWAYS AS (IF(deleted = b'0', CASE WHEN scope_detail_id IS NOT NULL THEN CONCAT('D', scope_detail_id) WHEN scope_id IS NOT NULL THEN CONCAT('S', scope_id) WHEN device_id IS NOT NULL THEN CONCAT('V', device_id) END, NULL)) STORED COMMENT '当前生效清单行引用键';
ALTER TABLE imp_eng_material_exchange_serial
  ADD UNIQUE KEY uk_exchange_active_ref (tenant_id, exchange_id, active_ref);
