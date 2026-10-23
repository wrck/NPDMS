-- Executed once with explicit approval on172.17.90.178:13306/npdms. Evidence only; do not replay.
ALTER TABLE `npdms`.`com_delivery_scope`
  DROP FOREIGN KEY fk_scope_order_line,
  MODIFY COLUMN `order_source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单来源系统编码',
  MODIFY COLUMN `order_company_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单公司编码',
  MODIFY COLUMN `order_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单类型编码',
  MODIFY COLUMN `department_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '办事处编码',
  MODIFY COLUMN `department_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '办事处名称',
  MODIFY COLUMN `department_version` int unsigned NULL DEFAULT NULL COMMENT '办事处版本';
