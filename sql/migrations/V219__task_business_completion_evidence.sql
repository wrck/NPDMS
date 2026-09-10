-- PM-11 / PM-03: append-only completion evaluations retain exact Owner identities,
-- opaque fact versions, aggregate version and configured criterion results.
-- Existing native/acceptance evaluations remain unchanged.
ALTER TABLE proj_project_task_completion_evaluation
    ADD COLUMN business_facts_json JSON NULL COMMENT '冻结业务关联事实及逐项完成判定证据'
    AFTER gate_snapshot_ref;
