-- Chronologically replayed from cc03787ec9c761358756da6320728928b47eaa39 (codex/f-acc-001-sds), original sql/migrations/V125__fcom001_permissions_menu_and_acceptance_seed.sql.
-- Renumbered after current master; Feature status is not promoted by this receipt.

-- F-COM-001 / COM-01：先补齐ERP订单行产品编码，再写本Feature权限、菜单与验收种子。
-- 本阶段只落加性列和精确V72受管夹具值；后续Step 7继续在本未执行迁移中追加正式配置。

ALTER TABLE `com_sales_order_line`
  ADD COLUMN `product_code` varchar(64) NULL COMMENT 'ERP订单行产品编码' AFTER `product_id`;

UPDATE `com_sales_order_line`
   SET `product_code` = CASE `id`
         WHEN 992002300001 THEN 'FPROJ002-V18-COMPAT-001'
         WHEN 992002300002 THEN 'FPROJ002-V18-COMPAT-002'
         WHEN 992002300003 THEN 'FPROJ002-V18-COMPAT-003'
         WHEN 992002300004 THEN 'FPROJ002-V18-COMPAT-004'
       END,
       `updater` = 'seed',
       `update_time` = NOW(3)
 WHERE `tenant_id` = 0
   AND `id` IN (992002300001, 992002300002, 992002300003, 992002300004)
   AND `order_id` = 992002399001
   AND `source_system` = 'SEED'
   AND `source_record_key` IN (
     'FPROJ002-V18-CONFIRMED',
     'FPROJ002-V18-PENDING',
     'FPROJ002-V18-NO-MATCH',
     'FPROJ002-V18-INACTIVE')
   AND `source_version` = '1'
   AND `creator` = 'seed'
   AND `updater` = 'seed'
   AND `deleted` = b'0';

DELIMITER $$
CREATE PROCEDURE `fcom001_verify_v125_managed_product_codes`()
BEGIN
  IF (SELECT COUNT(*)
        FROM `com_sales_order_line`
       WHERE `tenant_id` = 0
         AND ((`id` = 992002300001 AND `source_record_key` = 'FPROJ002-V18-CONFIRMED'
               AND `product_code` = 'FPROJ002-V18-COMPAT-001')
           OR (`id` = 992002300002 AND `source_record_key` = 'FPROJ002-V18-PENDING'
               AND `product_code` = 'FPROJ002-V18-COMPAT-002')
           OR (`id` = 992002300003 AND `source_record_key` = 'FPROJ002-V18-NO-MATCH'
               AND `product_code` = 'FPROJ002-V18-COMPAT-003')
           OR (`id` = 992002300004 AND `source_record_key` = 'FPROJ002-V18-INACTIVE'
               AND `product_code` = 'FPROJ002-V18-COMPAT-004'))
         AND `order_id` = 992002399001
         AND `source_system` = 'SEED'
         AND `source_version` = '1'
         AND `creator` = 'seed'
         AND `updater` = 'seed'
         AND `deleted` = b'0') <> 4 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-COM-001 V125 managed seed identity is incomplete';
  END IF;
END$$
DELIMITER ;

CALL `fcom001_verify_v125_managed_product_codes`();
DROP PROCEDURE `fcom001_verify_v125_managed_product_codes`;
