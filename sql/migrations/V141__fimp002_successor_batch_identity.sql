DROP PROCEDURE IF EXISTS `fimp002_require_initial_arrival_roots`;

DELIMITER $$
CREATE PROCEDURE `fimp002_require_initial_arrival_roots`()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM `imp_arrival_acceptance`
        WHERE `predecessor_acceptance_id` IS NOT NULL
           OR `successor_reason` IS NOT NULL
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'F-IMP-002 successor batch identity requires evidence-based reconciliation';
    END IF;
END$$
DELIMITER ;

CALL `fimp002_require_initial_arrival_roots`();

ALTER TABLE `imp_arrival_acceptance`
    ADD COLUMN `batch_root_marker` tinyint NULL AFTER `batch_code`;

UPDATE `imp_arrival_acceptance`
SET `batch_root_marker` = 1
WHERE `predecessor_acceptance_id` IS NULL
  AND `successor_reason` IS NULL;

ALTER TABLE `imp_arrival_acceptance`
    DROP INDEX `uk_imp_arrival_batch`,
    DROP CHECK `chk_imp_arrival_successor_pair`,
    ADD CONSTRAINT `chk_imp_arrival_batch_root_marker`
        CHECK (`batch_root_marker` IS NULL OR `batch_root_marker` = 1),
    ADD CONSTRAINT `chk_imp_arrival_batch_chain`
        CHECK ((`predecessor_acceptance_id` IS NULL
                    AND `successor_reason` IS NULL
                    AND `batch_root_marker` IS NOT NULL
                    AND `batch_root_marker` = 1)
            OR (`predecessor_acceptance_id` IS NOT NULL
                    AND `successor_reason` IS NOT NULL
                    AND `batch_root_marker` IS NULL)),
    ADD UNIQUE KEY `uk_imp_arrival_batch_root`
        (`tenant_id`, `project_id`, `batch_code`, `batch_root_marker`),
    ADD UNIQUE KEY `uk_imp_arrival_predecessor`
        (`tenant_id`, `predecessor_acceptance_id`);

DROP PROCEDURE IF EXISTS `fimp002_require_initial_arrival_roots`;
