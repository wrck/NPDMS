ALTER TABLE `cus_customer_master`
  ADD COLUMN `customer_level` varchar(32) DEFAULT NULL COMMENT '客户级别字典值' AFTER `short_name`;
