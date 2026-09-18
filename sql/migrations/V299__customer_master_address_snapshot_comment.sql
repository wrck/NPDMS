-- 客户主档 address 定位对齐：保存地点引用时由 Address/Site 引用链组装文本快照，
-- 无引用的存量客户承接 pms_customer.address 遗留文本（V298 改名延续）。
ALTER TABLE `cus_customer_master`
    MODIFY COLUMN `address` varchar(255) DEFAULT NULL
    COMMENT '客户地址快照（保存Address/Site地点引用时组装；无引用时为pms_customer.address遗留文本）';
