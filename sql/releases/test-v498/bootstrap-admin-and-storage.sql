-- One-time bootstrap AFTER B498 has succeeded, in the SAME explicitly selected schema.
-- Set these session variables through your secure deployment channel before SOURCE:
--   @npdms_admin_password_hash: a newly generated BCrypt hash (60 characters, cost 10..31).
--   @npdms_public_backend_origin: public HTTP(S) backend origin, without trailing slash.
-- Do not put a plaintext password, a hash or database credentials in the release files.
-- mysql must stop on errors; do not use --force.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
-- Unset MySQL user variables have binary type; normalize before REGEXP validation.
SET @npdms_admin_password_hash=CONVERT(COALESCE(@npdms_admin_password_hash,'') USING utf8mb4);
SET @npdms_public_backend_origin=CONVERT(COALESCE(@npdms_public_backend_origin,'') USING utf8mb4);

CREATE TEMPORARY TABLE npdms_bootstrap_guard (
  valid_hash TINYINT NOT NULL,
  valid_origin TINYINT NOT NULL,
  initial_identity TINYINT NOT NULL,
  empty_storage TINYINT NOT NULL,
  CONSTRAINT chk_bootstrap_requires_new_bcrypt_hash CHECK (valid_hash = 1),
  CONSTRAINT chk_bootstrap_requires_backend_origin CHECK (valid_origin = 1),
  CONSTRAINT chk_bootstrap_refuses_existing_admin CHECK (initial_identity = 1),
  CONSTRAINT chk_bootstrap_refuses_existing_storage CHECK (empty_storage = 1)
);
INSERT INTO npdms_bootstrap_guard
SELECT
  IF(@npdms_admin_password_hash IS NOT NULL
    AND CHAR_LENGTH(@npdms_admin_password_hash)=60
    AND LEFT(@npdms_admin_password_hash,4) IN ('$2a$','$2b$','$2y$')
    AND SUBSTRING(@npdms_admin_password_hash,5,2) REGEXP '^[0-9]{2}$'
    AND CAST(SUBSTRING(@npdms_admin_password_hash,5,2) AS UNSIGNED) BETWEEN 10 AND 31
    AND SUBSTRING(@npdms_admin_password_hash,7,1)='$'
    AND SUBSTRING(@npdms_admin_password_hash,8) REGEXP '^[./A-Za-z0-9]{53}$', 1, 0),
  IF(@npdms_public_backend_origin IS NOT NULL
    AND @npdms_public_backend_origin REGEXP '^https?://[^/@[:space:]]+$', 1, 0),
  IF((SELECT COUNT(*) FROM system_users WHERE id=1 AND username='admin'
      AND tenant_id=1 AND deleted=b'0' AND status=1
      AND password='!BOOTSTRAP_REQUIRED!')=1, 1, 0),
  IF((SELECT COUNT(*) FROM infra_file_config)=0, 1, 0);
DROP TEMPORARY TABLE npdms_bootstrap_guard;

START TRANSACTION;
UPDATE system_users
SET password=@npdms_admin_password_hash, status=0, updater='release-bootstrap'
WHERE id=1 AND username='admin' AND tenant_id=1 AND deleted=b'0'
  AND status=1 AND password='!BOOTSTRAP_REQUIRED!';

-- Reuse the upstream DB storage provider; no external storage key is required.
INSERT INTO infra_file_config
  (id,name,storage,remark,master,config,creator,updater,deleted)
VALUES
  (4,'数据库存储',1,'正式测试环境基础文件存储',b'1',
   JSON_OBJECT('@class','cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClientConfig',
               'domain',@npdms_public_backend_origin),
   'release-bootstrap','release-bootstrap',b'0');
COMMIT;

SET @npdms_admin_password_hash=NULL;
SET @npdms_public_backend_origin=NULL;
