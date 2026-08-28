-- F-PROJ-005 / PM-08：服务经理人工指派物理基础。
-- 兼容既有成员关系与无投递键的站内信；新写入的必填约束由应用服务执行。

SET @col := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'proj_project_member_assignment'
               AND column_name = 'department_id');
SET @sql := IF(@col = 0,
    'ALTER TABLE `proj_project_member_assignment` ADD COLUMN `department_id` BIGINT NULL COMMENT ''部门ID快照'' AFTER `company_name`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'proj_project_member_assignment'
               AND column_name = 'assignment_type');
SET @sql := IF(@col = 0,
    'ALTER TABLE `proj_project_member_assignment` ADD COLUMN `assignment_type` VARCHAR(32) NULL COMMENT ''责任类型：PRIMARY/COLLABORATOR'' AFTER `member_role`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'proj_project_member_assignment'
               AND column_name = 'site_id');
SET @sql := IF(@col = 0,
    'ALTER TABLE `proj_project_member_assignment` ADD COLUMN `site_id` BIGINT NULL COMMENT ''AST站点稳定ID'' AFTER `assignment_type`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'proj_project_member_assignment'
               AND column_name = 'change_reason');
SET @sql := IF(@col = 0,
    'ALTER TABLE `proj_project_member_assignment` ADD COLUMN `change_reason` VARCHAR(500) NULL COMMENT ''指派或改派原因'' AFTER `responsibility`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE()
               AND table_name = 'proj_project_member_assignment'
               AND index_name = 'idx_proj_member_current_responsibility');
SET @sql := IF(@idx = 0,
    'ALTER TABLE `proj_project_member_assignment` ADD KEY `idx_proj_member_current_responsibility` (`tenant_id`,`project_id`,`member_role`,`assignment_type`,`site_id`,`status`,`effective_to`)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'system_notify_message'
               AND column_name = 'delivery_key');
SET @sql := IF(@col = 0,
    'ALTER TABLE `system_notify_message` ADD COLUMN `delivery_key` VARCHAR(128) NULL COMMENT ''业务投递幂等键'' AFTER `user_type`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE()
               AND table_name = 'system_notify_message'
               AND index_name = 'uk_system_notify_message_delivery');
SET @sql := IF(@idx = 0,
    'ALTER TABLE `system_notify_message` ADD UNIQUE KEY `uk_system_notify_message_delivery` (`tenant_id`,`user_type`,`delivery_key`)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
