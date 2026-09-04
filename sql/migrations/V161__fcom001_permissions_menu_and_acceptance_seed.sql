-- F-COM-001 / COM-01：ERP订单行产品编码、八个最小权限键、菜单与受控验收种子。

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
CREATE PROCEDURE `fcom001_verify_v161_managed_product_codes`()
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
      SET MESSAGE_TEXT = 'F-COM-001 V161 managed seed identity is incomplete';
  END IF;
END$$
DELIMITER ;

CALL `fcom001_verify_v161_managed_product_codes`();
DROP PROCEDURE `fcom001_verify_v161_managed_product_codes`;

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(930900, '合同订单工作台', 'pms:commerce:contract:query', 2, 5, 19260, 'commerce-contracts',
 'ep:document', 'pms/commerce/contracts/index', 'PmsCommerceContracts', 0, b'1', b'1', b'1',
 'fcom001_seed', NOW(), 'fcom001_seed', NOW(), b'0'),
(930901, '项目合同关联', 'pms:commerce:contract:relate', 3, 10, 930900, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'fcom001_seed', NOW(), 'fcom001_seed', NOW(), b'0'),
(930902, '合同敏感字段', 'pms:commerce:contract:sensitive-read', 3, 20, 930900, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'fcom001_seed', NOW(), 'fcom001_seed', NOW(), b'0'),
(930903, '交付范围工作台', 'pms:commerce:scope:query', 2, 6, 19260, 'delivery-scopes',
 'ep:connection', 'pms/commerce/delivery-scope/index', 'PmsCommerceDeliveryScopes',
 0, b'1', b'1', b'1', 'fcom001_seed', NOW(), 'fcom001_seed', NOW(), b'0'),
(930904, '交付范围分配', 'pms:commerce:scope:assign', 3, 10, 930903, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'fcom001_seed', NOW(), 'fcom001_seed', NOW(), b'0'),
(930905, '交付范围调整', 'pms:commerce:scope:adjust', 3, 20, 930903, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'fcom001_seed', NOW(), 'fcom001_seed', NOW(), b'0'),
(930906, '交付范围释放', 'pms:commerce:scope:release', 3, 30, 930903, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'fcom001_seed', NOW(), 'fcom001_seed', NOW(), b'0'),
(930907, '商务权威写入', 'pms:commerce:authority:write', 3, 40, 930900, '', '', NULL, NULL,
 0, b'0', b'1', b'1', 'fcom001_seed', NOW(), 'fcom001_seed', NOW(), b'0'),
