-- Independent project acceptance keeps the existing project/type identity and legacy task provenance.
-- The rule is the 2026-09-20 approved report PASS policy; template/node conditions remain frozen in PROJ.
ALTER TABLE acc_acceptance
    MODIFY project_task_id BIGINT NULL,
    MODIFY execution_contract_id BIGINT NULL,
    MODIFY deliverable_id BIGINT NULL,
    ADD origin_kind VARCHAR(32) NOT NULL DEFAULT 'LEGACY_TASK',
    ADD origin_key VARCHAR(190) COLLATE utf8mb4_bin NULL,
    ADD origin_snapshot JSON NULL,
    ADD rule_snapshot JSON NULL,
    ADD UNIQUE KEY uk_acc_acceptance_origin (tenant_id, origin_key),
    ADD CONSTRAINT chk_acc_acceptance_origin CHECK (
        (origin_kind = 'LEGACY_TASK' AND project_task_id IS NOT NULL AND project_task_id > 0
            AND execution_contract_id IS NOT NULL AND execution_contract_id > 0)
        OR (origin_kind = 'DIRECT' AND project_task_id IS NULL AND execution_contract_id IS NULL
            AND deliverable_id IS NULL AND origin_key IS NOT NULL AND origin_snapshot IS NOT NULL
            AND rule_snapshot IS NOT NULL AND JSON_TYPE(origin_snapshot) = 'OBJECT' AND JSON_TYPE(rule_snapshot) = 'OBJECT')
    );
ALTER TABLE acc_acceptance_report_version ADD acceptance_rule_snapshot JSON NULL;
