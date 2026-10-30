-- V347: 退役客户联系人"联系人角色"（2026-09-23需求方指令：联系人角色与职务语义重复，整字段去除）
-- 角色仅存在于项目侧关系表 cus_project_customer_contact_relation.role_code，主档表无该列；
-- 既有数据 3 行全部 NULL，无信息丢失。字典类型 pms_customer_contact_role（V217 引入）无任何字典项，做逻辑退役。
-- 幂等：仅当列仍存在时删除；字典类型仅当未删除时逻辑删除，不物理覆盖。

SET @has_role_code = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cus_project_customer_contact_relation' AND COLUMN_NAME = 'role_code');
SET @ddl = IF(@has_role_code > 0,
    'ALTER TABLE cus_project_customer_contact_relation DROP COLUMN role_code',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE system_dict_type
SET deleted = b'1', deleted_time = NOW(), update_time = NOW()
WHERE type = 'pms_customer_contact_role' AND deleted = b'0';
