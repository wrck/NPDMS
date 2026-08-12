-- =============================================================================
-- V41: 修复 V21/V22 迁移产生的菜单名称乱码
-- 背景：Flyway JDBC 连接未指定 characterEncoding=utf8mb4，
--      导致 V21（项目组合/服务等级）和 V22（批量变更/工期倒排）中
--      的中文菜单名被 UTF-8→Latin1 二次编码写入数据库。
--      V23 及之后的迁移未受影响（Flyway 升级后行为变化）。
-- 本迁移通过 UPDATE 直接修复这 4 条记录的中文名称。
-- =============================================================================
UPDATE `system_menu` SET `name` = '项目组合', `update_time` = NOW()
WHERE `id` = 19130 AND `deleted` = b'0';

UPDATE `system_menu` SET `name` = '服务等级', `update_time` = NOW()
WHERE `id` = 19135 AND `deleted` = b'0';

UPDATE `system_menu` SET `name` = '批量变更', `update_time` = NOW()
WHERE `id` = 19140 AND `deleted` = b'0';

UPDATE `system_menu` SET `name` = '工期倒排', `update_time` = NOW()
WHERE `id` = 19145 AND `deleted` = b'0';
