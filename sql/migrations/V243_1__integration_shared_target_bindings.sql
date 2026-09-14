-- Preserve source uniqueness and the existing one-to-one rule for organization adapters.
-- Canonical order adapters retain every equivalent legacy row against the same target.
ALTER TABLE int_sync_binding
    ADD COLUMN target_shared boolean NOT NULL DEFAULT FALSE,
    ADD COLUMN exclusive_target_id bigint GENERATED ALWAYS AS
        (CASE WHEN target_shared = FALSE THEN target_id ELSE NULL END) STORED,
    DROP INDEX uk_int_binding_target,
    ADD UNIQUE KEY uk_int_binding_target (tenant_id, task_id, object_key, exclusive_target_id),
    ADD KEY idx_int_binding_target (tenant_id, task_id, object_key, target_id);
