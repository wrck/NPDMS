-- =============================================================
-- V280__fix_lowcode_permissions_and_columns.sql
-- Persist LowCode module integration fixes (column alignment):
--   1. Add deleted column to low_* tables that lack it
--   2. Add create_by/update_by columns to low_import_task
--   3. Add operate_time/update_time/create_by/update_by to low_config_audit_log
-- (源工程 V60 的 4/5 权限注册段（权限菜单 INSERT + super admin role 绑定）已按用户
--  指令剔除，用户权限/菜单体系不迁移；权限对齐由 yudao system_menu 体系承担)
--
-- NOTE: Uses PREPARE/EXECUTE with INFORMATION_SCHEMA check to be
--       idempotent (safe for re-run when columns already exist).
--       MySQL 8.0.16 does not support ADD COLUMN IF NOT EXISTS,
--       and Flyway does not support DELIMITER syntax.
-- =============================================================

-- 1. Add deleted column to tables that lack it (idempotent)
-- low_datasource
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_datasource' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_datasource ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_approval_chain' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_approval_chain ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_backup_record' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_backup_record ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_collaboration_session' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_collaboration_session ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_component_meta' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_component_meta ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_config_audit_log' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_config_audit_log ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_config_template' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_config_template ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_ddl_backup' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_ddl_backup ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_ddl_execution_log' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_ddl_execution_log ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_edit_lock' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_edit_lock ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_gray_release' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_gray_release ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_import_task' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_import_task ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_microflow_version' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_microflow_version ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_process_sla_record' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_process_sla_record ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_publish_record' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_publish_record ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_rule_test_case' AND column_name = 'deleted');
SET @sql = IF(@c = 0, 'ALTER TABLE low_rule_test_case ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''Logical delete 0=no 1=yes''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. Add create_by/update_by to low_import_task
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_import_task' AND column_name = 'create_by');
SET @sql = IF(@c = 0, 'ALTER TABLE low_import_task ADD COLUMN create_by VARCHAR(64) DEFAULT ''''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_import_task' AND column_name = 'update_by');
SET @sql = IF(@c = 0, 'ALTER TABLE low_import_task ADD COLUMN update_by VARCHAR(64) DEFAULT ''''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.1 Add operate_time/update_time/create_by/update_by to low_config_audit_log
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_config_audit_log' AND column_name = 'operate_time');
SET @sql = IF(@c = 0, 'ALTER TABLE low_config_audit_log ADD COLUMN operate_time DATETIME DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_config_audit_log' AND column_name = 'update_time');
SET @sql = IF(@c = 0, 'ALTER TABLE low_config_audit_log ADD COLUMN update_time DATETIME DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_config_audit_log' AND column_name = 'create_by');
SET @sql = IF(@c = 0, 'ALTER TABLE low_config_audit_log ADD COLUMN create_by VARCHAR(64) DEFAULT ''''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'low_config_audit_log' AND column_name = 'update_by');
SET @sql = IF(@c = 0, 'ALTER TABLE low_config_audit_log ADD COLUMN update_by VARCHAR(64) DEFAULT ''''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =============================================================
-- 适配自检（迁移自源工程 V60，renumber V280）
-- =============================================================
-- 适配内容：
--   - 保留：幂等列对齐段（deleted TINYINT / create_by/update_by VARCHAR(64) /
--     operate_time/update_time DATETIME），与 pms-module-lowcode 实体
--     （cn.iocoder.yudao.module.pms.lowcode.entity.*，继承 yudao BaseDO）对齐
--   - 剔除：源工程权限注册段（权限菜单 INSERT + super admin role 绑定），
--     用户权限/菜单体系不迁移（用户指令）；权限对齐由 yudao system_menu 承担
--   - 幂等：information_schema 检测列存在才 ALTER，重复执行安全
-- 文件结束
