-- Reuse the existing inspection report revision. No new table or existing-data rewrite.
-- Prepare only; protected test database DDL requires separate explicit authorization.
SET @umc_field_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='srv_inspection_report_revision' AND COLUMN_NAME='source_context_json');
SET @umc_ddl = IF(@umc_field_exists=0, 'ALTER TABLE srv_inspection_report_revision ADD COLUMN source_context_json JSON NULL COMMENT ''External report input reference and recovery context''', 'SELECT 1');
PREPARE umc_stmt FROM @umc_ddl; EXECUTE umc_stmt; DEALLOCATE PREPARE umc_stmt;

SET @umc_field_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='srv_inspection_report_revision' AND COLUMN_NAME='result_json');
SET @umc_ddl = IF(@umc_field_exists=0, 'ALTER TABLE srv_inspection_report_revision ADD COLUMN result_json JSON NULL COMMENT ''Report result business snapshot''', 'SELECT 1');
PREPARE umc_stmt FROM @umc_ddl; EXECUTE umc_stmt; DEALLOCATE PREPARE umc_stmt;

SET @umc_field_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='srv_inspection_report_revision' AND COLUMN_NAME='result_sha256');
SET @umc_ddl = IF(@umc_field_exists=0, 'ALTER TABLE srv_inspection_report_revision ADD COLUMN result_sha256 CHAR(64) NULL COMMENT ''Canonical report result digest''', 'SELECT 1');
PREPARE umc_stmt FROM @umc_ddl; EXECUTE umc_stmt; DEALLOCATE PREPARE umc_stmt;

SET @umc_source_check_exists = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='srv_inspection_report_revision' AND CONSTRAINT_NAME='ck_ins_report_source' AND CONSTRAINT_TYPE='CHECK');
SET @umc_ddl = IF(@umc_source_check_exists>0, 'ALTER TABLE srv_inspection_report_revision DROP CHECK ck_ins_report_source', 'SELECT 1');
PREPARE umc_stmt FROM @umc_ddl; EXECUTE umc_stmt; DEALLOCATE PREPARE umc_stmt;
ALTER TABLE srv_inspection_report_revision ADD CONSTRAINT ck_ins_report_source CHECK (source_code IN ('PLATFORM','MANUAL','UMC'));
-- Existing report immutability trigger, foreign keys and DOC format constraint remain in force.
