-- ACC-owned immutable mapping; original file/material/snapshot rows remain unchanged.
CREATE TABLE IF NOT EXISTS acc_completion_pdf_binding (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 certificate_id BIGINT NOT NULL,
 certificate_version BIGINT NOT NULL,
 document_kind VARCHAR(16) NOT NULL,
 layout_key CHAR(32) NOT NULL,
 material_id BIGINT NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 creator VARCHAR(64) DEFAULT '',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater VARCHAR(64) DEFAULT '',
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted BIT NOT NULL DEFAULT b'0',
 PRIMARY KEY (id),
 UNIQUE KEY uk_completion_pdf_identity (tenant_id,certificate_id,certificate_version,document_kind,layout_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
