-- 列出所有有 project_id 列的 pms_* 表
SELECT DISTINCT table_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND column_name = 'project_id'
  AND table_name LIKE 'pms_%'
ORDER BY table_name;
