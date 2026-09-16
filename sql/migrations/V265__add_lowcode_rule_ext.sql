-- =============================================================
-- V265__add_lowcode_rule_ext.sql
-- 为低代码规则表添加扩展字段 ext。
--
-- <p>用于存储规则的可视化设计器辅助信息，如表达式规则的 inputsSchema
-- （用户手动维护的输入变量 schema，供表达式编辑器变量补全与测试面板生成模板）。
-- 字段为 LONGTEXT NULL，纯增量变更，向后兼容。</p>
--
-- <p>注意：Flyway 每个版本仅执行一次，MySQL 8 不支持 ADD COLUMN IF NOT EXISTS 语法，
-- 标准 ALTER TABLE ADD COLUMN 即可。</p>
-- =============================================================

ALTER TABLE `low_rule`
    ADD COLUMN `ext` LONGTEXT NULL COMMENT '扩展信息（JSON，如表达式规则 inputsSchema）';
