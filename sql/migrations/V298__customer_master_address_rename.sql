-- V298: cus_customer_master 地址字段正名（F-CUS-001 主档属性承接）
-- V106 前向迁移把 pms_customer.address 承接为 legacy_address_snapshot（迁移期限定名）。
-- 该列承载的是客户地址现役属性数据（新域地址关系见 cus_customer_location），限定词无实质语义，
-- 按客户主档字段规范正名为 address；数据不变，仅列名与注释调整。

ALTER TABLE `cus_customer_master`
  RENAME COLUMN `legacy_address_snapshot` TO `address`;

ALTER TABLE `cus_customer_master`
  MODIFY COLUMN `address` varchar(255) DEFAULT NULL COMMENT '客户地址（承接 pms_customer.address）';
