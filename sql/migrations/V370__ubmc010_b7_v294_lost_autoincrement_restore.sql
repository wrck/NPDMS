-- V294 字段注释对齐用 MODIFY COLUMN 重定义了这些表的 `id` 列，但遗漏了原 DDL 的
-- AUTO_INCREMENT 属性；BaseBusinessEntity 统一声明 IdType.AUTO，依赖数据库自增生成主键，
-- 批量初始化（如项目创建实例化 ACC 交付件）在缺失自增的表上以
-- "Field 'id' doesn't have a default value" 失败。
-- 本迁移仅恢复与原建表 DDL（V52/V57/V63/V88/V289）一致的自增属性；既有行 id 与业务数据不变。
-- proj_project 等其余聚合已由 V369 恢复，不在此重复。
SET FOREIGN_KEY_CHECKS = 0;
ALTER TABLE proj_project_template_task_definition
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE proj_project_member_assignment
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE proj_project_company_department_relation
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE proj_project_task_completion_evaluation
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE acc_project_deliverable
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE ast_device_version
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID';
SET FOREIGN_KEY_CHECKS = 1;
