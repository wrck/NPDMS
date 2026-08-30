DROP PROCEDURE IF EXISTS `fcom001_require_empty_order_contract_relation`;
DELIMITER $$
CREATE PROCEDURE `fcom001_require_empty_order_contract_relation`()
BEGIN
    IF EXISTS (SELECT 1 FROM `com_sales_order_contract_relation` LIMIT 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'F-COM-001 order-contract relation identity migration requires an empty table';
    END IF;
END$$
DELIMITER ;
CALL `fcom001_require_empty_order_contract_relation`();
DROP PROCEDURE IF EXISTS `fcom001_require_empty_order_contract_relation`;

ALTER TABLE `com_sales_order_contract_relation`
    DROP INDEX `uk_com_order_contract_source`,
    ADD COLUMN `sales_order_source_key` varchar(128) NOT NULL AFTER `source_system`,
    ADD COLUMN `contract_source_key` varchar(128) NOT NULL AFTER `sales_order_source_key`,
    ADD UNIQUE KEY `uk_com_order_contract_source_pair`
        (`tenant_id`, `source_system`, `sales_order_source_key`, `contract_source_key`),
    DROP COLUMN `source_key`;
