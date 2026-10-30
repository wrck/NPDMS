-- 179-only forward migration; source: 25 previously reviewed and applied A statements (2026-10-21).
-- Executes only against the 179 recovery lineage; no legacy trigger definitions or task runs.
-- The 179 V506 failure and receipt are retained separately; P22's deferred foreign key is unchanged.
SET @sync_history_ok = (
 SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND
 ((version='506.1' AND checksum=-703476136 AND script='V506_1__reconcile_partial_cutover_support.sql') OR
  (version='516' AND checksum=-2064355135 AND script='V516__stage_plan_management_menu_roles.sql'))
);
SET @sync_guard_sql = IF(@sync_history_ok=2 AND ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='system_users' AND column_name IN ('employee_no', 'custom_info')) OR (table_name='ast_product_official_info' AND column_name IN ('product_category', 'product_type')) OR (table_name='com_shipment_contract_reference' AND column_name IN ('warranty_years')) OR (table_name='com_shipment_package' AND column_name IN ('carrier_code', 'shipment_contract_source_key')) OR (table_name='proj_project' AND column_name IN ('custom_info', 'order_create_time', 'disabled', 'effective_from', 'effective_to')) OR (table_name='com_delivery_scope' AND column_name IN ('allocation_confirmed', 'delivered_quantity', 'open_quantity')) OR (table_name='proj_project_member_assignment' AND column_name IN ('member_code', 'phone_number', 'email', 'registration_source'))))=0 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='cus_customer_master' AND column_name='name' AND column_type='varchar(128)' AND is_nullable='NO') OR (table_name='imp_eng_installation' AND column_name='install_location' AND column_type='varchar(255)' AND is_nullable='YES') OR (table_name='proj_project_party' AND column_name IN ('creator','updater') AND is_nullable='NO')))=4),
 'SELECT 1 AS sync_179_preconditions_verified','SYNC_179_PRECONDITIONS_NOT_MET');
PREPARE sync_179_guard FROM @sync_guard_sql;
EXECUTE sync_179_guard;
DEALLOCATE PREPARE sync_179_guard;

-- A15 system_users; reviewed SHA-256 a5cbd708daf9f7bdc0a52407c5ae9b561fc64bfdc438c3a67f1d12f7c98ccf54
ALTER TABLE `system_users`
  ADD COLUMN employee_no VARCHAR(50) NULL COMMENT '真实员工工号',
  ADD COLUMN custom_info JSON NULL COMMENT '账号扩展业务属性';

-- A16 ast_product_official_info; reviewed SHA-256 36c5f77ff5fb24fb5ef922457a16db6e10b3cf70bb3f911e761e7317a7b7dbf9
ALTER TABLE `ast_product_official_info`
  ADD COLUMN `product_category` varchar(100) DEFAULT NULL COMMENT 'CRM productCode',
  ADD COLUMN `product_type` varchar(100) DEFAULT NULL COMMENT 'CRM productSubCode',
  ADD UNIQUE INDEX uk_product_official_tenant_code (tenant_id,product_code);

-- A17 com_shipment_contract_reference; reviewed SHA-256 0eaeafc8c0d1bce4c50380e44e90d154f9567d4cc601f5b6b93945f5dceb2626
ALTER TABLE `com_shipment_contract_reference`
  ADD COLUMN warranty_years INT NULL COMMENT '合同保修年数，原空白或NULL表示未知；不代表当前保修状态';

-- A18 com_shipment_package; reviewed SHA-256 7c5f9b3e8d544536c96bcbf86106856efdb3147132ed0627f0c00b482b0eda54
ALTER TABLE `com_shipment_package`
  ADD COLUMN carrier_code VARCHAR(256) NULL,
  ADD COLUMN shipment_contract_source_key VARCHAR(128) NULL COMMENT '原fb_shipment.con_id，保留精确父来源身份';

-- A19 proj_project; reviewed SHA-256 3891519e8625d4f5eb0209907058f2cdcad76d13850bc9f8845d9ac21f0d7057
ALTER TABLE `proj_project`
  ADD COLUMN custom_info JSON NULL,
  ADD COLUMN order_create_time DATETIME(3) NULL,
  ADD COLUMN disabled BIT(1) NULL,
  ADD COLUMN effective_from DATETIME(3) NULL,
  ADD COLUMN effective_to DATETIME(3) NULL;

-- A20 com_delivery_scope; reviewed SHA-256 4cf2f220c1f95e07843c8cb2821f23d5b272b10aba717e826eef429dedc89462
ALTER TABLE `com_delivery_scope`
  ADD COLUMN allocation_confirmed BIT(1) NOT NULL DEFAULT b'1' COMMENT '是否确认占用订单分配数量',
  ADD COLUMN delivered_quantity DECIMAL(18,6) NULL COMMENT '项目范围原已发货数量',
  ADD COLUMN open_quantity DECIMAL(18,6) NULL COMMENT '项目范围原订单未交数量',
  MODIFY COLUMN current_order_line_id BIGINT GENERATED ALWAYS AS (CASE WHEN allocation_confirmed=b'1' AND deleted=0 AND effective_to IS NULL THEN order_line_id ELSE NULL END) STORED,
  DROP INDEX uk_scope_version,
  ADD UNIQUE KEY uk_scope_version (tenant_id,(CASE WHEN allocation_confirmed=b'1' THEN order_line_id ELSE NULL END),project_id,allocation_version);

-- A21 proj_project_member_assignment; reviewed SHA-256 3f341fe2bf0f0e4a8f8cf2eb4229e96ba619e0f01937cfbe2839923a787d7c15
ALTER TABLE `proj_project_member_assignment`
  ADD COLUMN member_code VARCHAR(45) NULL COMMENT '成员区间人员编码',
  ADD COLUMN phone_number VARCHAR(20) NULL COMMENT '成员区间联系电话',
  ADD COLUMN email VARCHAR(45) NULL COMMENT '成员区间邮箱',
  ADD COLUMN registration_source VARCHAR(2) NULL COMMENT '登记来源：1项目信息，2手工成员',
  MODIFY COLUMN create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，未确认历史可保留原NULL',
  MODIFY COLUMN update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间，未确认历史可保留原NULL',
  ADD CONSTRAINT ck_proj_member_confirmed_identity_audit CHECK (authorization_confirmed=b'0' OR (user_id IS NOT NULL AND create_time IS NOT NULL AND update_time IS NOT NULL));

-- A22 proj_project_party; reviewed SHA-256 7d56ee174acea35b0d05a4e686621b2b4fc8144c5f0a48b45a27138a077c1dc7
ALTER TABLE `proj_project_party`
  MODIFY COLUMN creator VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建人',
  MODIFY COLUMN updater VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新人';

-- A24 cus_customer_master; reviewed SHA-256 b2d7d226bc6424754d28b70f8d4caf23f7a69b44a2b043a4ef95327797328be8
ALTER TABLE `cus_customer_master`
  MODIFY COLUMN `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户名称';

-- A25 imp_eng_installation; reviewed SHA-256 924ef756aa2dac8d0518acabfcf9a665e3c637934a373ee5307daba4d7fd42b1
ALTER TABLE `imp_eng_installation`
  MODIFY COLUMN `install_location` VARCHAR(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '安装位置';
