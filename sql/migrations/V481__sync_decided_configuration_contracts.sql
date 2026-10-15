-- CRM view columns verified from JDBC metadata on 2026-10-15.
ALTER TABLE `cus_customer_master`
  ADD COLUMN `erp_customer_code` varchar(50) DEFAULT NULL COMMENT 'CRM ERP客户编码' AFTER `code`;

ALTER TABLE `ast_product_official_info`
  MODIFY COLUMN `product_code` varchar(100) NOT NULL COMMENT '产品编码（CRM itemCode）',
  MODIFY COLUMN `product_name` varchar(500) DEFAULT NULL COMMENT '产品名称（CRM itemDesc）',
  MODIFY COLUMN `technical_spec` longtext DEFAULT NULL COMMENT '技术规格（CRM remark）',
  ADD COLUMN `product_category` varchar(100) DEFAULT NULL COMMENT 'CRM productCode',
  ADD COLUMN `product_type` varchar(100) DEFAULT NULL COMMENT 'CRM productSubCode',
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

-- Existing duplicates must be adjudicated, never silently discarded to create the index.
CREATE UNIQUE INDEX uk_product_official_tenant_code ON ast_product_official_info (tenant_id,product_code);
ALTER TABLE com_sales_order_line MODIFY COLUMN order_id BIGINT NULL,
  DROP INDEX uk_sales_order_line,
  ADD UNIQUE INDEX uk_sales_order_line_business (tenant_id,source_system,company_code,order_type,order_no,line_no);
ALTER TABLE com_shipment_package ADD COLUMN shipment_contract_source_key VARCHAR(128) NULL COMMENT '原fb_shipment.con_id，保留精确父来源身份';

-- 展示缓存改造的结构前置；应用部署与执行仍遵循环境迁移授权。
-- 两列保留为可空兼容字段，新缓存不填充、不关联；不改变项目主表。
ALTER TABLE proj_project_lease_product MODIFY COLUMN project_id BIGINT NULL;
ALTER TABLE proj_project_product_configuration MODIFY COLUMN project_id BIGINT NULL;
-- P23原创建/更新时间允许NULL；缓存原样保留缺失值，不补当前时间。
ALTER TABLE com_contract_collection_plan
  MODIFY COLUMN create_time DATETIME(3) NULL,
  MODIFY COLUMN update_time DATETIME(3) NULL;
-- 按来源projectCode已知宽度提供普通查询索引，不添加业务唯一约束。
-- 租赁来源varchar(255)，配置来源varchar(100)；目标仍保留TEXT原列，避免截断旧值。
CREATE INDEX idx_lease_cache_project ON proj_project_lease_product (tenant_id,project_code(255),deleted);
CREATE INDEX idx_configuration_cache_project ON proj_project_product_configuration (tenant_id,project_code(100),deleted);
CREATE INDEX idx_collection_plan_cache_contract ON com_contract_collection_plan (tenant_id,contract_no,deleted);

-- Whole-source P34 preserves the original five fields, including an absent serial number.
ALTER TABLE ast_device_factory_version_record MODIFY COLUMN device_sn VARCHAR(100) NULL;
