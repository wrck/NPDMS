-- Merge missing phase management fields into the existing native stage entity.
-- No pms_project_phase data backfill and no modification of historical execution records.
ALTER TABLE proj_project_stage
    ADD COLUMN suggested_start_time DATETIME(3) NULL COMMENT '建议开始时间',
    ADD COLUMN suggested_end_time DATETIME(3) NULL COMMENT '建议结束时间',
    ADD COLUMN plan_start_time DATETIME(3) NULL COMMENT '计划开始时间',
    ADD COLUMN plan_end_time DATETIME(3) NULL COMMENT '计划结束时间',
    ADD COLUMN actual_start_time DATETIME(3) NULL COMMENT '当前轮次阶段激活时间',
    ADD COLUMN actual_end_time DATETIME(3) NULL COMMENT '当前轮次阶段结束时间',
    ADD COLUMN deviation_reason VARCHAR(500) NULL COMMENT '偏差原因',
    ADD COLUMN responsible_role VARCHAR(64) NULL COMMENT '保留字段，暂不参与业务',
    ADD COLUMN responsible_user_id BIGINT NULL COMMENT '保留字段，暂不参与业务';
