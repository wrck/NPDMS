DROP PROCEDURE IF EXISTS `fimp002_require_provable_task5b_history`;

DELIMITER $$
CREATE PROCEDURE `fimp002_require_provable_task5b_history`()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM `imp_arrival_acceptance`
        WHERE `predecessor_acceptance_id` IS NOT NULL
        LIMIT 1
    ) OR EXISTS (
        SELECT 1
        FROM `imp_arrival_difference`
        WHERE `project_fact_version` IS NOT NULL
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'F-IMP-002 Task 5B immutable history requires evidence-based reconciliation';
    END IF;
END$$
DELIMITER ;

CALL `fimp002_require_provable_task5b_history`();

ALTER TABLE `imp_arrival_acceptance`
    ADD COLUMN `successor_reason` varchar(32) NULL DEFAULT NULL AFTER `predecessor_acceptance_id`,
    ADD CONSTRAINT `chk_imp_arrival_successor_pair`
        CHECK ((`predecessor_acceptance_id` IS NULL AND `successor_reason` IS NULL)
            OR (`predecessor_acceptance_id` IS NOT NULL AND `successor_reason` IS NOT NULL)),
    ADD CONSTRAINT `chk_imp_arrival_successor_reason`
        CHECK (`successor_reason` IS NULL OR `successor_reason` IN
            ('SUPPLEMENT', 'CORRECTION', 'DIFFERENCE_CLOSURE', 'EXEMPTION_INVALIDATION'));

ALTER TABLE `imp_arrival_difference`
    ADD COLUMN `fact_impact_type` varchar(32) NULL DEFAULT NULL AFTER `project_fact_version`,
    ADD CONSTRAINT `chk_imp_arrival_difference_fact_pair`
        CHECK ((`project_fact_version` IS NULL AND `fact_impact_type` IS NULL)
            OR (`project_fact_version` IS NOT NULL AND `fact_impact_type` IS NOT NULL)),
    ADD CONSTRAINT `chk_imp_arrival_difference_fact_impact`
        CHECK (`fact_impact_type` IS NULL OR `fact_impact_type` IN
            ('CORRECTION', 'REOPEN', 'EXEMPTION_INVALIDATION'));

DROP PROCEDURE IF EXISTS `fimp002_require_provable_task5b_history`;
