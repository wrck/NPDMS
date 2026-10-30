-- 179-only forward migration; source: 25 previously reviewed and applied A statements (2026-10-21).
-- Executes only against the 179 recovery lineage; no legacy trigger definitions or task runs.
-- The 179 V506 failure and receipt are retained separately; P22's deferred foreign key is unchanged.
SET @sync_history_ok = (
 SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND
 ((version='506.1' AND checksum=-703476136 AND script='V506_1__reconcile_partial_cutover_support.sql') OR
  (version='516' AND checksum=-2064355135 AND script='V516__stage_plan_management_menu_roles.sql'))
);
SET @sync_guard_sql = IF(@sync_history_ok=2 AND ((SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='com_sales_order_line' AND index_name='uk_sales_order_line')=1 AND (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='com_sales_order_line' AND index_name IN ('idx_sales_order_line_parent','uk_sales_order_line_business'))=0 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='com_sales_order_line' AND column_name='order_id' AND is_nullable='NO')=1 AND NOT EXISTS (SELECT 1 FROM com_sales_order_line WHERE tenant_id IS NOT NULL AND source_system IS NOT NULL AND company_code IS NOT NULL AND line_type IS NOT NULL AND order_no IS NOT NULL AND line_no IS NOT NULL GROUP BY tenant_id,source_system,company_code,line_type,order_no,line_no HAVING COUNT(*)>1)),
 'SELECT 1 AS sync_179_preconditions_verified','SYNC_179_PRECONDITIONS_NOT_MET');
PREPARE sync_179_guard FROM @sync_guard_sql;
EXECUTE sync_179_guard;
DEALLOCATE PREPARE sync_179_guard;

-- A14 com_sales_order_line; reviewed SHA-256 c56191a90b8264a239fe72a42005dd4a4fb6d81c63f14a76ffdbe28a4e1346e0
ALTER TABLE `com_sales_order_line`
  ADD INDEX idx_sales_order_line_parent (tenant_id,order_id);

-- A23 com_sales_order_line; reviewed SHA-256 221b66e18576f2b3ed3fdb722517000cb7c4a2ff521d41c5e5db0b5d2b3f5cad
ALTER TABLE `com_sales_order_line`
  MODIFY COLUMN order_id BIGINT NULL COMMENT '订单ID',
  DROP INDEX uk_sales_order_line,
  ADD UNIQUE INDEX uk_sales_order_line_business (tenant_id,source_system,company_code,line_type,order_no,line_no);
