-- Dedicated fixture tables only. Run in the named isolated acceptance database, never a business database.
CREATE TABLE IF NOT EXISTS it_default_sheet (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 title VARCHAR(128) NOT NULL, confirmed BIT, remark TEXT, version BIGINT NOT NULL DEFAULT 0,
 creator VARCHAR(64), updater VARCHAR(64), create_time DATETIME, update_time DATETIME, deleted BIT NOT NULL DEFAULT b'0'
) AUTO_INCREMENT=993910090100;
CREATE TABLE IF NOT EXISTS it_default_sheet_line (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, aggregate_type VARCHAR(255) NOT NULL,
 aggregate_id BIGINT NOT NULL, aggregate_revision_id BIGINT, detail_order INT NOT NULL,
 description VARCHAR(128) NOT NULL, quantity DECIMAL(20,4), enabled BIT, version BIGINT NOT NULL DEFAULT 0,
 creator VARCHAR(64), updater VARCHAR(64), create_time DATETIME, update_time DATETIME, deleted BIT NOT NULL DEFAULT b'0',
 KEY idx_acceptance_sheet_line (tenant_id,aggregate_type,aggregate_id,aggregate_revision_id,deleted)
) AUTO_INCREMENT=993910090200;
CREATE TABLE IF NOT EXISTS it_default_memo (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 title VARCHAR(128) NOT NULL, content TEXT, enabled BIT, version BIGINT NOT NULL DEFAULT 0,
 creator VARCHAR(64), updater VARCHAR(64), create_time DATETIME, update_time DATETIME, deleted BIT NOT NULL DEFAULT b'0'
) AUTO_INCREMENT=993910090300;
CREATE TABLE IF NOT EXISTS it_default_memo_revision (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 title VARCHAR(128) NOT NULL, content TEXT, enabled BIT, version BIGINT NOT NULL DEFAULT 0,
 entity_id BIGINT NOT NULL, revision_no INT NOT NULL, source_revision_id BIGINT, base_effective_revision_id BIGINT,
 base_entity_version INT, change_reason VARCHAR(255), frozen_by BIGINT, frozen_at DATETIME,
 revision_state VARCHAR(16) NOT NULL, effective BIT NOT NULL DEFAULT b'0',
 creator VARCHAR(64), updater VARCHAR(64), create_time DATETIME, update_time DATETIME, deleted BIT NOT NULL DEFAULT b'0',
 UNIQUE KEY uk_acceptance_memo_revision (tenant_id,entity_id,revision_no)
) AUTO_INCREMENT=993910090400;
