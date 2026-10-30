-- 外部最终用户不参与系统授权；兼容已部署V434的身份审计约束，不改写历史成员。
SET @col := (SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='proj_project_member_assignment' AND column_name='authorization_confirmed');
SET @sql := IF(@col=0,
 'ALTER TABLE proj_project_member_assignment ADD COLUMN authorization_confirmed BIT NOT NULL DEFAULT b''1'' COMMENT ''是否经Owner确认参与当前授权；外部最终用户为0''',
 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @constraint_present := (SELECT COUNT(*) FROM information_schema.table_constraints
 WHERE constraint_schema=DATABASE() AND table_name='proj_project_member_assignment'
 AND constraint_name='ck_proj_member_end_user_no_system_identity');
SET @sql := IF(@constraint_present=0,
 'ALTER TABLE proj_project_member_assignment ADD CONSTRAINT ck_proj_member_end_user_no_system_identity CHECK (member_role <> ''END_USER'' OR (user_id IS NULL AND authorization_confirmed=b''0''))',
 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
