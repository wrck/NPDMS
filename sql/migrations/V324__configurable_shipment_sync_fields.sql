-- 专项数据同步：通用引擎目标字段。来源映射；不执行真实同步。
-- 合同未知来源状态必须保持未知，不以默认 ACTIVE 代替权威事实。
ALTER TABLE com_contract
    MODIFY COLUMN source_lifecycle_status VARCHAR(32) NULL DEFAULT NULL,
    MODIFY COLUMN status VARCHAR(32) NULL DEFAULT NULL;
ALTER TABLE ast_device
    MODIFY COLUMN package_no VARCHAR(128) NULL,
    ADD COLUMN internal_serial_no VARCHAR(128) NULL COMMENT '内部序列号',
    ADD COLUMN secondary_sn VARCHAR(100) NULL COMMENT '最新发货附加SN缓存',
    ADD COLUMN secondary_item VARCHAR(64) NULL COMMENT '最新发货附加物料缓存';
ALTER TABLE ast_device_shipment
    MODIFY COLUMN package_no VARCHAR(128) NULL,
    ADD COLUMN legacy_package_key VARCHAR(128) NULL COMMENT '来源装箱单键，未解析时仍保留',
    ADD COLUMN rma_related_sn VARCHAR(100) NULL COMMENT '来源RMA关联SN候选，不生成替换关系',
    ADD COLUMN rma_marked TINYINT NOT NULL DEFAULT 0 COMMENT '来源RMA标识',
    ADD COLUMN order_line_id BIGINT NULL COMMENT '唯一解析的ERP订单行ID',
    ADD COLUMN source_payload JSON NULL COMMENT '来源条码记录';
