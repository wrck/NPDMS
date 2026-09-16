-- Shared instance fields only: no template fields, formulas or work-hour additions.
-- Existing rows remain NULL; do not infer acceptance from completion or backfill history.
ALTER TABLE proj_project_stage
    ADD COLUMN acceptance_time DATETIME(3) NULL COMMENT '验收时间，与实际结束时间独立';

ALTER TABLE proj_project_task
    ADD COLUMN suggested_start_time DATETIME(3) NULL COMMENT '建议开始时间',
    ADD COLUMN suggested_end_time DATETIME(3) NULL COMMENT '建议结束时间',
    ADD COLUMN acceptance_time DATETIME(3) NULL COMMENT '验收时间，与实际结束时间独立';
