-- F-COM-001 / COM-01@V1 前向切换（Requirement: COM-01@V1）。
-- APPLICATION_WRITE_STOP_REQUIRED：应用停写必须覆盖预检、装载、对账和发布全窗口；
-- Flyway独占锁不能替代业务停写。失败后先repair，再在停写窗口重跑本迁移。

DROP PROCEDURE IF EXISTS `fcom001_v124_forward`;

DELIMITER $$

CREATE PROCEDURE `fcom001_v124_forward`()
main: BEGIN
    DECLARE v_old_order_line INT DEFAULT 0;
    DECLARE v_old_scope INT DEFAULT 0;
    DECLARE v_old_detail INT DEFAULT 0;
    DECLARE v_new_targets INT DEFAULT 0;
    DECLARE v_archives INT DEFAULT 0;
    DECLARE v_shadows INT DEFAULT 0;
    DECLARE v_count BIGINT DEFAULT 0;
    DECLARE v_seed_line_qty DECIMAL(24, 6) DEFAULT 0;
    DECLARE v_seed_scope_qty DECIMAL(24, 6) DEFAULT 0;
    DECLARE v_seed_detail_qty DECIMAL(24, 6) DEFAULT 0;
    DECLARE v_line_count BIGINT DEFAULT 0;
    DECLARE v_line_min_id BIGINT DEFAULT NULL;
    DECLARE v_line_max_id BIGINT DEFAULT NULL;
    DECLARE v_line_max_version INT DEFAULT NULL;
    DECLARE v_line_max_update_time DATETIME(3) DEFAULT NULL;
    DECLARE v_scope_count BIGINT DEFAULT 0;
    DECLARE v_scope_min_id BIGINT DEFAULT NULL;
    DECLARE v_scope_max_id BIGINT DEFAULT NULL;
    DECLARE v_scope_max_version INT DEFAULT NULL;
    DECLARE v_scope_max_update_time DATETIME(3) DEFAULT NULL;
    DECLARE v_detail_count BIGINT DEFAULT 0;
    DECLARE v_detail_min_id BIGINT DEFAULT NULL;
    DECLARE v_detail_max_id BIGINT DEFAULT NULL;
    DECLARE v_detail_max_version INT DEFAULT NULL;
    DECLARE v_detail_max_update_time DATETIME(3) DEFAULT NULL;
    DECLARE v_check_min_id BIGINT DEFAULT NULL;
    DECLARE v_check_max_id BIGINT DEFAULT NULL;
    DECLARE v_check_max_version INT DEFAULT NULL;
    DECLARE v_check_max_update_time DATETIME(3) DEFAULT NULL;

    SELECT COUNT(*) INTO v_old_order_line FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'com_order_line';
    SELECT COUNT(*) INTO v_old_scope FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'com_delivery_scope';
    SELECT COUNT(*) INTO v_old_detail FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'com_delivery_scope_detail';
    SELECT COUNT(*) INTO v_new_targets FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name IN (
       'com_contract', 'com_sales_order', 'com_sales_order_line',
       'com_order_contract_relation', 'com_project_contract_relation',
       'acc_acceptance_scope_binding');
    SELECT COUNT(*) INTO v_archives FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name IN (
       'fcom001_v70_com_order_line', 'fcom001_v70_com_delivery_scope',
       'fcom001_v70_com_delivery_scope_detail');
    SELECT COUNT(*) INTO v_shadows FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name LIKE 'fcom001_shadow\_%' ESCAPE '\\';

    -- 原子发布已完成但Flyway元数据未写入时只做只读幂等复核。
    IF v_old_order_line = 0 AND v_old_scope = 1 AND v_old_detail = 1
       AND v_new_targets = 6 AND v_archives = 3 AND v_shadows = 0 THEN
        SELECT
          ((SELECT COUNT(*) FROM `fcom001_v70_com_order_line`) <> 4)
          + ((SELECT COUNT(*) FROM `fcom001_v70_com_delivery_scope`) <> 2)
          + ((SELECT COUNT(*) FROM `fcom001_v70_com_delivery_scope_detail`) <> 4)
          + ((SELECT COUNT(*) FROM `com_sales_order`) <> 1)
          + ((SELECT COUNT(*) FROM `com_sales_order_line`) <> 4)
          + ((SELECT COUNT(*) FROM `com_delivery_scope`) <> 2)
          + ((SELECT COUNT(*) FROM `com_delivery_scope_detail`) <> 4)
          + ((SELECT COUNT(*) FROM `com_order_contract_relation`) <> 0)
          + ((SELECT COUNT(*) FROM `com_project_contract_relation`) <> 0)
          + ((SELECT COUNT(*) FROM `acc_acceptance_scope_binding`) <> 0)
          INTO v_count;
        IF v_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_STATE_PUBLISHED_REPLAY_INVALID';
        END IF;
        SELECT
          (SELECT COUNT(*) FROM `com_sales_order_line` l
            LEFT JOIN `com_sales_order` o ON o.tenant_id = l.tenant_id AND o.id = l.order_id
           WHERE o.id IS NULL)
          + (SELECT COUNT(*) FROM `com_delivery_scope` s
            LEFT JOIN `com_sales_order_line` l
              ON l.tenant_id = s.tenant_id AND l.id = s.order_line_id
           WHERE l.id IS NULL)
          + (SELECT COUNT(*) FROM `com_delivery_scope_detail` d
            LEFT JOIN `com_delivery_scope` s
              ON s.tenant_id = d.tenant_id AND s.id = d.delivery_scope_id
           WHERE s.id IS NULL)
          INTO v_count;
        IF v_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_STATE_PUBLISHED_REPLAY_INVALID';
        END IF;
        SELECT 'FCOM001_STATE_PUBLISHED_REPLAY' AS migration_state;
        LEAVE main;
    END IF;

    -- 原V123三表仍完整存在时允许清理影子并确定性重试。
    IF NOT (v_old_order_line = 1 AND v_old_scope = 1 AND v_old_detail = 1
            AND v_new_targets = 0 AND v_archives = 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_STATE_MIXED_RESTORE_SNAPSHOT';
    END IF;
    SELECT 'FCOM001_STATE_FRESH_V123' AS migration_state;

    -- 冻结输入水位。当前受支持V123必须恰好包含完整且未改写的V72夹具。
    SELECT COUNT(*), MIN(id), MAX(id), COALESCE(MAX(version), 0), MAX(update_time)
      INTO v_line_count, v_line_min_id, v_line_max_id, v_line_max_version, v_line_max_update_time
      FROM `com_order_line`;
    SELECT COUNT(*), MIN(id), MAX(id), COALESCE(MAX(version), 0), MAX(update_time)
      INTO v_scope_count, v_scope_min_id, v_scope_max_id, v_scope_max_version, v_scope_max_update_time
      FROM `com_delivery_scope`;
    SELECT COUNT(*), MIN(id), MAX(id), COALESCE(MAX(version), 0), MAX(update_time)
      INTO v_detail_count, v_detail_min_id, v_detail_max_id, v_detail_max_version, v_detail_max_update_time
      FROM `com_delivery_scope_detail`;
    SELECT COUNT(*), COALESCE(SUM(quantity), 0)
      INTO v_count, v_seed_line_qty
      FROM `com_order_line`
     WHERE id IN (992002300001, 992002300002, 992002300003, 992002300004);
    IF v_count <> 4 OR v_seed_line_qty <> 120.000000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_V72_SEED_PARTIAL_OR_TAMPERED';
    END IF;
    SELECT COUNT(*) INTO v_count
      FROM `com_order_line`
     WHERE id IN (992002300001, 992002300002, 992002300003, 992002300004)
       AND tenant_id = 0 AND creator = 'seed' AND updater = 'seed'
       AND source_system = 'SEED' AND source_key LIKE 'FPROJ002-V18-%'
       AND order_id = 992002399001 AND deleted = b'0';
    IF v_count <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_V72_SEED_PARTIAL_OR_TAMPERED';
    END IF;
    SELECT COUNT(*), COALESCE(SUM(allocated_qty), 0)
      INTO v_count, v_seed_scope_qty
      FROM `com_delivery_scope`
     WHERE id IN (992002310001, 992002310004)
       AND tenant_id = 0 AND project_id = 992002000000
       AND order_line_id IN (992002300001, 992002300004)
       AND creator = 'seed' AND updater = 'seed'
       AND source_evidence LIKE 'FPROJ002-V18-%' AND deleted = b'0';
    IF v_count <> 2 OR v_seed_scope_qty <> 110.000000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_V72_SEED_PARTIAL_OR_TAMPERED';
    END IF;
    SELECT COUNT(*), COALESCE(SUM(allocated_qty), 0)
      INTO v_count, v_seed_detail_qty
      FROM `com_delivery_scope_detail`
     WHERE id IN (992002320001, 992002320002, 992002320003, 992002320004)
       AND tenant_id = 0 AND creator = 'seed' AND updater = 'seed'
       AND delivery_scope_id IN (992002310001, 992002310004) AND deleted = b'0';
    IF v_count <> 4 OR v_seed_detail_qty <> 110.000000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_V72_SEED_PARTIAL_OR_TAMPERED';
    END IF;
    SELECT COUNT(*) INTO v_count
      FROM `com_delivery_scope_detail`
     WHERE (id = 992002320001 AND delivery_scope_id <> 992002310001)
        OR (id = 992002320002 AND delivery_scope_id <> 992002310001)
        OR (id = 992002320003 AND delivery_scope_id <> 992002310001)
        OR (id = 992002320004 AND delivery_scope_id <> 992002310004);
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_V72_SEED_PARTIAL_OR_TAMPERED';
    END IF;

    -- 普通V70业务行没有当前目标父订单、主体和办事处Owner闭包，禁止跳过或推断。
    SELECT
      (SELECT COUNT(*) FROM `com_order_line`
        WHERE id NOT IN (992002300001, 992002300002, 992002300003, 992002300004))
      + (SELECT COUNT(*) FROM `com_delivery_scope`
        WHERE id NOT IN (992002310001, 992002310004))
      + (SELECT COUNT(*) FROM `com_delivery_scope_detail`
        WHERE id NOT IN (992002320001, 992002320002, 992002320003, 992002320004))
      INTO v_count;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_NON_SEED_V70_OWNER_FACTS_UNAVAILABLE';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM `proj_project` p
      JOIN `system_dept` d ON d.id = p.department_id
       AND d.tenant_id = p.tenant_id AND d.deleted = b'0'
     WHERE p.id = 992002000000 AND p.tenant_id = 0 AND p.deleted = b'0'
       AND p.project_code LIKE 'FPROJ002-V18-%'
       AND p.company_code = 'DPTECH-DEMO'
       AND p.department_code = 'OFFICE-HZ-DEMO'
       AND CAST(d.code AS BINARY) = CAST(p.department_code AS BINARY) AND d.status = 0
       AND d.name IS NOT NULL AND d.version IS NOT NULL;
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_V72_SEED_OWNER_FACT_UNAVAILABLE';
    END IF;

    -- 子到父清理仅处理固定影子名；正式V123表和归档表从不删除。
    DROP TABLE IF EXISTS `fcom001_shadow_acc_acceptance_scope_binding`;
    DROP TABLE IF EXISTS `fcom001_shadow_com_delivery_scope_detail`;
    DROP TABLE IF EXISTS `fcom001_shadow_com_delivery_scope`;
    DROP TABLE IF EXISTS `fcom001_shadow_com_project_contract_relation`;
    DROP TABLE IF EXISTS `fcom001_shadow_com_order_contract_relation`;
    DROP TABLE IF EXISTS `fcom001_shadow_com_sales_order_line`;
    DROP TABLE IF EXISTS `fcom001_shadow_com_sales_order`;
    DROP TABLE IF EXISTS `fcom001_shadow_com_contract`;

    CREATE TABLE `fcom001_shadow_com_contract` (
      `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL,
      `company_id` BIGINT NULL, `company_code` VARCHAR(64) NOT NULL,
      `company_name` VARCHAR(255) NULL, `contract_no` VARCHAR(64) NOT NULL,
      `master_source_system` VARCHAR(32) NOT NULL,
      `master_source_record_key` VARCHAR(128) COLLATE utf8mb4_0900_bin NULL,
      `master_source_version` VARCHAR(64) COLLATE utf8mb4_0900_bin NULL,
      `contract_type` VARCHAR(32) NULL, `customer_id` BIGINT NULL,
      `customer_code` VARCHAR(64) NULL, `customer_name` VARCHAR(512) NULL,
      `contract_name` VARCHAR(512) NULL, `currency_code` VARCHAR(32) NULL,
      `effective_date` DATE NULL, `expiry_date` DATE NULL,
      `source_sync_time` DATETIME(3) NULL, `source_updated_at` DATETIME(3) NULL,
      `status` VARCHAR(32) NOT NULL, `version` INT UNSIGNED NOT NULL DEFAULT 0,
      `creator` VARCHAR(64) NOT NULL DEFAULT '',
      `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
      `updater` VARCHAR(64) NOT NULL DEFAULT '',
      `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
      `deleted` TINYINT NOT NULL DEFAULT 0,
      PRIMARY KEY (`id`), UNIQUE KEY `uk_contract_tenant_row` (`tenant_id`, `id`),
      UNIQUE KEY `uk_contract_business` (`tenant_id`, `company_code`, `contract_no`),
      UNIQUE KEY `uk_contract_master_source` (`tenant_id`, `master_source_system`, `master_source_record_key`),
      KEY `idx_contract_company` (`tenant_id`, `company_id`, `status`, `contract_no`),
      KEY `idx_contract_customer` (`tenant_id`, `customer_id`, `status`),
      KEY `idx_contract_no` (`tenant_id`, `contract_no`, `company_code`),
      CONSTRAINT `chk_contract_dates` CHECK (`expiry_date` IS NULL OR `effective_date` IS NULL OR `expiry_date` >= `effective_date`),
      CONSTRAINT `chk_contract_deleted` CHECK (`deleted` IN (0, 1))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同主档';

    CREATE TABLE `fcom001_shadow_com_sales_order` (
      `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL,
      `source_system` VARCHAR(32) NOT NULL,
      `source_record_key` VARCHAR(128) COLLATE utf8mb4_0900_bin NULL,
      `source_version` VARCHAR(64) COLLATE utf8mb4_0900_bin NULL,
      `company_id` BIGINT NULL, `company_code` VARCHAR(64) NOT NULL,
      `company_name` VARCHAR(255) NULL, `order_type` VARCHAR(32) NOT NULL,
      `order_no` VARCHAR(64) NOT NULL, `sales_type` VARCHAR(32) NULL,
      `customer_id` BIGINT NULL, `customer_code` VARCHAR(64) NULL,
      `customer_name` VARCHAR(512) NULL, `source_project_name` VARCHAR(512) NULL,
      `order_comment` VARCHAR(2048) NULL, `order_create_time` DATETIME(3) NULL,
      `customer_required_time` DATETIME(3) NULL, `source_sync_time` DATETIME(3) NULL,
      `source_updated_at` DATETIME(3) NULL, `status` VARCHAR(32) NOT NULL,
      `version` INT UNSIGNED NOT NULL DEFAULT 0,
      `creator` VARCHAR(64) NOT NULL DEFAULT '',
      `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
      `updater` VARCHAR(64) NOT NULL DEFAULT '',
      `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
      `deleted` TINYINT NOT NULL DEFAULT 0,
      PRIMARY KEY (`id`), UNIQUE KEY `uk_sales_order_tenant_row` (`tenant_id`, `id`),
      UNIQUE KEY `uk_sales_order_business` (`tenant_id`, `source_system`, `company_code`, `order_type`, `order_no`),
      UNIQUE KEY `uk_sales_order_source` (`tenant_id`, `source_system`, `source_record_key`),
      KEY `idx_sales_order_company` (`tenant_id`, `company_id`, `status`, `order_no`),
      KEY `idx_sales_order_customer` (`tenant_id`, `customer_code`, `status`),
      KEY `idx_sales_order_no` (`tenant_id`, `order_no`),
      KEY `idx_sales_order_time` (`tenant_id`, `order_create_time`, `status`),
      CONSTRAINT `chk_sales_order_deleted` CHECK (`deleted` IN (0, 1))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ERP销售订单主档';

    CREATE TABLE `fcom001_shadow_com_sales_order_line` (
      `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL, `order_id` BIGINT NOT NULL,
      `source_system` VARCHAR(32) NOT NULL,
      `source_record_key` VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL,
      `source_version` VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
      `company_id` BIGINT NULL, `company_code` VARCHAR(64) NOT NULL,
      `company_name` VARCHAR(255) NULL, `order_type` VARCHAR(32) NOT NULL,
      `order_no` VARCHAR(64) NOT NULL, `line_no` VARCHAR(32) NOT NULL,
      `line_type` VARCHAR(32) NULL, `item_code` VARCHAR(64) NULL,
      `item_desc` VARCHAR(512) NULL, `product_id` BIGINT NULL,
      `bundle_code` VARCHAR(64) NULL, `profit_center` VARCHAR(64) NULL,
      `real_execution_no` VARCHAR(64) NULL, `warranty_month` INT NULL,
      `customer_id` BIGINT NULL, `customer_code` VARCHAR(64) NULL,
      `customer_name` VARCHAR(512) NULL,
      `order_qty` DECIMAL(18, 6) NULL, `open_qty` DECIMAL(18, 6) NULL,
      `delivered_qty` DECIMAL(18, 6) NULL, `unit_code` VARCHAR(32) NOT NULL,
      `unit_scale` TINYINT UNSIGNED NOT NULL, `quantity_status` VARCHAR(32) NOT NULL,
      `source_sync_time` DATETIME(3) NULL, `source_updated_at` DATETIME(3) NULL,
      `status` VARCHAR(32) NOT NULL, `version` INT UNSIGNED NOT NULL DEFAULT 0,
      `creator` VARCHAR(64) NOT NULL DEFAULT '',
      `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
      `updater` VARCHAR(64) NOT NULL DEFAULT '',
      `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
      `deleted` TINYINT NOT NULL DEFAULT 0,
      PRIMARY KEY (`id`), UNIQUE KEY `uk_sales_order_line_tenant_row` (`tenant_id`, `id`),
      UNIQUE KEY `uk_sales_order_line` (`tenant_id`, `order_id`, `line_no`),
      UNIQUE KEY `uk_sales_order_line_source` (`tenant_id`, `source_system`, `source_record_key`),
      KEY `idx_sales_order_line_business` (`tenant_id`, `source_system`, `company_code`, `order_type`, `order_no`, `line_no`),
      KEY `idx_sales_order_line_customer` (`tenant_id`, `customer_code`, `status`, `id`),
      KEY `idx_sales_order_line_item` (`tenant_id`, `item_code`),
      KEY `idx_sales_order_line_profit` (`tenant_id`, `profit_center`, `order_id`),
      CONSTRAINT `fk_sales_order_line_order` FOREIGN KEY (`tenant_id`, `order_id`)
        REFERENCES `fcom001_shadow_com_sales_order` (`tenant_id`, `id`),
      CONSTRAINT `chk_sales_order_line_deleted` CHECK (`deleted` IN (0, 1)),
      CONSTRAINT `chk_sales_order_line_unit_scale` CHECK (`unit_scale` BETWEEN 0 AND 6),
      CONSTRAINT `chk_sales_order_line_quantity_scale` CHECK (
        (`order_qty` IS NULL OR ROUND(`order_qty`, `unit_scale`) = `order_qty`)
        AND (`open_qty` IS NULL OR ROUND(`open_qty`, `unit_scale`) = `open_qty`)
        AND (`delivered_qty` IS NULL OR ROUND(`delivered_qty`, `unit_scale`) = `delivered_qty`)),
      CONSTRAINT `chk_sales_order_line_authority` CHECK (`quantity_status` <> 'CONFIRMED' OR `order_qty` IS NOT NULL)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ERP销售订单行及数量快照';

    CREATE TABLE `fcom001_shadow_com_order_contract_relation` (
      `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL,
      `order_id` BIGINT NOT NULL, `contract_id` BIGINT NOT NULL,
      `relation_role` VARCHAR(32) NOT NULL DEFAULT 'RELATED',
      `relation_source` VARCHAR(32) NOT NULL,
      `creator` VARCHAR(64) NOT NULL DEFAULT '',
      `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
      `updater` VARCHAR(64) NOT NULL DEFAULT '',
      `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
      `deleted` TINYINT NOT NULL DEFAULT 0,
      PRIMARY KEY (`id`), UNIQUE KEY `uk_order_contract_rel_tenant_row` (`tenant_id`, `id`),
      UNIQUE KEY `uk_order_contract` (`tenant_id`, `order_id`, `contract_id`, `relation_role`),
      KEY `idx_order_contract_reverse` (`tenant_id`, `contract_id`, `order_id`),
      CONSTRAINT `fk_order_contract_order` FOREIGN KEY (`tenant_id`, `order_id`)
        REFERENCES `fcom001_shadow_com_sales_order` (`tenant_id`, `id`),
      CONSTRAINT `fk_order_contract_contract` FOREIGN KEY (`tenant_id`, `contract_id`)
        REFERENCES `fcom001_shadow_com_contract` (`tenant_id`, `id`),
      CONSTRAINT `chk_order_contract_deleted` CHECK (`deleted` IN (0, 1))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售订单与合同关系';

    CREATE TABLE `fcom001_shadow_com_project_contract_relation` (
      `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL,
      `project_id` BIGINT NOT NULL, `contract_id` BIGINT NOT NULL,
      `relation_role` VARCHAR(32) NOT NULL DEFAULT 'RELATED',
      `source_system` VARCHAR(32) NOT NULL, `source_table` VARCHAR(64) NULL,
      `source_record_key` VARCHAR(128) NULL, `effective_from` DATETIME(3) NULL,
      `effective_to` DATETIME(3) NULL, `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
      `version` INT UNSIGNED NOT NULL DEFAULT 0,
      `creator` VARCHAR(64) NOT NULL DEFAULT '',
      `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
      `updater` VARCHAR(64) NOT NULL DEFAULT '',
      `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
      `deleted` TINYINT NOT NULL DEFAULT 0,
      PRIMARY KEY (`id`), UNIQUE KEY `uk_project_contract_rel_tenant_row` (`tenant_id`, `id`),
      UNIQUE KEY `uk_project_contract` (`tenant_id`, `project_id`, `contract_id`, `relation_role`),
      KEY `idx_project_contract_reverse` (`tenant_id`, `contract_id`, `project_id`),
      CONSTRAINT `fk_project_contract_contract` FOREIGN KEY (`tenant_id`, `contract_id`)
        REFERENCES `fcom001_shadow_com_contract` (`tenant_id`, `id`),
      CONSTRAINT `chk_project_contract_dates` CHECK (`effective_to` IS NULL OR `effective_from` IS NULL OR `effective_to` >= `effective_from`),
      CONSTRAINT `chk_project_contract_deleted` CHECK (`deleted` IN (0, 1))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目与合同直接关系';

    CREATE TABLE `fcom001_shadow_com_delivery_scope` (
      `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL,
      `project_id` BIGINT NOT NULL, `project_code` VARCHAR(64) NOT NULL,
      `project_name` VARCHAR(255) NULL, `project_company_code` VARCHAR(64) NULL,
      `project_company_name` VARCHAR(255) NULL, `project_department_code` VARCHAR(64) NULL,
      `project_department_name` VARCHAR(255) NULL, `project_customer_code` VARCHAR(64) NULL,
      `project_customer_name` VARCHAR(255) NULL,
      `project_manager_employee_no` VARCHAR(64) NULL, `project_manager_name` VARCHAR(128) NULL,
      `order_line_id` BIGINT NOT NULL,
      `current_order_line_id` BIGINT GENERATED ALWAYS AS
        (CASE WHEN `deleted` = 0 AND `effective_to` IS NULL THEN `order_line_id` ELSE NULL END) STORED,
      `order_source_system` VARCHAR(32) NOT NULL, `order_company_code` VARCHAR(64) NOT NULL,
      `order_company_name` VARCHAR(255) NULL, `order_type` VARCHAR(32) NOT NULL,
      `order_no` VARCHAR(64) NOT NULL, `line_no` VARCHAR(32) NOT NULL,
      `item_code` VARCHAR(64) NULL, `item_desc` VARCHAR(512) NULL,
      `allocated_qty` DECIMAL(18, 6) NOT NULL, `scope_status` VARCHAR(32) NOT NULL,
      `allocation_version` BIGINT NOT NULL, `allocation_source` VARCHAR(32) NOT NULL,
      `change_reason` VARCHAR(500) NULL,
      `office_department_id` BIGINT NOT NULL, `office_department_code` VARCHAR(64) NOT NULL,
      `office_department_name` VARCHAR(255) NOT NULL,
      `office_department_version` INT UNSIGNED NOT NULL,
      `source_evidence` VARCHAR(255) NULL, `effective_from` DATETIME(3) NOT NULL,
      `effective_to` DATETIME(3) NULL, `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
      `version` INT UNSIGNED NOT NULL DEFAULT 0,
      `creator` VARCHAR(64) NOT NULL DEFAULT '',
      `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
      `updater` VARCHAR(64) NOT NULL DEFAULT '',
      `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
      `deleted` TINYINT NOT NULL DEFAULT 0,
      PRIMARY KEY (`id`), UNIQUE KEY `uk_project_order_line_scope_tenant_row` (`tenant_id`, `id`),
      UNIQUE KEY `uk_scope_version` (`tenant_id`, `order_line_id`, `project_id`, `allocation_version`),
      UNIQUE KEY `uk_scope_current` (`tenant_id`, `project_id`, `current_order_line_id`),
      KEY `idx_scope_order_line` (`tenant_id`, `order_line_id`, `scope_status`, `project_id`),
      KEY `idx_scope_project` (`tenant_id`, `project_id`, `scope_status`, `order_line_id`),
      KEY `idx_scope_item` (`tenant_id`, `item_code`, `scope_status`, `project_id`),
      KEY `idx_scope_acceptance_lock` (`tenant_id`, `project_id`, `effective_to`, `id`),
      CONSTRAINT `fk_scope_order_line` FOREIGN KEY (`tenant_id`, `order_line_id`)
        REFERENCES `fcom001_shadow_com_sales_order_line` (`tenant_id`, `id`),
      CONSTRAINT `chk_scope_quantity` CHECK (`allocated_qty` > 0),
      CONSTRAINT `chk_scope_dates` CHECK (`effective_to` IS NULL OR `effective_to` >= `effective_from`),
      CONSTRAINT `chk_scope_deleted` CHECK (`deleted` IN (0, 1))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目对ERP订单行的权威实施范围';

    CREATE TABLE `fcom001_shadow_com_delivery_scope_detail` (
      `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL,
      `delivery_scope_id` BIGINT NOT NULL, `detail_sequence` INT UNSIGNED NOT NULL,
      `serial_no` VARCHAR(128) NULL, `product_code` VARCHAR(64) NULL,
      `product_name` VARCHAR(255) NULL, `device_type_code` VARCHAR(64) NULL,
      `device_type_name` VARCHAR(255) NULL, `delivery_batch_no` VARCHAR(64) NULL,
      `source_record_key` VARCHAR(128) NULL, `allocated_qty` DECIMAL(18, 6) NOT NULL,
      `detail_status` VARCHAR(32) NOT NULL, `source_snapshot` JSON NULL,
      `remark` VARCHAR(500) NULL, `version` INT UNSIGNED NOT NULL DEFAULT 0,
      `creator` VARCHAR(64) NOT NULL DEFAULT '',
      `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
      `updater` VARCHAR(64) NOT NULL DEFAULT '',
      `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
      `deleted` TINYINT NOT NULL DEFAULT 0,
      PRIMARY KEY (`id`), UNIQUE KEY `uk_delivery_scope_detail_tenant_row` (`tenant_id`, `id`),
      UNIQUE KEY `uk_delivery_scope_detail_sequence` (`tenant_id`, `delivery_scope_id`, `detail_sequence`),
      KEY `idx_delivery_scope_detail_product` (`tenant_id`, `product_code`, `device_type_code`, `delivery_scope_id`),
      KEY `idx_delivery_scope_detail_serial` (`tenant_id`, `serial_no`, `delivery_scope_id`),
      CONSTRAINT `fk_delivery_scope_detail_scope` FOREIGN KEY (`tenant_id`, `delivery_scope_id`)
        REFERENCES `fcom001_shadow_com_delivery_scope` (`tenant_id`, `id`),
      CONSTRAINT `chk_delivery_scope_detail_quantity` CHECK (`allocated_qty` > 0),
      CONSTRAINT `chk_delivery_scope_detail_subject` CHECK (
        `serial_no` IS NOT NULL OR `product_code` IS NOT NULL OR `device_type_code` IS NOT NULL),
      CONSTRAINT `chk_delivery_scope_detail_deleted` CHECK (`deleted` IN (0, 1))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交付范围主体明细';

    CREATE TABLE `fcom001_shadow_acc_acceptance_scope_binding` (
      `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL, `project_id` BIGINT NOT NULL,
      `project_stage_snapshot_id` BIGINT NOT NULL, `delivery_scope_id` BIGINT NOT NULL,
      `scope_allocation_version` BIGINT NOT NULL, `binding_trigger` VARCHAR(32) NOT NULL,
      `binding_status` VARCHAR(32) NOT NULL, `effective_from` DATETIME(3) NOT NULL,
      `effective_to` DATETIME(3) NULL, `acceptance_fact_version` INT UNSIGNED NOT NULL DEFAULT 1,
      `version` INT UNSIGNED NOT NULL DEFAULT 0,
      `creator` VARCHAR(64) NOT NULL DEFAULT '',
      `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
      `updater` VARCHAR(64) NOT NULL DEFAULT '',
      `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
      `deleted` TINYINT NOT NULL DEFAULT 0,
      PRIMARY KEY (`id`),
      UNIQUE KEY `uk_acceptance_scope_binding` (`tenant_id`, `project_id`, `project_stage_snapshot_id`, `delivery_scope_id`, `scope_allocation_version`),
      KEY `idx_acceptance_scope_current` (`tenant_id`, `delivery_scope_id`, `effective_to`, `binding_status`),
      CONSTRAINT `chk_acceptance_scope_trigger` CHECK (`binding_trigger` IN ('PROJECT_STAGE_ENTRY', 'SCOPE_VERSION_EFFECTIVE')),
      CONSTRAINT `chk_acceptance_scope_status` CHECK (`binding_status` = 'LOCKED'),
      CONSTRAINT `chk_acceptance_scope_effective` CHECK (`effective_to` IS NULL),
      CONSTRAINT `chk_acceptance_scope_deleted` CHECK (`deleted` IN (0, 1))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ACC验收范围锁定事实';

    -- 精确V72夹具重建：所有常量仅属于该固定闭包，不构成普通ERP/产品/办事处事实。
    INSERT INTO `fcom001_shadow_com_sales_order` (
      id, tenant_id, source_system, source_record_key, source_version,
      company_id, company_code, company_name, order_type, order_no,
      source_project_name, source_sync_time, source_updated_at, status,
      version, creator, create_time, updater, update_time, deleted)
    SELECT 992002399001, p.tenant_id, 'SEED', 'FPROJ002-V18-ORDER', '1',
      p.company_id, p.company_code, p.company_name, 'SEED', 'FPROJ002-V18-ORDER',
      p.project_name, NOW(3), NOW(3), 'ENABLED', 0, 'seed', NOW(3), 'seed', NOW(3), 0
      FROM `proj_project` p
     WHERE p.tenant_id = 0 AND p.id = 992002000000 AND p.deleted = b'0';

    INSERT INTO `fcom001_shadow_com_sales_order_line` (
      id, tenant_id, order_id, source_system, source_record_key, source_version,
      company_id, company_code, company_name, order_type, order_no, line_no,
      item_code, customer_id, customer_code, customer_name, order_qty, open_qty,
      delivered_qty, unit_code, unit_scale, quantity_status, source_sync_time,
      source_updated_at, status, version, creator, create_time, updater, update_time, deleted)
    SELECT l.id, l.tenant_id, l.order_id, l.source_system, l.source_key, l.source_version,
      o.company_id, o.company_code, o.company_name, o.order_type, o.order_no, l.line_code,
      l.item_code, o.customer_id, o.customer_code, o.customer_name, l.quantity, NULL,
      NULL, l.unit_code, 0, l.quantity_status, l.synced_at, l.source_updated_at,
      'ENABLED', l.version, l.creator, l.create_time, l.updater, l.update_time, l.deleted
      FROM `com_order_line` l
      JOIN `fcom001_shadow_com_sales_order` o
        ON o.tenant_id = l.tenant_id AND o.id = l.order_id
     WHERE l.id IN (992002300001, 992002300002, 992002300003, 992002300004);

    INSERT INTO `fcom001_shadow_com_delivery_scope` (
      id, tenant_id, project_id, project_code, project_name,
      project_company_code, project_company_name, project_department_code,
      project_department_name, project_customer_code, project_customer_name,
      order_line_id, order_source_system, order_company_code, order_company_name,
      order_type, order_no, line_no, item_code, allocated_qty, scope_status,
      allocation_version, allocation_source, office_department_id,
      office_department_code, office_department_name, office_department_version,
      source_evidence, effective_from, effective_to, status, version,
      creator, create_time, updater, update_time, deleted)
    SELECT s.id, s.tenant_id, s.project_id, p.project_code, p.project_name,
      p.company_code, p.company_name, p.department_code, p.department_name,
      p.customer_code, p.customer_name, s.order_line_id, l.source_system,
      l.company_code, l.company_name, l.order_type, l.order_no, l.line_no,
      l.item_code, s.allocated_qty, s.scope_status, s.allocation_version,
      'LEGACY', d.id, d.code, d.name, d.version, s.source_evidence,
      s.effective_from, s.effective_to, 'ENABLED', s.version,
      s.creator, s.create_time, s.updater, s.update_time, s.deleted
      FROM `com_delivery_scope` s
      JOIN `proj_project` p ON p.tenant_id = s.tenant_id AND p.id = s.project_id AND p.deleted = b'0'
      JOIN `system_dept` d ON d.tenant_id = p.tenant_id AND d.id = p.department_id
       AND CAST(d.code AS BINARY) = CAST(p.department_code AS BINARY)
       AND d.deleted = b'0' AND d.status = 0
      JOIN `fcom001_shadow_com_sales_order_line` l
        ON l.tenant_id = s.tenant_id AND l.id = s.order_line_id
     WHERE s.id IN (992002310001, 992002310004);

    INSERT INTO `fcom001_shadow_com_delivery_scope_detail` (
      id, tenant_id, delivery_scope_id, detail_sequence, serial_no,
      product_code, device_type_code, source_record_key, allocated_qty,
      detail_status, source_snapshot, version, creator, create_time,
      updater, update_time, deleted)
    WITH numbered AS (
      SELECT d.*, ROW_NUMBER() OVER (
        PARTITION BY d.tenant_id, d.delivery_scope_id ORDER BY d.id) AS rn
        FROM `com_delivery_scope_detail` d
       WHERE d.id IN (992002320001, 992002320002, 992002320003, 992002320004)
    )
    SELECT n.id, n.tenant_id, n.delivery_scope_id, n.rn, n.serial_no,
      NULL,
      CASE n.id
        WHEN 992002320002 THEN 'FPROJ002-SEED-PARTIAL'
        WHEN 992002320003 THEN 'FPROJ002-SEED-FALLBACK'
        WHEN 992002320004 THEN 'FPROJ002-SEED-INACTIVE'
        ELSE NULL END,
      CONCAT('FPROJ002-V18-DETAIL-', n.id), n.allocated_qty,
      n.detail_status, n.source_snapshot, n.version, n.creator, n.create_time,
      n.updater, n.update_time, n.deleted
      FROM numbered n;

    -- 装载后全量对账：分类行数、ID、父子闭包、数量、区间、版本和唯一键。
    SELECT COUNT(*) INTO v_count FROM `fcom001_shadow_com_sales_order_line`;
    IF v_count <> 4 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_RECONCILIATION_FAILED'; END IF;
    SELECT COUNT(*) INTO v_count FROM `fcom001_shadow_com_delivery_scope`;
    IF v_count <> 2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_RECONCILIATION_FAILED'; END IF;
    SELECT COUNT(*) INTO v_count FROM `fcom001_shadow_com_delivery_scope_detail`;
    IF v_count <> 4 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_RECONCILIATION_FAILED'; END IF;
    SELECT COUNT(*) INTO v_count
      FROM `fcom001_shadow_com_sales_order_line` l
      LEFT JOIN `fcom001_shadow_com_sales_order` o
        ON o.tenant_id = l.tenant_id AND o.id = l.order_id
     WHERE o.id IS NULL;
    IF v_count <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_RECONCILIATION_FAILED'; END IF;
    SELECT COUNT(*) INTO v_count
      FROM `fcom001_shadow_com_delivery_scope` s
      LEFT JOIN `fcom001_shadow_com_sales_order_line` l
        ON l.tenant_id = s.tenant_id AND l.id = s.order_line_id
     WHERE l.id IS NULL;
    IF v_count <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_RECONCILIATION_FAILED'; END IF;
    SELECT COUNT(*) INTO v_count
      FROM `fcom001_shadow_com_delivery_scope_detail` d
      LEFT JOIN `fcom001_shadow_com_delivery_scope` s
        ON s.tenant_id = d.tenant_id AND s.id = d.delivery_scope_id
     WHERE s.id IS NULL;
    IF v_count <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_RECONCILIATION_FAILED'; END IF;
    SELECT COUNT(*) INTO v_count
      FROM `com_delivery_scope` old
      JOIN `fcom001_shadow_com_delivery_scope` fresh ON fresh.id = old.id
     WHERE fresh.tenant_id <> old.tenant_id OR fresh.order_line_id <> old.order_line_id
        OR fresh.project_id <> old.project_id OR fresh.allocated_qty <> old.allocated_qty
        OR CAST(fresh.scope_status AS BINARY) <> CAST(old.scope_status AS BINARY)
        OR fresh.allocation_version <> old.allocation_version
        OR NOT (CAST(fresh.source_evidence AS BINARY) <=> CAST(old.source_evidence AS BINARY))
        OR fresh.effective_from <> old.effective_from
        OR NOT (fresh.effective_to <=> old.effective_to) OR fresh.version <> old.version;
    IF v_count <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_RECONCILIATION_FAILED'; END IF;
    SELECT COUNT(*) INTO v_count
      FROM `com_delivery_scope_detail` old
      JOIN `fcom001_shadow_com_delivery_scope_detail` fresh ON fresh.id = old.id
     WHERE fresh.tenant_id <> old.tenant_id OR fresh.delivery_scope_id <> old.delivery_scope_id
        OR fresh.allocated_qty <> old.allocated_qty
        OR CAST(fresh.detail_status AS BINARY) <> CAST(old.detail_status AS BINARY)
        OR NOT (CAST(fresh.serial_no AS BINARY) <=> CAST(old.serial_no AS BINARY))
        OR NOT (fresh.source_snapshot <=> old.source_snapshot) OR fresh.version <> old.version;
    IF v_count <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_RECONCILIATION_FAILED'; END IF;
    SELECT COALESCE(SUM(order_qty), 0) INTO v_count FROM `fcom001_shadow_com_sales_order_line`;
    IF v_count <> v_seed_line_qty THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_RECONCILIATION_FAILED'; END IF;
    SELECT COALESCE(SUM(allocated_qty), 0) INTO v_count FROM `fcom001_shadow_com_delivery_scope`;
    IF v_count <> v_seed_scope_qty THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_RECONCILIATION_FAILED'; END IF;
    SELECT COALESCE(SUM(allocated_qty), 0) INTO v_count FROM `fcom001_shadow_com_delivery_scope_detail`;
    IF v_count <> v_seed_detail_qty THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_RECONCILIATION_FAILED'; END IF;

    -- 换名前再次核对冻结水位；任一输入变化保持V123正式表原样。
    SELECT COUNT(*), MIN(id), MAX(id), COALESCE(MAX(version), 0), MAX(update_time)
      INTO v_count, v_check_min_id, v_check_max_id, v_check_max_version, v_check_max_update_time
      FROM `com_order_line`;
    IF v_count <> v_line_count OR NOT (v_check_min_id <=> v_line_min_id)
       OR NOT (v_check_max_id <=> v_line_max_id) OR v_check_max_version <> v_line_max_version
       OR NOT (v_check_max_update_time <=> v_line_max_update_time) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_V70_WATERMARK_CHANGED';
    END IF;
    SELECT COUNT(*), MIN(id), MAX(id), COALESCE(MAX(version), 0), MAX(update_time)
      INTO v_count, v_check_min_id, v_check_max_id, v_check_max_version, v_check_max_update_time
      FROM `com_delivery_scope`;
    IF v_count <> v_scope_count OR NOT (v_check_min_id <=> v_scope_min_id)
       OR NOT (v_check_max_id <=> v_scope_max_id) OR v_check_max_version <> v_scope_max_version
       OR NOT (v_check_max_update_time <=> v_scope_max_update_time) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_V70_WATERMARK_CHANGED';
    END IF;
    SELECT COUNT(*), MIN(id), MAX(id), COALESCE(MAX(version), 0), MAX(update_time)
      INTO v_count, v_check_min_id, v_check_max_id, v_check_max_version, v_check_max_update_time
      FROM `com_delivery_scope_detail`;
    IF v_count <> v_detail_count OR NOT (v_check_min_id <=> v_detail_min_id)
       OR NOT (v_check_max_id <=> v_detail_max_id) OR v_check_max_version <> v_detail_max_version
       OR NOT (v_check_max_update_time <=> v_detail_max_update_time) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_V70_WATERMARK_CHANGED';
    END IF;
    SELECT COALESCE(SUM(quantity), 0) INTO v_count FROM `com_order_line`;
    IF v_count <> v_seed_line_qty THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_V70_WATERMARK_CHANGED'; END IF;
    SELECT COALESCE(SUM(allocated_qty), 0) INTO v_count FROM `com_delivery_scope`;
    IF v_count <> v_seed_scope_qty THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_V70_WATERMARK_CHANGED'; END IF;
    SELECT COALESCE(SUM(allocated_qty), 0) INTO v_count FROM `com_delivery_scope_detail`;
    IF v_count <> v_seed_detail_qty THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FCOM001_V70_WATERMARK_CHANGED'; END IF;

    -- 唯一发布动作；其后不再执行影响业务真值的DDL或DML。
    RENAME TABLE
      `com_order_line` TO `fcom001_v70_com_order_line`,
      `com_delivery_scope` TO `fcom001_v70_com_delivery_scope`,
      `com_delivery_scope_detail` TO `fcom001_v70_com_delivery_scope_detail`,
      `fcom001_shadow_com_contract` TO `com_contract`,
      `fcom001_shadow_com_sales_order` TO `com_sales_order`,
      `fcom001_shadow_com_sales_order_line` TO `com_sales_order_line`,
      `fcom001_shadow_com_order_contract_relation` TO `com_order_contract_relation`,
      `fcom001_shadow_com_project_contract_relation` TO `com_project_contract_relation`,
      `fcom001_shadow_com_delivery_scope` TO `com_delivery_scope`,
      `fcom001_shadow_com_delivery_scope_detail` TO `com_delivery_scope_detail`,
      `fcom001_shadow_acc_acceptance_scope_binding` TO `acc_acceptance_scope_binding`;
END$$

DELIMITER ;

CALL `fcom001_v124_forward`();
DROP PROCEDURE IF EXISTS `fcom001_v124_forward`;
