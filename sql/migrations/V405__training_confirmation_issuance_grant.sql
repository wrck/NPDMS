-- New issuance evidence only. Existing tokens are neither revoked nor attributed to guessed employees.
CREATE TABLE IF NOT EXISTS imp_training_confirmation_grant (
 id BIGINT NOT NULL AUTO_INCREMENT,tenant_id BIGINT NOT NULL,training_id BIGINT NOT NULL,issuance_version BIGINT NOT NULL,
 token_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,issued_by_user_id BIGINT NOT NULL,
 issued_at DATETIME(3) NOT NULL,expires_at DATETIME(3) NOT NULL,scope_version BIGINT NOT NULL,
 confirmation_revision_id BIGINT NULL,confirmation_rules_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 creator VARCHAR(64) NOT NULL DEFAULT '',create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updater VARCHAR(64) NOT NULL DEFAULT '',update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),deleted BIT(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(id),UNIQUE KEY uk_training_grant_issuance(tenant_id,training_id,issuance_version),
 UNIQUE KEY uk_training_grant_token(tenant_id,training_id,token_digest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
