-- REVIEW ONLY: not executed; no structural-write authorization in this turn.
-- Target: dedicated npdms_reverify_20261014 only. Never npdms_domain_test.
-- No Flyway, migration runner, synchronization task, trigger/index changes or service restart.
-- Preserve existing charset/collation, nullability, default and comments; change length only.

ALTER TABLE `npdms_reverify_20261014`.`cus_customer_master`
  MODIFY COLUMN `name` VARCHAR(255)
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
  NOT NULL COMMENT '客户名称';

ALTER TABLE `npdms_reverify_20261014`.`imp_eng_installation`
  MODIFY COLUMN `install_location` VARCHAR(1024)
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
  NULL DEFAULT NULL COMMENT '安装位置';
