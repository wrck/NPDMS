-- 2026-09-20 专项业务裁决：满意度可选择已发布问卷独立发起，原业务时点来源保留。
ALTER TABLE acc_satisfaction_collection_task
    MODIFY COLUMN project_task_id BIGINT NULL,
    ADD COLUMN origin_kind VARCHAR(24) NOT NULL DEFAULT 'LEGACY_TASK',
    ADD COLUMN origin_key VARCHAR(191) NULL,
    ADD COLUMN origin_snapshot JSON NULL,
    ADD UNIQUE KEY uk_acc_sat_direct_origin (tenant_id, origin_kind, origin_key),
    ADD CONSTRAINT chk_acc_sat_origin CHECK (
        (origin_kind = 'LEGACY_TASK' AND project_task_id IS NOT NULL)
        OR (origin_kind = 'DIRECT' AND project_task_id IS NULL AND deliverable_id IS NULL
            AND origin_key IS NOT NULL AND origin_snapshot IS NOT NULL)
    );

-- An independent result has no automatic deliverable archive target until explicitly associated.
ALTER TABLE acc_satisfaction_result MODIFY COLUMN archive_status VARCHAR(32) NULL;
