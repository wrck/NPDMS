-- P12-B5 统一业务模型：SRV/PLT 聚合主键切换为数据库自增 + 采集任务补行版本列
-- BaseBusinessEntity（IdType.AUTO + @Version Long）要求：主键由数据库自增、行版本由 MyBatis-Plus 乐观锁维护。
-- 既有行 id 保持不变，仅新生成 id 改由数据库自增；srv_inspection_rule 已是 AUTO_INCREMENT+version，无需调整。
-- 子表/外键引用这些主键列类型不变（BIGINT→BIGINT），会话内关闭外键检查以允许列重建。
SET FOREIGN_KEY_CHECKS = 0;
ALTER TABLE plt_authorization_grant
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '授权记录编号';
ALTER TABLE plt_business_view_revision
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '业务视图修订编号';
ALTER TABLE plt_collection_template
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '采集模板编号';
ALTER TABLE plt_collection_task
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '采集任务编号',
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '行版本（乐观锁）' AFTER id;
SET FOREIGN_KEY_CHECKS = 1;
