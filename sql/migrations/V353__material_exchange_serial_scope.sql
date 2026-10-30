-- 换货申请设备行结构改造：设备清单行 = 合同对应销售订单行在项目内的交付范围分配。
-- 新行写入清单行引用（scope_detail_id / scope_id 二选一）与订单行快照；旧序列号快照字段保留（可空），新行不再写入。
-- active_ref 取代旧设备唯一键，使同一换货申请可同时容纳旧序列号行（V{equipment_id}）与清单行（D{scopeDetailId} / S{scopeId}）。
ALTER TABLE imp_eng_material_exchange_serial
    ADD COLUMN scope_detail_id bigint DEFAULT NULL COMMENT '设备清单明细拆分行稳定ID（与scope_id二选一）' AFTER quantity,
    ADD COLUMN scope_id bigint DEFAULT NULL COMMENT '未拆分交付范围行稳定ID（与scope_detail_id二选一）' AFTER scope_detail_id,
    ADD COLUMN order_no varchar(64) NOT NULL DEFAULT '' COMMENT '销售订单号快照' AFTER scope_id,
    ADD COLUMN line_no varchar(32) NOT NULL DEFAULT '' COMMENT '销售订单行号快照' AFTER order_no,
    ADD COLUMN item_code varchar(64) NOT NULL DEFAULT '' COMMENT '物料编码快照' AFTER line_no,
    ADD COLUMN device_type_code varchar(64) DEFAULT NULL COMMENT '设备类型编码快照' AFTER item_code,
    ADD COLUMN device_type_name varchar(64) DEFAULT NULL COMMENT '设备类型名称快照' AFTER device_type_code,
    MODIFY COLUMN equipment_id bigint DEFAULT NULL COMMENT '设备稳定ID（旧序列号快照，新行不写入）',
    MODIFY COLUMN sn varchar(128) DEFAULT NULL COMMENT '设备序列号（旧序列号快照，新行不写入）',
    DROP COLUMN active_equipment_id,
    DROP INDEX uk_exchange_active_device,
    ADD COLUMN active_ref varchar(64) GENERATED ALWAYS AS (IF(deleted = b'0', CASE WHEN scope_detail_id IS NOT NULL THEN CONCAT('D', scope_detail_id) WHEN scope_id IS NOT NULL THEN CONCAT('S', scope_id) WHEN equipment_id IS NOT NULL THEN CONCAT('V', equipment_id) END, NULL)) STORED COMMENT '当前生效清单行引用键',
    ADD UNIQUE KEY uk_exchange_active_ref (tenant_id, exchange_id, active_ref);
