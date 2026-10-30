-- ============================================================================
-- V378: 订单行产品字段规范更名与订单/订单行 customInfo 原样同步（F-COM-001 治理续）
-- 裁决（2026-09-29）：订单行 item_code/item_desc/model_code 与规范名按对应关系为重复
-- 字段，直接更名规范化（产品/物料双读法）：
--   item_code  → product_code  （产品编码/物料编码；product_code 已存在（V161），
--                先以 COALESCE 合并其缺失值，再移除 item_code 及其索引）
--   item_desc  → product_desc  （产品描述/物料描述）
--   model_code → product_model （产品型号/物料型号）
-- product_id 为产品主档外键，语义独立，保留。
-- pm_order_data_from_erp / pm_order_line_from_erp 的 customInfo 由"仅存迁移证据"
-- 改为原样同步至 com_sales_order.custom_info / com_sales_order_line.custom_info。
-- 已迁移历史行不回填 custom_info（UPSERT/RETAIN：来源未变更不重写）；原始值仍保留在迁移证据中。
-- ============================================================================
ALTER TABLE `com_sales_order`
  ADD COLUMN `custom_info` JSON NULL COMMENT '来源customInfo原样同步' AFTER `customer_required_time`;

UPDATE `com_sales_order_line`
   SET `product_code` = COALESCE(`product_code`, `item_code`)
 WHERE `product_code` IS NULL AND `item_code` IS NOT NULL;

ALTER TABLE `com_sales_order_line` DROP INDEX `idx_sales_order_line_item`;

ALTER TABLE `com_sales_order_line`
  CHANGE COLUMN `item_desc` `product_desc` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL
    COMMENT '产品描述/物料描述（原item_desc）';

ALTER TABLE `com_sales_order_line`
  CHANGE COLUMN `model_code` `product_model` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL
    COMMENT '产品型号/物料型号（原model_code）';

ALTER TABLE `com_sales_order_line` DROP COLUMN `item_code`;

ALTER TABLE `com_sales_order_line`
  MODIFY COLUMN `product_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL
    COMMENT '产品编码/物料编码（ERP订单行）' AFTER `line_type`;

ALTER TABLE `com_sales_order_line`
  ADD COLUMN `custom_info` JSON NULL COMMENT '来源customInfo原样同步' AFTER `product_desc`;

ALTER TABLE `com_sales_order_line`
  ADD INDEX `idx_sales_order_line_product` (`tenant_id`, `product_code`);
