-- 项目成员外部最终用户职务；部门复用现有 department_name 区间资料。
-- 可空前向扩展，不回填或覆盖历史，不改变系统身份、部门关系和项目授权。
SET @member_title_exists := (SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='proj_project_member_assignment' AND column_name='title');
SET @member_title_sql := IF(@member_title_exists=0,
 'ALTER TABLE proj_project_member_assignment ADD COLUMN title VARCHAR(64) NULL COMMENT ''外部最终用户职务，随成员区间留痕''',
 'SELECT 1');
PREPARE member_title_statement FROM @member_title_sql;
EXECUTE member_title_statement;
DEALLOCATE PREPARE member_title_statement;
