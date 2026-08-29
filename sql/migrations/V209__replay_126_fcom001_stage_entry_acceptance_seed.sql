-- Chronologically replayed from 7d578e3749e8a1262e1589d1d3342c98872d91aa (codex/f-acc-001-sds), original sql/migrations/V126__fcom001_stage_entry_acceptance_seed.sql.
-- Renumbered after current master; Feature status is not promoted by this receipt.

-- F-COM-001 / COM-01：公开REST进入验收阶段的独立受管正向夹具。
-- 固定身份要么全新原子写入，要么完整一致地幂等复核；部分占用或普通数据冲突一律失败。

DROP PROCEDURE IF EXISTS `fcom001_apply_v126_stage_entry_seed`;

DELIMITER $$
CREATE PROCEDURE `fcom001_apply_v126_stage_entry_seed`()
BEGIN
  DECLARE existing_identity_count INT DEFAULT 0;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;

  SELECT
      (SELECT COUNT(*) FROM `proj_project`
        WHERE `tenant_id` = 0 AND (`id` = 992002900001
          OR `project_code` = 'F-COM001-STAGE-ENTRY-001'
          OR (`code_root_id` = 992002900001 AND `project_sequence` = 0)))
    + (SELECT COUNT(*) FROM `proj_project_tree_version`
        WHERE `tenant_id` = 0 AND (`id` = 992002900002
          OR (`root_project_id` = 992002900001 AND `tree_version` = 1)
          OR `change_batch_id` = 'F-COM001-STAGE-ENTRY-TREE-V1'))
    + (SELECT COUNT(*) FROM `proj_project_tree_path`
        WHERE `tenant_id` = 0 AND (`id` = 992002900003
          OR (`root_project_id` = 992002900001 AND `tree_version` = 1
            AND `ancestor_project_id` = 992002900001
            AND `descendant_project_id` = 992002900001)))
    + (SELECT COUNT(*) FROM `proj_project_stage`
        WHERE `tenant_id` = 0 AND (`id` IN (992002900004, 992002900005)
          OR (`project_id` = 992002900001 AND `stage_code` IN ('S4', 'S5'))))
    + (SELECT COUNT(*) FROM `proj_project_member_assignment`
        WHERE `tenant_id` = 0 AND (`id` = 992002900006
          OR (`project_id` = 992002900001 AND `user_id` = 992002800002
            AND `member_role` = 'PROJECT_MANAGER'
            AND `effective_from` = '2026-08-01 00:00:00.000')))
    + (SELECT COUNT(*) FROM `com_sales_order_line`
        WHERE `tenant_id` = 0 AND (`id` = 992002900007
          OR (`source_system` = 'SEED'
            AND `source_record_key` = 'F-COM001-STAGE-ENTRY-LINE-001')
          OR (`order_id` = 992002399001 AND `line_no` = 'LINE-STAGE-ENTRY')))
    + (SELECT COUNT(*) FROM `com_delivery_scope`
        WHERE `tenant_id` = 0 AND (`id` = 992002900008
          OR (`order_line_id` = 992002900007 AND `project_id` = 992002900001
            AND `allocation_version` = 1)))
    + (SELECT COUNT(*) FROM `com_delivery_scope_detail`
        WHERE `tenant_id` = 0 AND (`id` = 992002900009
          OR (`delivery_scope_id` = 992002900008 AND `detail_sequence` = 1)))
    INTO existing_identity_count;

  IF existing_identity_count = 0 THEN
    INSERT INTO `proj_project`
    (`id`, `project_code`, `code_root_id`, `project_sequence`, `code_rule_version`, `project_name`,
     `root_id`, `tree_path`, `tree_depth`, `tree_sort`, `manager_id`, `manager_employee_no`,
     `manager_name`, `company_id`, `company_code`, `company_name`, `department_id`,
     `department_code`, `department_name`, `project_type`, `creation_reason`, `source_type`,
     `status`, `version`, `creator`, `updater`, `deleted`, `tenant_id`, `lifecycle_status`,
     `current_stage`, `assignment_status`)
    VALUES
    (992002900001, 'F-COM001-STAGE-ENTRY-001', 992002900001, 0, 'V1',
     'F-COM-001验收阶段进入正向夹具', 992002900001, '/', 0, 0, 992002800002,
     'FCOM001-ACCEPTANCE', 'FCOM001全权限验收', 930850, 'DPTECH-DEMO',
     '迪普科技示例公司', 930851, 'OFFICE-HZ-DEMO', '杭州示例办事处', 'STANDARD',
     'F-COM-001公开REST进入验收阶段受管前置', 'MIGRATION', 'S4', 0,
     'fcom001_seed', 'fcom001_seed', b'0', 0, 'ACTIVE', 'S4', 'ASSIGNED');

    INSERT INTO `proj_project_tree_version`
    (`id`, `root_project_id`, `tree_version`, `status`, `change_batch_id`, `node_count`,
     `path_count`, `activated_at`, `version`, `creator`, `updater`, `deleted`, `tenant_id`)
    VALUES
    (992002900002, 992002900001, 1, 'ACTIVE', 'F-COM001-STAGE-ENTRY-TREE-V1', 1, 1,
     NOW(), 0, 'fcom001_seed', 'fcom001_seed', b'0', 0);

    INSERT INTO `proj_project_tree_path`
    (`id`, `tree_version`, `root_project_id`, `ancestor_project_id`, `descendant_project_id`,
     `distance`, `version`, `creator`, `updater`, `deleted`, `tenant_id`)
    VALUES
    (992002900003, 1, 992002900001, 992002900001, 992002900001, 0, 0,
     'fcom001_seed', 'fcom001_seed', b'0', 0);

    INSERT INTO `proj_project_stage`
    (`id`, `project_id`, `stage_code`, `name`, `sort_order`, `status`, `version`,
     `creator`, `updater`, `deleted`, `tenant_id`)
    VALUES
    (992002900004, 992002900001, 'S4', '上线阶段', 40, 'DONE', 0,
     'fcom001_seed', 'fcom001_seed', b'0', 0),
    (992002900005, 992002900001, 'S5', '验收阶段', 50, 'PENDING', 0,
     'fcom001_seed', 'fcom001_seed', b'0', 0);

    INSERT INTO `proj_project_member_assignment`
    (`id`, `project_id`, `user_id`, `employee_no`, `member_name`, `company_id`, `company_code`,
     `company_name`, `department_id`, `department_code`, `department_name`, `member_role`,
     `assignment_type`, `responsibility`, `effective_from`, `effective_to`, `status`, `version`,
     `creator`, `updater`, `deleted`, `tenant_id`)
    VALUES
    (992002900006, 992002900001, 992002800002, 'FCOM001-ACCEPTANCE',
     'FCOM001全权限验收', 930850, 'DPTECH-DEMO', '迪普科技示例公司', 930851,
     'OFFICE-HZ-DEMO', '杭州示例办事处', 'PROJECT_MANAGER', 'PRIMARY',
     'F-COM-001验收阶段进入正向夹具', '2026-08-01 00:00:00.000', NULL, 'ACTIVE', 0,
     'fcom001_seed', 'fcom001_seed', b'0', 0);

    INSERT INTO `com_sales_order_line`
    (`id`, `tenant_id`, `order_id`, `source_system`, `source_record_key`, `source_version`,
     `company_id`, `company_code`, `company_name`, `order_type`, `order_no`, `line_no`,
     `item_code`, `item_desc`, `product_code`, `order_qty`, `open_qty`, `delivered_qty`,
     `unit_code`, `unit_scale`, `quantity_status`, `source_sync_time`, `source_updated_at`,
     `status`, `version`, `creator`, `updater`, `deleted`)
    VALUES
    (992002900007, 0, 992002399001, 'SEED', 'F-COM001-STAGE-ENTRY-LINE-001', '1',
     930850, 'DPTECH-DEMO', '迪普科技示例公司', 'SEED', 'FPROJ002-V18-ORDER',
     'LINE-STAGE-ENTRY', 'ITEM-FCOM001-STAGE-ENTRY', '验收阶段进入专用范围',
     'F-COM001-PRODUCT-STAGE-ENTRY', 5, 5, 0, 'SET', 0, 'CONFIRMED', NOW(3), NOW(3),
     'ENABLED', 0, 'fcom001_seed', 'fcom001_seed', 0);

    INSERT INTO `com_delivery_scope`
    (`id`, `tenant_id`, `project_id`, `project_code`, `project_name`, `project_company_code`,
     `project_company_name`, `project_department_code`, `project_department_name`,
     `project_manager_employee_no`, `project_manager_name`, `order_line_id`,
     `order_source_system`, `order_company_code`, `order_company_name`, `order_type`, `order_no`,
     `line_no`, `item_code`, `item_desc`, `allocated_qty`, `scope_status`, `allocation_version`,
     `allocation_source`, `change_reason`, `office_department_id`, `office_department_code`,
     `office_department_name`, `office_department_version`, `source_evidence`, `effective_from`,
     `status`, `version`, `creator`, `updater`, `deleted`)
    VALUES
    (992002900008, 0, 992002900001, 'F-COM001-STAGE-ENTRY-001',
     'F-COM-001验收阶段进入正向夹具', 'DPTECH-DEMO', '迪普科技示例公司',
     'OFFICE-HZ-DEMO', '杭州示例办事处', 'FCOM001-ACCEPTANCE', 'FCOM001全权限验收',
     992002900007, 'SEED', 'DPTECH-DEMO', '迪普科技示例公司', 'SEED',
     'FPROJ002-V18-ORDER', 'LINE-STAGE-ENTRY', 'ITEM-FCOM001-STAGE-ENTRY',
     '验收阶段进入专用范围', 2, 'ACTIVE', 1, 'SEED', '验收阶段进入正向夹具',
     930851, 'OFFICE-HZ-DEMO', '杭州示例办事处', 0,
     'F-COM001-STAGE-ENTRY-SCOPE-001', NOW(3), 'ENABLED', 0,
     'fcom001_seed', 'fcom001_seed', 0);

    INSERT INTO `com_delivery_scope_detail`
    (`id`, `tenant_id`, `delivery_scope_id`, `detail_sequence`, `product_code`,
     `source_record_key`, `allocated_qty`, `detail_status`, `source_snapshot`, `version`,
     `creator`, `updater`, `deleted`)
    VALUES
    (992002900009, 0, 992002900008, 1, 'F-COM001-PRODUCT-STAGE-ENTRY',
     'F-COM001-STAGE-ENTRY-DETAIL-001', 2, 'ACTIVE',
     JSON_OBJECT('scenario', 'STAGE_ENTRY_ACCEPTANCE'), 0,
     'fcom001_seed', 'fcom001_seed', 0);
  END IF;

  IF (SELECT COUNT(*) FROM `proj_project`
       WHERE `id` = 992002900001 AND `tenant_id` = 0
         AND `project_code` = 'F-COM001-STAGE-ENTRY-001'
         AND `code_root_id` = 992002900001 AND `project_sequence` = 0
         AND `root_id` = 992002900001 AND `parent_id` IS NULL AND `tree_path` = '/'
         AND `manager_id` = 992002800002 AND `company_id` = 930850
         AND `company_code` = 'DPTECH-DEMO' AND `department_id` = 930851
         AND `department_code` = 'OFFICE-HZ-DEMO' AND `status` = 'S4'
         AND `lifecycle_status` = 'ACTIVE' AND `current_stage` = 'S4'
         AND `version` = 0 AND `creator` = 'fcom001_seed' AND `updater` = 'fcom001_seed'
         AND `deleted` = b'0') <> 1
    OR (SELECT COUNT(*) FROM `proj_project_tree_version`
       WHERE `id` = 992002900002 AND `tenant_id` = 0 AND `root_project_id` = 992002900001
         AND `tree_version` = 1 AND `status` = 'ACTIVE'
         AND `change_batch_id` = 'F-COM001-STAGE-ENTRY-TREE-V1'
         AND `node_count` = 1 AND `path_count` = 1 AND `creator` = 'fcom001_seed'
         AND `updater` = 'fcom001_seed' AND `deleted` = b'0') <> 1
    OR (SELECT COUNT(*) FROM `proj_project_tree_path`
       WHERE `id` = 992002900003 AND `tenant_id` = 0 AND `tree_version` = 1
         AND `root_project_id` = 992002900001 AND `ancestor_project_id` = 992002900001
         AND `descendant_project_id` = 992002900001 AND `distance` = 0
         AND `creator` = 'fcom001_seed' AND `updater` = 'fcom001_seed'
         AND `deleted` = b'0') <> 1
    OR (SELECT COUNT(*) FROM `proj_project_stage`
       WHERE `tenant_id` = 0 AND `project_id` = 992002900001
         AND ((`id` = 992002900004 AND `stage_code` = 'S4' AND `sort_order` = 40
               AND `status` = 'DONE' AND `version` = 0)
           OR (`id` = 992002900005 AND `stage_code` = 'S5' AND `sort_order` = 50
               AND `status` = 'PENDING' AND `version` = 0))
         AND `creator` = 'fcom001_seed' AND `updater` = 'fcom001_seed'
         AND `deleted` = b'0') <> 2
    OR (SELECT COUNT(*) FROM `proj_project_member_assignment`
       WHERE `id` = 992002900006 AND `tenant_id` = 0 AND `project_id` = 992002900001
         AND `user_id` = 992002800002 AND `member_role` = 'PROJECT_MANAGER'
         AND `assignment_type` = 'PRIMARY' AND `effective_to` IS NULL AND `status` = 'ACTIVE'
         AND `creator` = 'fcom001_seed' AND `updater` = 'fcom001_seed'
         AND `deleted` = b'0') <> 1
    OR (SELECT COUNT(*) FROM `com_sales_order_line`
       WHERE `id` = 992002900007 AND `tenant_id` = 0 AND `order_id` = 992002399001
         AND `source_system` = 'SEED' AND `source_record_key` = 'F-COM001-STAGE-ENTRY-LINE-001'
         AND `source_version` = '1' AND `line_no` = 'LINE-STAGE-ENTRY'
         AND `product_code` = 'F-COM001-PRODUCT-STAGE-ENTRY' AND `order_qty` = 5
         AND `open_qty` = 5 AND `quantity_status` = 'CONFIRMED' AND `status` = 'ENABLED'
         AND `creator` = 'fcom001_seed' AND `updater` = 'fcom001_seed' AND `deleted` = 0) <> 1
    OR (SELECT COUNT(*) FROM `com_delivery_scope`
       WHERE `id` = 992002900008 AND `tenant_id` = 0 AND `project_id` = 992002900001
         AND `project_code` = 'F-COM001-STAGE-ENTRY-001' AND `order_line_id` = 992002900007
         AND `allocated_qty` = 2 AND `scope_status` = 'ACTIVE' AND `allocation_version` = 1
         AND `office_department_id` = 930851 AND `office_department_code` = 'OFFICE-HZ-DEMO'
         AND `office_department_name` = '杭州示例办事处' AND `office_department_version` = 0
         AND `effective_to` IS NULL AND `status` = 'ENABLED'
         AND `creator` = 'fcom001_seed' AND `updater` = 'fcom001_seed' AND `deleted` = 0) <> 1
    OR (SELECT COUNT(*) FROM `com_delivery_scope_detail`
       WHERE `id` = 992002900009 AND `tenant_id` = 0 AND `delivery_scope_id` = 992002900008
         AND `detail_sequence` = 1 AND `product_code` = 'F-COM001-PRODUCT-STAGE-ENTRY'
         AND `allocated_qty` = 2 AND `detail_status` = 'ACTIVE'
         AND `creator` = 'fcom001_seed' AND `updater` = 'fcom001_seed' AND `deleted` = 0) <> 1
    OR (SELECT COUNT(*) FROM `proj_project_stage_snapshot`
       WHERE `tenant_id` = 0 AND `project_id` = 992002900001
         AND `stage_code` = 'S5' AND `operation_type` = 'STAGE_ENTRY' AND `deleted` = b'0') <> 0
    OR (SELECT COUNT(*) FROM `acc_acceptance_scope_binding`
       WHERE `tenant_id` = 0 AND (`project_id` = 992002900001
          OR `delivery_scope_id` = 992002900008) AND `deleted` = 0) <> 0
    OR (SELECT COUNT(*) FROM `pms_acc_acceptance`
       WHERE `tenant_id` = 0 AND `project_id` = 992002900001 AND `deleted` = b'0') <> 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-COM-001 V126 managed stage-entry seed is incomplete or conflicting';
  END IF;

  COMMIT;
END$$
DELIMITER ;

CALL `fcom001_apply_v126_stage_entry_seed`();
DROP PROCEDURE `fcom001_apply_v126_stage_entry_seed`;
