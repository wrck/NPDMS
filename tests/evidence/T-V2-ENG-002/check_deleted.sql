-- 查看项目实际数据
SELECT id, code, status, deleted FROM pms_project LIMIT 5;
SELECT COUNT(*) AS total FROM pms_project WHERE deleted = 0;
SELECT COUNT(*) AS total_b0 FROM pms_project WHERE deleted = b'0';
SELECT COUNT(*) AS total_b1 FROM pms_project WHERE deleted = b'1';
