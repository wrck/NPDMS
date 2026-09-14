-- 2026-09-14 专项迁移：来源缺失单位时保留 NULL，数量保持 PENDING_AUTHORITY。
ALTER TABLE com_sales_order_line MODIFY COLUMN unit_code varchar(32) NULL;
ALTER TABLE com_sales_order_line ADD CONSTRAINT chk_sales_order_line_known_unit
    CHECK (quantity_status = 'PENDING_AUTHORITY' OR unit_code IS NOT NULL);
