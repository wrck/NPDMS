-- P12-B6 统一业务模型：PRJ 聚合主键切换为数据库自增（BaseBusinessEntity IdType.AUTO），
-- 并为两个未启用乐观锁的遗留表补 version 列。
-- 原主键由应用侧雪花分配（ASSIGN_ID / 手写 XML 显式 id）；既有行 id 保持不变，仅新生成 id 改由数据库自增。
-- 子表/外键引用这些主键列类型不变（BIGINT→BIGINT），会话内关闭外键检查以允许列重建。
SET FOREIGN_KEY_CHECKS = 0;
ALTER TABLE proj_project
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目主档编号';
ALTER TABLE proj_project_portfolio
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目组合编号';
ALTER TABLE proj_project_plan_version
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目计划版本编号';
ALTER TABLE proj_project_split_request
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目拆分申请编号';
ALTER TABLE proj_project_tree_version
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目树版本编号';
ALTER TABLE proj_project_tree_change
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目树变更编号';
ALTER TABLE proj_stage_suggestion_rule
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '阶段建议规则编号',
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '行版本（乐观锁）' AFTER id;
ALTER TABLE proj_project_exit_record
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目正常收尾退出记录编号',
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '行版本（乐观锁）' AFTER id;
SET FOREIGN_KEY_CHECKS = 1;
