-- 179-only continuation of the reviewed P22 pending-reference structure.
-- The 178 execution record remains immutable; this migration changes no business row.
SET @p22_179_guard_sql = IF(
 (SELECT COUNT(*) FROM flyway_schema_history WHERE version='506.1' AND success=1)=1
 AND (SELECT COUNT(*) FROM flyway_schema_history WHERE version='506')=0
 AND (SELECT COUNT(*) FROM flyway_schema_history WHERE version='516.5' AND success=1)=1
 AND (SELECT COUNT(*) FROM flyway_schema_history WHERE success=0)=0
 AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE()
      AND table_name='com_delivery_scope' AND constraint_name='fk_scope_order_line' AND constraint_type='FOREIGN KEY')=1
 AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema=DATABASE()
      AND table_name='com_delivery_scope' AND constraint_name='fk_scope_order_line'
      AND referenced_table_name='com_sales_order_line' AND column_name IN ('tenant_id','order_line_id'))=2
 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='com_delivery_scope'
      AND is_nullable='NO' AND ((column_name='order_source_system' AND column_type='varchar(32)' AND collation_name='utf8mb4_0900_ai_ci')
       OR (column_name='order_company_code' AND column_type='varchar(64)' AND collation_name='utf8mb4_0900_ai_ci')
       OR (column_name='order_type' AND column_type='varchar(32)' AND collation_name='utf8mb4_0900_ai_ci')
       OR (column_name='department_code' AND column_type='varchar(64)' AND collation_name='utf8mb4_0900_ai_ci')
       OR (column_name='department_name' AND column_type='varchar(255)' AND collation_name='utf8mb4_0900_ai_ci')
       OR (column_name='department_version' AND column_type='int unsigned')))=6,
 'SELECT 1 AS p22_179_preconditions_verified','P22_179_PRECONDITIONS_NOT_MET');
PREPARE p22_179_guard FROM @p22_179_guard_sql;
EXECUTE p22_179_guard;
DEALLOCATE PREPARE p22_179_guard;

-- Same six column definitions and foreign-key change as the reviewed 178 P22 B package.
ALTER TABLE `com_delivery_scope`
  DROP FOREIGN KEY fk_scope_order_line,
  MODIFY COLUMN `order_source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单来源系统编码',
  MODIFY COLUMN `order_company_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单公司编码',
  MODIFY COLUMN `order_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单类型编码',
  MODIFY COLUMN `department_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '办事处编码',
  MODIFY COLUMN `department_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '办事处名称',
  MODIFY COLUMN `department_version` int unsigned NULL DEFAULT NULL COMMENT '办事处版本';
