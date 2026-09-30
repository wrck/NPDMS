-- V381(原 theirs V359): 发货事件保留来源订单引用
-- 设备序列号同步的发货事件不再解析内部订单行ID，直接以来源订单号与行号落列；
-- 后续与销售订单行的对应关系按业务编号匹配，不做强制外键解析。
ALTER TABLE ast_device_shipment
    ADD COLUMN order_no VARCHAR(64) NULL COMMENT '来源订单号',
    ADD COLUMN line_no VARCHAR(64) NULL COMMENT '来源订单行号';
