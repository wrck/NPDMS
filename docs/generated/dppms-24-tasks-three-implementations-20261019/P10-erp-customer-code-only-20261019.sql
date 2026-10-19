-- 仅59284专用验证库；本文件是可审阅补丁，生成时未执行。
-- 沿用V481已批准的类型、长度、NULL及注释，不执行该版本其他DDL。
ALTER TABLE `npdms_reverify_20261014`.`cus_customer_master`
  ADD COLUMN `erp_customer_code` varchar(50) DEFAULT NULL COMMENT 'CRM ERP客户编码' AFTER `code`;
