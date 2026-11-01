-- Target-only first administrator setup after B530 succeeds.
-- Provide a newly generated BCrypt hash as @npdms_admin_password_hash in this
-- same session via the approved secret channel. Never save credentials here.
-- No source password, password default, storage or external secret is copied.
-- Run with a client that stops on the first error (never --force).
SET @npdms_admin_password_hash=CONVERT(COALESCE(@npdms_admin_password_hash,'') USING utf8mb4);
CREATE TEMPORARY TABLE production_admin_bootstrap_guard (
 valid_hash TINYINT NOT NULL CHECK (valid_hash=1),
 initial_admin TINYINT NOT NULL CHECK (initial_admin=1)
);
INSERT INTO production_admin_bootstrap_guard
SELECT IF(CHAR_LENGTH(@npdms_admin_password_hash)=60
 AND LEFT(@npdms_admin_password_hash,4) IN ('$2a$','$2b$','$2y$')
 AND SUBSTRING(@npdms_admin_password_hash,5,2) REGEXP '^[0-9]{2}$'
 AND CAST(SUBSTRING(@npdms_admin_password_hash,5,2) AS UNSIGNED) BETWEEN 10 AND 31
 AND SUBSTRING(@npdms_admin_password_hash,7,1)='$'
 AND SUBSTRING(@npdms_admin_password_hash,8) REGEXP '^[./A-Za-z0-9]{53}$',1,0),
 IF((SELECT COUNT(*) FROM system_users WHERE id=1 AND username='admin' AND tenant_id=1
     AND deleted=0 AND status=1 AND password='!BOOTSTRAP_REQUIRED!')=1,1,0);
DROP TEMPORARY TABLE production_admin_bootstrap_guard;
UPDATE system_users
SET password=@npdms_admin_password_hash,status=0,updater='production-bootstrap'
WHERE id=1 AND tenant_id=1 AND deleted=0 AND status=1 AND password='!BOOTSTRAP_REQUIRED!';
SET @npdms_admin_password_hash=NULL;
