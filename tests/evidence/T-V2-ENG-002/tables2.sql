-- 列出所有包含 project_id 列的表，并统计每个表的 project 维度
SELECT
  table_name,
  table_rows
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name LIKE 'pms_%'
ORDER BY table_name;
