-- FB-18: nullable source references only; existing configuration records and file history remain unchanged.
ALTER TABLE imp_eng_configuration
    ADD COLUMN imported_log_file_id BIGINT NULL COMMENT 'AST immutable archived log file ID',
    ADD COLUMN imported_log_batch_id BIGINT NULL COMMENT 'AST immutable log import batch ID',
    ADD UNIQUE KEY uk_imp_configuration_imported_log (tenant_id, imported_log_file_id);
