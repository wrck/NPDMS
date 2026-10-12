-- Supports the existing PLT global source identity lookup across import batches.
-- Non-unique: keeps every source revision, mapping and immutable receipt intact.
ALTER TABLE plt_migration_source_record
    ADD INDEX idx_plt_migration_global_source_identity
        (tenant_id, source_system, source_table, source_record_key, deleted),
    ALGORITHM=INPLACE, LOCK=NONE;
