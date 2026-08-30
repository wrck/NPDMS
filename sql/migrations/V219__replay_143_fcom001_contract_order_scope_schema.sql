-- Chronologically replayed from ae1968c63af614700bd586915e37c74ef1b0152b (codex/f-cut-001-matrices), original sql/migrations/V143__fcom001_contract_order_scope_schema.sql.
-- Renumbered after current master; Feature status is not promoted by this receipt.

DROP PROCEDURE IF EXISTS `fcom001_preflight_scope_current`;
DELIMITER $$
CREATE PROCEDURE `fcom001_preflight_scope_current`()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM `com_delivery_scope`
        WHERE `deleted` = b'0'
          AND `scope_status` IN ('ACTIVE', 'CONFLICT')
          AND `effective_to` IS NULL
        GROUP BY `tenant_id`, `order_line_id`, `project_id`
        HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'F-COM-001 duplicate current ACTIVE/CONFLICT scope rows';
    END IF;
END$$
DELIMITER ;
CALL `fcom001_preflight_scope_current`();
DROP PROCEDURE IF EXISTS `fcom001_preflight_scope_current`;

CREATE TABLE `com_contract` (
    `id` bigint NOT NULL,
    `company_code` varchar(32) NOT NULL,
    `contract_no` varchar(64) NOT NULL,
    `customer_code` varchar(64) DEFAULT NULL,
    `customer_name` varchar(255) DEFAULT NULL,
    `contract_amount` decimal(20, 2) DEFAULT NULL,
    `currency_code` varchar(16) DEFAULT NULL,
    `authority_status` varchar(32) NOT NULL,
    `source_lifecycle_status` varchar(32) NOT NULL,
    `source_system` varchar(32) NOT NULL,
    `source_key` varchar(128) NOT NULL,
    `source_version` varchar(128) NOT NULL,
    `source_updated_at` datetime(3) NOT NULL,
    `synced_at` datetime(3) NOT NULL,
    `version` int NOT NULL,
    `creator` varchar(64) NOT NULL,
    `create_time` datetime(3) NOT NULL,
    `updater` varchar(64) NOT NULL,
    `update_time` datetime(3) NOT NULL,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_com_contract_business` (`tenant_id`, `company_code`, `contract_no`),
    UNIQUE KEY `uk_com_contract_source` (`tenant_id`, `source_system`, `source_key`),
    CONSTRAINT `ck_com_contract_authority` CHECK (`authority_status` IN ('PENDING_AUTHORITY', 'CONFIRMED')),
    CONSTRAINT `ck_com_contract_lifecycle` CHECK (`source_lifecycle_status` IN ('ACTIVE', 'CANCELLED', 'RETURNED')),
    CONSTRAINT `ck_com_contract_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='COM合同权威副本';

CREATE TABLE `com_sales_order` (
    `id` bigint NOT NULL,
    `company_code` varchar(32) NOT NULL,
    `order_no` varchar(64) NOT NULL,
    `order_type` varchar(32) NOT NULL,
    `customer_code` varchar(64) DEFAULT NULL,
    `customer_name` varchar(255) DEFAULT NULL,
    `order_amount` decimal(20, 2) DEFAULT NULL,
    `currency_code` varchar(16) DEFAULT NULL,
    `authority_status` varchar(32) NOT NULL,
    `source_lifecycle_status` varchar(32) NOT NULL,
    `source_system` varchar(32) NOT NULL,
    `source_key` varchar(128) NOT NULL,
    `source_version` varchar(128) NOT NULL,
    `source_updated_at` datetime(3) NOT NULL,
    `synced_at` datetime(3) NOT NULL,
    `version` int NOT NULL,
    `creator` varchar(64) NOT NULL,
    `create_time` datetime(3) NOT NULL,
    `updater` varchar(64) NOT NULL,
    `update_time` datetime(3) NOT NULL,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_com_sales_order_source` (`tenant_id`, `source_system`, `source_key`),
    CONSTRAINT `ck_com_sales_order_authority` CHECK (`authority_status` IN ('PENDING_AUTHORITY', 'CONFIRMED')),
    CONSTRAINT `ck_com_sales_order_lifecycle` CHECK (`source_lifecycle_status` IN ('ACTIVE', 'CANCELLED', 'RETURNED')),
    CONSTRAINT `ck_com_sales_order_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='COM销售订单权威副本';

CREATE TABLE `com_sales_order_contract_relation` (
    `id` bigint NOT NULL,
    `sales_order_id` bigint NOT NULL,
    `contract_id` bigint NOT NULL,
    `relation_status` varchar(16) NOT NULL,
    `source_system` varchar(32) NOT NULL,
    `source_key` varchar(128) NOT NULL,
    `source_version` varchar(64) NOT NULL,
    `source_evidence` json NOT NULL,
    `effective_from` datetime(3) NOT NULL,
    `effective_to` datetime(3) DEFAULT NULL,
    `creator` varchar(64) NOT NULL,
    `create_time` datetime(3) NOT NULL,
    `updater` varchar(64) NOT NULL,
    `update_time` datetime(3) NOT NULL,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_com_order_contract_source` (`tenant_id`, `source_system`, `source_key`),
    UNIQUE KEY `uk_com_order_contract_period` (`tenant_id`, `sales_order_id`, `contract_id`, `effective_from`),
    CONSTRAINT `ck_com_order_contract_status` CHECK (`relation_status` IN ('ACTIVE', 'ENDED')),
    CONSTRAINT `ck_com_order_contract_range` CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='COM订单合同关系';

CREATE TABLE `com_project_contract_relation` (
    `id` bigint NOT NULL,
    `project_id` bigint NOT NULL,
    `contract_id` bigint NOT NULL,
    `relation_status` varchar(16) NOT NULL,
    `effective_from` datetime(3) NOT NULL,
    `effective_to` datetime(3) DEFAULT NULL,
    `current_marker` tinyint GENERATED ALWAYS AS (
        CASE WHEN `relation_status` = 'ACTIVE' AND `effective_to` IS NULL THEN 1 ELSE NULL END
    ) STORED,
    `version` int NOT NULL,
    `creator` varchar(64) NOT NULL,
    `create_time` datetime(3) NOT NULL,
    `updater` varchar(64) NOT NULL,
    `update_time` datetime(3) NOT NULL,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_com_project_contract_period` (`tenant_id`, `project_id`, `contract_id`, `effective_from`),
    UNIQUE KEY `uk_com_project_contract_current` (`tenant_id`, `project_id`, `contract_id`, `current_marker`),
    CONSTRAINT `ck_com_project_contract_status` CHECK (`relation_status` IN ('ACTIVE', 'ENDED')),
    CONSTRAINT `ck_com_project_contract_range` CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),
    CONSTRAINT `ck_com_project_contract_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='COM项目合同关系';

CREATE TABLE `com_authority_candidate` (
    `id` bigint NOT NULL,
    `object_type` varchar(32) NOT NULL,
    `candidate_source_system` varchar(32) NOT NULL,
    `candidate_source_key` varchar(128) NOT NULL,
    `candidate_version` varchar(128) NOT NULL,
    `candidate_payload` json NOT NULL,
    `evidence_reference` json NOT NULL,
    `candidate_status` varchar(32) NOT NULL,
    `matched_owner_table` varchar(64) DEFAULT NULL,
    `matched_owner_id` bigint DEFAULT NULL,
    `matched_owner_source_version` varchar(128) DEFAULT NULL,
    `submitted_by` bigint NOT NULL,
    `submitted_at` datetime(3) NOT NULL,
    `decided_by` bigint DEFAULT NULL,
    `decided_at` datetime(3) DEFAULT NULL,
    `version` int NOT NULL,
    `creator` varchar(64) NOT NULL,
    `create_time` datetime(3) NOT NULL,
    `updater` varchar(64) NOT NULL,
    `update_time` datetime(3) NOT NULL,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_com_candidate_source` (`tenant_id`, `object_type`, `candidate_source_system`, `candidate_source_key`, `candidate_version`),
    CONSTRAINT `ck_com_candidate_source_system` CHECK (`candidate_source_system` = 'PLATFORM_MANUAL'),
    CONSTRAINT `ck_com_candidate_object_type` CHECK (`object_type` IN ('CONTRACT', 'SALES_ORDER', 'ORDER_LINE')),
    CONSTRAINT `ck_com_candidate_status` CHECK (`candidate_status` IN ('PENDING_RECONCILIATION', 'MATCHED', 'REJECTED')),
    CONSTRAINT `ck_com_candidate_decision` CHECK (
        (`candidate_status` = 'PENDING_RECONCILIATION' AND `matched_owner_table` IS NULL AND `matched_owner_id` IS NULL
            AND `matched_owner_source_version` IS NULL AND `decided_by` IS NULL AND `decided_at` IS NULL)
        OR (`candidate_status` = 'MATCHED' AND `matched_owner_table` IS NOT NULL AND `matched_owner_id` IS NOT NULL
            AND `matched_owner_source_version` IS NOT NULL AND `decided_by` IS NOT NULL AND `decided_at` IS NOT NULL)
        OR (`candidate_status` = 'REJECTED' AND `matched_owner_table` IS NULL AND `matched_owner_id` IS NULL
            AND `matched_owner_source_version` IS NULL AND `decided_by` IS NOT NULL AND `decided_at` IS NOT NULL)
    ),
    CONSTRAINT `ck_com_candidate_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='COM人工权威候选';

CREATE TABLE `com_delivery_scope_project_version` (
    `id` bigint NOT NULL,
    `project_id` bigint NOT NULL,
    `scope_version` bigint NOT NULL,
    `payload_version` int NOT NULL,
    `last_change_type` varchar(32) NOT NULL,
    `version` int NOT NULL,
    `creator` varchar(64) NOT NULL,
    `create_time` datetime(3) NOT NULL,
    `updater` varchar(64) NOT NULL,
    `update_time` datetime(3) NOT NULL,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_com_scope_project_version` (`tenant_id`, `project_id`),
    CONSTRAINT `ck_com_scope_project_scope_version` CHECK (`scope_version` >= 0),
    CONSTRAINT `ck_com_scope_project_payload_version` CHECK (`payload_version` >= 0),
    CONSTRAINT `ck_com_scope_project_row_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='COM项目范围持久水位';

ALTER TABLE `com_order_line`
    ADD COLUMN `model_code` varchar(64) DEFAULT NULL AFTER `item_code`,
    ADD COLUMN `source_lifecycle_status` varchar(32) DEFAULT NULL AFTER `quantity_status`,
    ADD CONSTRAINT `ck_com_order_line_lifecycle` CHECK (
        `source_lifecycle_status` IS NULL OR `source_lifecycle_status` IN ('ACTIVE', 'CANCELLED', 'RETURNED')
    );

ALTER TABLE `com_delivery_scope_detail`
    ADD COLUMN `unit_code` varchar(32) DEFAULT NULL AFTER `allocated_qty`,
    ADD COLUMN `product_code` varchar(64) DEFAULT NULL AFTER `unit_code`,
    ADD COLUMN `model_code` varchar(64) DEFAULT NULL AFTER `product_code`,
    ADD COLUMN `site_id` bigint DEFAULT NULL AFTER `model_code`,
    ADD COLUMN `site_location_id` bigint DEFAULT NULL AFTER `site_id`,
    ADD COLUMN `location_text` varchar(512) DEFAULT NULL AFTER `site_location_id`,
    ADD COLUMN `location_resolution_status` varchar(16) DEFAULT NULL AFTER `location_text`,
    ADD CONSTRAINT `ck_com_scope_detail_qualified` CHECK (
        `unit_code` IS NULL OR (
            (NULLIF(TRIM(`product_code`), '') IS NOT NULL OR NULLIF(TRIM(`model_code`), '') IS NOT NULL)
            AND `location_resolution_status` IN ('RESOLVED', 'UNRESOLVED')
            AND ((`location_resolution_status` = 'RESOLVED' AND `site_id` IS NOT NULL AND `site_id` > 0
                    AND `site_location_id` IS NOT NULL AND `site_location_id` > 0 AND `location_text` IS NULL)
                OR (`location_resolution_status` = 'UNRESOLVED' AND `site_id` IS NULL
                    AND `site_location_id` IS NULL AND NULLIF(TRIM(`location_text`), '') IS NOT NULL))
            AND (`serial_no` IS NULL OR `allocated_qty` = 1)
        )
    );

ALTER TABLE `com_delivery_scope`
    DROP INDEX `uk_com_scope_current`,
    DROP COLUMN `current_marker`,
    ADD COLUMN `current_marker` tinyint GENERATED ALWAYS AS (
        CASE WHEN `scope_status` IN ('ACTIVE', 'CONFLICT') AND `effective_to` IS NULL THEN 1 ELSE NULL END
    ) STORED AFTER `effective_to`,
    ADD UNIQUE KEY `uk_com_scope_current` (`tenant_id`, `order_line_id`, `project_id`, `current_marker`);
