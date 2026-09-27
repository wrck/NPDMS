-- P12-B3 统一业务模型：per_source 源记录修订列承载 ACC 聚合 Long 版本
ALTER TABLE proj_project_exit_record
  MODIFY COLUMN source_record_revision BIGINT NOT NULL COMMENT '来源记录修订（聚合版本）';