(930908, '人工候选核对', 'pms:commerce:authority:reconcile', 3, 50, 930900, '', '', NULL, NULL,
 0, b'0', b'1', b'1', 'fcom001_seed', NOW(), 'fcom001_seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name`=VALUES(`name`), `permission`=VALUES(`permission`), `type`=VALUES(`type`),
 `sort`=VALUES(`sort`), `parent_id`=VALUES(`parent_id`), `path`=VALUES(`path`),
 `icon`=VALUES(`icon`), `component`=VALUES(`component`), `component_name`=VALUES(`component_name`),
 `status`=0, `visible`=VALUES(`visible`), `keep_alive`=VALUES(`keep_alive`),
 `always_show`=VALUES(`always_show`), `updater`='fcom001_seed', `update_time`=NOW(), `deleted`=b'0';

-- 该角色仅是本地受控验收身份的正式授权配置，不是业务角色模板。
INSERT INTO `system_role`
(`id`, `name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(992002800001, 'FCOM001全权限验收角色', 'fcom001_acceptance_full', 930, 1, '', 0, 2,
 '仅用于F-COM-001本地真实浏览器正向闭环，不固化业务角色映射',
 'fcom001_seed', NOW(), 'fcom001_seed', NOW(), b'0', 0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `code`=VALUES(`code`), `sort`=VALUES(`sort`),
 `data_scope`=VALUES(`data_scope`), `status`=0, `remark`=VALUES(`remark`),
 `updater`='fcom001_seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `system_users`
(`id`, `username`, `password`, `nickname`, `remark`, `dept_id`, `post_ids`, `email`, `mobile`, `sex`,
 `avatar`, `status`, `login_ip`, `login_date`, `creator`, `create_time`, `updater`, `update_time`,
 `deleted`, `tenant_id`)
SELECT 992002800002, 'fcom001_acceptance', source_user.`password`, 'FCOM001全权限验收',
 '本地隔离验收账号；权限通过FCOM001专用正式角色配置', 930851, '[]', '', '', 0, '', 0, '', NULL,
 'fcom001_seed', NOW(), 'fcom001_seed', NOW(), b'0', 0
FROM `system_users` source_user
WHERE source_user.`id`=107 AND source_user.`deleted`=b'0'
ON DUPLICATE KEY UPDATE `username`=VALUES(`username`), `password`=VALUES(`password`),
 `nickname`=VALUES(`nickname`), `remark`=VALUES(`remark`), `dept_id`=VALUES(`dept_id`),
 `status`=0, `updater`='fcom001_seed', `update_time`=NOW(), `deleted`=b'0', `tenant_id`=0;

INSERT INTO `system_user_role`
(`user_id`, `role_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 992002800002, 992002800001, 'fcom001_seed', NOW(), 'fcom001_seed', NOW(), b'0', 0
WHERE NOT EXISTS (SELECT 1 FROM `system_user_role`
 WHERE `user_id`=992002800002 AND `role_id`=992002800001 AND `deleted`=b'0');

INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 992002800001, grant_row.`menu_id`, 'fcom001_seed', NOW(), 'fcom001_seed', NOW(), b'0', 0
FROM (
 SELECT 19260 AS `menu_id` UNION ALL SELECT 930900 UNION ALL SELECT 930901 UNION ALL SELECT 930902
 UNION ALL SELECT 930903 UNION ALL SELECT 930904 UNION ALL SELECT 930905 UNION ALL SELECT 930906
 UNION ALL SELECT 930907 UNION ALL SELECT 930908
) grant_row
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
 WHERE existing.`role_id`=992002800001 AND existing.`menu_id`=grant_row.`menu_id`
   AND existing.`deleted`=b'0');

INSERT INTO `system_user_company_department_scope`
(`id`, `tenant_id`, `user_id`, `company_id`, `company_code`, `company_name`, `department_id`,
 `department_code`, `department_name`, `scope_role`, `is_primary`, `effective_from`, `effective_to`,
 `status`, `version`, `creator`, `updater`, `deleted`)
SELECT 992002800003, 0, 992002800002, c.`id`, c.`code`, c.`name`, d.`id`, d.`code`, d.`name`,
 'FCOM001_ACCEPTANCE', b'1', '2026-08-01 00:00:00.000', NULL, 0, 0,
 'fcom001_seed', 'fcom001_seed', b'0'
FROM `system_company` c JOIN `system_dept` d
  ON d.`id`=930851 AND d.`tenant_id`=0 AND d.`deleted`=b'0'
WHERE c.`id`=930850 AND c.`tenant_id`=0 AND c.`deleted`=b'0'
ON DUPLICATE KEY UPDATE `company_id`=VALUES(`company_id`), `company_code`=VALUES(`company_code`),
 `company_name`=VALUES(`company_name`), `department_id`=VALUES(`department_id`),
 `department_code`=VALUES(`department_code`), `department_name`=VALUES(`department_name`),
 `effective_to`=NULL, `status`=0, `updater`='fcom001_seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `proj_project_member_assignment`
(`id`, `project_id`, `user_id`, `employee_no`, `member_name`, `company_id`, `company_code`, `company_name`,
 `department_id`, `department_code`, `department_name`, `member_role`, `assignment_type`, `responsibility`,
 `effective_from`, `effective_to`, `status`, `version`, `creator`, `updater`, `deleted`, `tenant_id`)
VALUES
(992002800004, 992002000000, 992002800002, 'FCOM001-ACCEPTANCE', 'FCOM001全权限验收',
 930850, 'DPTECH-DEMO', '迪普科技示例公司', 930851, 'OFFICE-HZ-DEMO', '杭州示例办事处',
 'PROJECT_MANAGER', 'PRIMARY', 'F-COM-001正向闭环验收', '2026-08-01 00:00:00.000', NULL,
 'ACTIVE', 0, 'fcom001_seed', 'fcom001_seed', b'0', 0)
ON DUPLICATE KEY UPDATE `member_name`=VALUES(`member_name`), `responsibility`=VALUES(`responsibility`),
 `effective_to`=NULL, `status`='ACTIVE', `updater`='fcom001_seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `com_contract`
(`id`, `tenant_id`, `company_id`, `company_code`, `company_name`, `contract_no`,
 `master_source_system`, `master_source_record_key`, `master_source_version`, `contract_type`,
 `contract_name`, `currency_code`, `source_sync_time`, `source_updated_at`, `status`, `version`,
 `creator`, `updater`, `deleted`)
VALUES
(992002390001, 0, 930850, 'DPTECH-DEMO', '迪普科技示例公司', 'F-COM001-CONTRACT-001',
 'SEED', 'F-COM001-CONTRACT-001', '1', 'SALES', 'F-COM-001验收合同', 'CNY', NOW(3), NOW(3),
 'ENABLED', 0, 'fcom001_seed', 'fcom001_seed', 0)
ON DUPLICATE KEY UPDATE `contract_name`=VALUES(`contract_name`), `status`='ENABLED',
 `updater`='fcom001_seed', `update_time`=NOW(3), `deleted`=0;

INSERT INTO `com_order_contract_relation`
(`id`, `tenant_id`, `order_id`, `contract_id`, `relation_role`, `relation_source`,
 `source_system`, `sales_order_source_key`, `contract_source_key`, `source_version`,
 `source_evidence`, `effective_from`, `effective_to`, `creator`, `updater`, `deleted`)
VALUES
(992002390002, 0, 992002399001, 992002390001, 'RELATED', 'SEED',
 'SEED', 'FPROJ002-V18-ORDER', 'F-COM001-CONTRACT-001', '1',
 JSON_OBJECT('evidenceType', 'CONTROLLED_ACCEPTANCE_SEED',
             'migrationVersion', 'V161',
             'salesOrderSourceKey', 'FPROJ002-V18-ORDER',
             'contractSourceKey', 'F-COM001-CONTRACT-001'),
 '2026-08-01 00:00:00.000', NULL, 'fcom001_seed', 'fcom001_seed', 0)
ON DUPLICATE KEY UPDATE
 `relation_source`=VALUES(`relation_source`), `source_system`=VALUES(`source_system`),
 `sales_order_source_key`=VALUES(`sales_order_source_key`),
 `contract_source_key`=VALUES(`contract_source_key`), `source_version`=VALUES(`source_version`),
 `source_evidence`=VALUES(`source_evidence`), `effective_from`=VALUES(`effective_from`),
 `effective_to`=VALUES(`effective_to`), `updater`='fcom001_seed',
 `update_time`=NOW(3), `deleted`=0;

-- 两条附加订单行分别用于验收阶段外正向写入和验收阶段内锁定负向。
INSERT INTO `com_sales_order_line`
(`id`, `tenant_id`, `order_id`, `source_system`, `source_record_key`, `source_version`,
 `company_id`, `company_code`, `company_name`, `order_type`, `order_no`, `line_no`,
 `item_code`, `item_desc`, `product_code`, `order_qty`, `open_qty`, `delivered_qty`,
 `unit_code`, `unit_scale`, `quantity_status`, `source_sync_time`, `source_updated_at`,
 `status`, `version`, `creator`, `updater`, `deleted`)
VALUES
(992002300005, 0, 992002399001, 'SEED', 'F-COM001-AVAILABLE', '1', 930850,
 'DPTECH-DEMO', '迪普科技示例公司', 'SEED', 'FPROJ002-V18-ORDER', 'LINE-AVAILABLE',
 'ITEM-FCOM001-A', '正向分配验收', 'F-COM001-PRODUCT-A', 50, 50, 0, 'SET', 0,
 'CONFIRMED', NOW(3), NOW(3), 'ENABLED', 0, 'fcom001_seed', 'fcom001_seed', 0),
(992002300006, 0, 992002399001, 'SEED', 'F-COM001-LOCKED', '1', 930850,
 'DPTECH-DEMO', '迪普科技示例公司', 'SEED', 'FPROJ002-V18-ORDER', 'LINE-LOCKED',
 'ITEM-FCOM001-B', '验收锁定负向', 'F-COM001-PRODUCT-B', 20, 20, 0, 'SET', 0,
 'CONFIRMED', NOW(3), NOW(3), 'ENABLED', 0, 'fcom001_seed', 'fcom001_seed', 0)
ON DUPLICATE KEY UPDATE `product_code`=VALUES(`product_code`), `order_qty`=VALUES(`order_qty`),
 `quantity_status`='CONFIRMED', `status`='ENABLED', `updater`='fcom001_seed',
 `update_time`=NOW(3), `deleted`=0;

-- 叶项目32是独立的验收阶段内受控场景；根项目仍保持F-PROJ-002原有S0场景。
UPDATE `proj_project`
SET `company_id`=930850, `company_code`='DPTECH-DEMO', `company_name`='迪普科技示例公司',
    `department_id`=930851, `department_code`='OFFICE-HZ-DEMO', `department_name`='杭州示例办事处',
    `current_stage`='S5', `updater`='fcom001_seed', `update_time`=NOW()
WHERE `tenant_id`=0 AND `id`=992002000032 AND `lifecycle_status`='ACTIVE' AND `deleted`=b'0';

INSERT INTO `proj_project_stage`
(`id`, `project_id`, `stage_code`, `name`, `sort_order`, `status`, `version`,
 `creator`, `updater`, `deleted`, `tenant_id`)
VALUES
(992002700005, 992002000032, 'S5', '验收阶段', 50, 'ACTIVE', 0,
 'fcom001_seed', 'fcom001_seed', b'0', 0)
ON DUPLICATE KEY UPDATE `status`='ACTIVE', `updater`='fcom001_seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `proj_project_stage_snapshot`
(`id`, `project_id`, `stage_code`, `snapshot_no`, `operation_type`, `before_stage`, `after_stage`,
 `before_lifecycle_status`, `after_lifecycle_status`, `reason_code`, `reason_detail`, `tree_version`,
 `operation_id`, `operator_user_id`, `operated_at`, `creator`, `updater`, `deleted`, `tenant_id`)
VALUES
(992002700006, 992002000032, 'S5', 1, 'STAGE_ENTRY', 'S4', 'S5', 'ACTIVE', 'ACTIVE',
 'FCOM001_ACCEPTANCE_SEED', '受控验收阶段内范围锁定场景', 1, 'F-COM001-STAGE-ENTRY-001',
 992002800002, NOW(3), 'fcom001_seed', 'fcom001_seed', b'0', 0)
ON DUPLICATE KEY UPDATE `reason_detail`=VALUES(`reason_detail`), `updater`='fcom001_seed',
 `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `com_delivery_scope`
(`id`, `tenant_id`, `project_id`, `project_code`, `order_line_id`, `order_source_system`,
 `order_company_code`, `order_company_name`, `order_type`, `order_no`, `line_no`, `item_code`, `item_desc`,
 `allocated_qty`, `scope_status`, `allocation_version`, `allocation_source`, `change_reason`,
 `office_department_id`, `office_department_code`, `office_department_name`, `office_department_version`,
 `source_evidence`, `effective_from`, `status`, `version`, `creator`, `updater`, `deleted`)
VALUES
(992002310006, 0, 992002000032, 'FPROJ002-V18-PENDING', 992002300006, 'SEED',
 'DPTECH-DEMO', '迪普科技示例公司', 'SEED', 'FPROJ002-V18-ORDER', 'LINE-LOCKED',
 'ITEM-FCOM001-B', '验收锁定负向', 10, 'ACTIVE', 1, 'SEED', '验收阶段内锁定场景',
 930851, 'OFFICE-HZ-DEMO', '杭州示例办事处', 0, 'F-COM001-LOCKED-SCOPE-001',
 NOW(3), 'ENABLED', 0, 'fcom001_seed', 'fcom001_seed', 0)
ON DUPLICATE KEY UPDATE `scope_status`='ACTIVE', `effective_to`=NULL,
 `updater`='fcom001_seed', `update_time`=NOW(3), `deleted`=0;

INSERT INTO `com_delivery_scope_detail`
(`id`, `tenant_id`, `delivery_scope_id`, `detail_sequence`, `product_code`, `source_record_key`,
 `allocated_qty`, `detail_status`, `source_snapshot`, `version`, `creator`, `updater`, `deleted`)
VALUES
(992002320006, 0, 992002310006, 1, 'F-COM001-PRODUCT-B', 'F-COM001-LOCKED-DETAIL-001',
 10, 'ACTIVE', JSON_OBJECT('scenario','ACCEPTANCE_LOCKED'), 0, 'fcom001_seed', 'fcom001_seed', 0)
ON DUPLICATE KEY UPDATE `detail_status`='ACTIVE', `updater`='fcom001_seed',
 `update_time`=NOW(3), `deleted`=0;

INSERT INTO `acc_acceptance_scope_binding`
(`id`, `tenant_id`, `project_id`, `project_stage_snapshot_id`, `delivery_scope_id`,
 `scope_allocation_version`, `binding_trigger`, `binding_status`, `effective_from`,
 `acceptance_fact_version`, `version`, `creator`, `updater`, `deleted`)
VALUES
(992002700007, 0, 992002000032, 992002700006, 992002310006, 1,
 'PROJECT_STAGE_ENTRY', 'LOCKED', NOW(3), 1, 0, 'fcom001_seed', 'fcom001_seed', 0)
ON DUPLICATE KEY UPDATE `binding_status`='LOCKED', `effective_to`=NULL,
 `updater`='fcom001_seed', `update_time`=NOW(3), `deleted`=0;

INSERT INTO `pms_equipment`
(`id`, `serial_number`, `name`, `model`, `project_id`, `status`, `remark`, `version`,
 `creator`, `updater`, `deleted`, `tenant_id`)
VALUES
(992002700008, 'SN-FCOM001-VALID-001', 'F-COM-001可分配设备', 'ACCEPTANCE', NULL, 0,
 '受控AST有效SN验收', 0, 'fcom001_seed', 'fcom001_seed', b'0', 0),
(992002700009, 'SN-FCOM001-INVALID-001', 'F-COM-001不可分配设备', 'ACCEPTANCE', NULL, 4,
 '受控AST不可分配SN验收', 0, 'fcom001_seed', 'fcom001_seed', b'0', 0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `project_id`=VALUES(`project_id`),
 `status`=VALUES(`status`), `remark`=VALUES(`remark`), `updater`='fcom001_seed',
 `update_time`=NOW(), `deleted`=b'0', `tenant_id`=0;
