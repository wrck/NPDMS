-- CRM view columns verified from JDBC metadata on 2026-10-15.
ALTER TABLE `cus_customer_master`
  ADD COLUMN `erp_customer_code` varchar(50) DEFAULT NULL COMMENT 'CRM ERP客户编码' AFTER `code`;

ALTER TABLE `ast_product_official_info`
  MODIFY COLUMN `product_code` varchar(100) NOT NULL COMMENT '产品编码（CRM itemCode）',
  MODIFY COLUMN `product_name` varchar(500) DEFAULT NULL COMMENT '产品名称（CRM itemDesc）',
  MODIFY COLUMN `technical_spec` longtext DEFAULT NULL COMMENT '技术规格（CRM remark）',
  ADD COLUMN `crm_product_code` varchar(100) DEFAULT NULL COMMENT 'CRM productCode',
  ADD COLUMN `product_sub_code` varchar(100) DEFAULT NULL COMMENT 'CRM productSubCode',
  ADD COLUMN `list_price` decimal(23,10) DEFAULT NULL COMMENT 'CRM listPrice',
  ADD COLUMN `crm_status` int DEFAULT NULL COMMENT 'CRM status',
  ADD COLUMN `business_unit` varchar(100) DEFAULT NULL COMMENT 'CRM BU',
  ADD COLUMN `product_line` varchar(100) DEFAULT NULL COMMENT 'CRM productLine',
  ADD COLUMN `org_id` int DEFAULT NULL COMMENT 'CRM orgId',
  ADD COLUMN `source_created_at` datetime(3) DEFAULT NULL COMMENT 'CRM createTime',
  ADD COLUMN `crm_state_code` int DEFAULT NULL COMMENT 'CRM statecode',
  ADD COLUMN `crm_status_code` int DEFAULT NULL COMMENT 'CRM statuscode',
  ADD COLUMN `product_stage` int DEFAULT NULL COMMENT 'CRM productStage',
  ADD COLUMN `end_of_sale_date` datetime(3) DEFAULT NULL COMMENT 'CRM endOfSaleDate',
  ADD COLUMN `end_of_support_date` datetime(3) DEFAULT NULL COMMENT 'CRM endOfSupportDate',
  ADD COLUMN `end_of_life_date` datetime(3) DEFAULT NULL COMMENT 'CRM endOfLifeDate',
  ADD COLUMN `last_renewal_date` datetime(3) DEFAULT NULL COMMENT 'CRM lastRenewalDate';
