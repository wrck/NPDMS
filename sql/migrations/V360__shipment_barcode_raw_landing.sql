-- V360: 发货事件与设备关系全字段承接来源业务字段
-- 承接表已确定为 ast_device_shipment / ast_device_relationship；本迁移补齐来源表
-- (fb_shipment_barcode / fb_shipment_barcode_relation) 此前未承接的业务字段，保证不遗漏。
-- 同名语义沿用既有列命名：item→product_code、item2→secondary_item、barcode2→secondary_sn、
-- com_barcode→internal_serial_no；无既定语义的按来源语义取 plain 命名。
ALTER TABLE ast_device_shipment
    ADD COLUMN product_code VARCHAR(64) NULL COMMENT '来源物料编码 item',
    ADD COLUMN secondary_sn VARCHAR(100) NULL COMMENT '来源附加SN barcode2',
    ADD COLUMN secondary_item VARCHAR(64) NULL COMMENT '来源附加物料 item2',
    ADD COLUMN internal_serial_no VARCHAR(128) NULL COMMENT '来源组合条码 com_barcode',
    ADD COLUMN is_rma INT NULL COMMENT '来源RMA标识 isRMA',
    ADD COLUMN profit_center VARCHAR(32) NULL COMMENT '来源利润中心 profitCenter',
    ADD COLUMN sole_agent_suffix VARCHAR(32) NULL COMMENT '来源独家代理后缀 soleAgentSuffix',
    ADD COLUMN sync_time DATETIME NULL COMMENT '来源同步时间 syncTime',
    ADD COLUMN uuid VARCHAR(64) NULL COMMENT '来源UUID';
ALTER TABLE ast_device_relationship
    ADD COLUMN source_item VARCHAR(15) NULL COMMENT '来源主SN物料 item1',
    ADD COLUMN target_item VARCHAR(15) NULL COMMENT '来源目标SN物料 item2';
