-- F-PROJ-002 / PRD V1.8：项目拆分、版本化项目树与进度汇总验收种子。
-- 使用独立高段ID和FPROJ002-V18前缀；所有数据均可由唯一键安全重放。

-- 旧项目树独立入口退役；V1.8能力统一收敛到项目详情工作台。
UPDATE `system_menu`
SET `visible` = b'0', `status` = 1, `permission` = '', `updater` = 'seed', `update_time` = NOW()
WHERE `id` IN (18012, 18024, 18025);

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
  `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
  `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(198720, '项目拆分', 'pms:project:create', 3, 10, 18071, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198721, '项目树移动', 'pms:project:update', 3, 20, 18071, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198722, '进度策略维护', 'pms:project:progress-policy:update', 3, 30, 18071, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198723, '进度策略提交', 'pms:project:progress-policy:submit', 3, 40, 18071, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`),
  `parent_id`=VALUES(`parent_id`), `status`=0, `visible`=b'1', `updater`='seed',
  `update_time`=NOW(), `deleted`=b'0';

-- 商务范围：CONFIRMED可分配；PENDING_AUTHORITY不可分配。组合明细分别承载
-- 精确(办事处+序列号)、部分限定(仅办事处)和无维度降级；RELEASED代表停用不参与。
INSERT INTO `com_order_line` (`id`, `source_system`, `source_key`, `source_version`, `order_id`,
  `line_code`, `item_code`, `quantity`, `unit_code`, `quantity_status`, `source_updated_at`,
  `synced_at`, `version`, `creator`, `updater`, `deleted`, `tenant_id`) VALUES
(992002300001, 'SEED', 'FPROJ002-V18-CONFIRMED', '1', 992002399001, 'LINE-CONFIRMED', 'ITEM-DEMO', 100, 'SET', 'CONFIRMED', NOW(), NOW(), 0, 'seed', 'seed', b'0', 0),
(992002300002, 'SEED', 'FPROJ002-V18-PENDING', '1', 992002399001, 'LINE-PENDING', 'ITEM-PENDING', NULL, 'SET', 'PENDING_AUTHORITY', NOW(), NOW(), 0, 'seed', 'seed', b'0', 0),
(992002300003, 'SEED', 'FPROJ002-V18-NO-MATCH', '1', 992002399001, 'LINE-NO-MATCH', 'ITEM-NO-MATCH', 10, 'SET', 'CONFIRMED', NOW(), NOW(), 0, 'seed', 'seed', b'0', 0),
(992002300004, 'SEED', 'FPROJ002-V18-INACTIVE', '1', 992002399001, 'LINE-INACTIVE', 'ITEM-INACTIVE', 10, 'SET', 'CONFIRMED', NOW(), NOW(), 0, 'seed', 'seed', b'0', 0)
ON DUPLICATE KEY UPDATE `source_version`=VALUES(`source_version`), `quantity`=VALUES(`quantity`),
  `quantity_status`=VALUES(`quantity_status`), `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

-- 深度30只是性能与非固定层级证明，不是业务上限。
INSERT INTO `proj_project` (`id`, `project_code`, `code_root_id`, `project_sequence`, `code_rule_version`,
  `project_name`, `parent_id`, `root_id`, `tree_path`, `tree_depth`, `tree_sort`,
  `business_level_code`, `business_level_name`, `source_type`, `status`, `progress`,
  `lifecycle_status`, `current_stage`, `assignment_status`, `version`, `creator`, `updater`,
  `deleted`, `tenant_id`)
WITH RECURSIVE `depth_seed` AS (
  SELECT 0 AS `depth`, CAST('/' AS CHAR(1024)) AS `path`
  UNION ALL
  SELECT `depth` + 1, CAST(CONCAT(`path`, 992002000000 + `depth`, '/') AS CHAR(1024))
  FROM `depth_seed` WHERE `depth` < 30
)
SELECT 992002000000 + `depth`, CONCAT('FPROJ002-V18-D', LPAD(`depth`, 2, '0')),
  992002000000, `depth`, 'V1', CONCAT('V1.8深度验证-', `depth`),
  IF(`depth` = 0, NULL, 992002000000 + `depth` - 1), 992002000000, `path`, `depth`, 10,
  CASE MOD(`depth`, 3) WHEN 0 THEN 'LEVEL_REGION' WHEN 1 THEN 'LEVEL_OFFICE' ELSE 'LEVEL_NODE' END,
  CASE MOD(`depth`, 3) WHEN 0 THEN '大区' WHEN 1 THEN '办事处' ELSE '节点' END,
  'MIGRATION', 'S0', CASE WHEN `depth` = 0 THEN 0 ELSE 40 END,
  'ACTIVE', 'S0', 'UNASSIGNED', 0, 'seed', 'seed', b'0', 0
FROM `depth_seed`
WHERE NOT EXISTS (SELECT 1 FROM `proj_project` p WHERE p.`tenant_id`=0 AND p.`id`=992002000000 + `depth`);

INSERT INTO `proj_project` (`id`, `project_code`, `code_root_id`, `project_sequence`, `code_rule_version`,
  `project_name`, `parent_id`, `root_id`, `tree_path`, `tree_depth`, `tree_sort`,
  `business_level_code`, `business_level_name`, `source_type`, `status`, `progress`,
  `lifecycle_status`, `current_stage`, `assignment_status`, `version`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT s.`id`, s.`code`, 992002000000, s.`seq`, 'V1', s.`name`, 992002000000, 992002000000,
  '/992002000000/', 1, s.`sort`, s.`level_code`, s.`level_name`, 'MIGRATION', 'S0', s.`progress`,
  s.`lifecycle`, 'S0', 'UNASSIGNED', 0, 'seed', 'seed', b'0', 0
FROM (
  SELECT 992002000031 `id`, 'FPROJ002-V18-EQUAL' `code`, 31 `seq`, '等权策略子项目' `name`, 20 `sort`, 'LEVEL_NODE' `level_code`, '节点' `level_name`, 50 `progress`, 'NORMAL_CLOSED' `lifecycle`
  UNION ALL
  SELECT 992002000032, 'FPROJ002-V18-PENDING', 32, '待计算子项目', 30, 'LEVEL_OFFICE', '办事处', 100, 'ACTIVE'
) s
WHERE NOT EXISTS (SELECT 1 FROM `proj_project` p WHERE p.`tenant_id`=0 AND p.`id`=s.`id`);

INSERT INTO `com_delivery_scope` (`id`, `order_line_id`, `project_id`, `allocated_qty`, `scope_status`,
  `allocation_version`, `source_evidence`, `effective_from`, `effective_to`, `version`, `creator`, `updater`, `deleted`, `tenant_id`) VALUES
(992002310001, 992002300001, 992002000000, 100, 'ACTIVE', 1, 'FPROJ002-V18-SCOPE-CONFIRMED', NOW(), NULL, 0, 'seed', 'seed', b'0', 0),
(992002310004, 992002300004, 992002000000, 10, 'RELEASED', 1, 'FPROJ002-V18-SCOPE-INACTIVE', NOW(), NOW(), 0, 'seed', 'seed', b'0', 0)
ON DUPLICATE KEY UPDATE `scope_status`=VALUES(`scope_status`), `effective_to`=VALUES(`effective_to`),
  `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `com_delivery_scope_detail` (`id`, `delivery_scope_id`, `office_department_code`, `serial_no`,
  `allocated_qty`, `detail_status`, `source_snapshot`, `version`, `creator`, `updater`, `deleted`, `tenant_id`) VALUES
(992002320001, 992002310001, 'OFFICE-FPROJ002-A', 'SN-FPROJ002-001', 20, 'ACTIVE', JSON_OBJECT('scenario','EXACT_MATCH'), 0, 'seed', 'seed', b'0', 0),
(992002320002, 992002310001, 'OFFICE-FPROJ002-A', NULL, 30, 'ACTIVE', JSON_OBJECT('scenario','PARTIAL_MATCH_PRIORITY_YIELD'), 0, 'seed', 'seed', b'0', 0),
(992002320003, 992002310001, NULL, NULL, 50, 'ACTIVE', JSON_OBJECT('scenario','FALLBACK'), 0, 'seed', 'seed', b'0', 0),
(992002320004, 992002310004, NULL, NULL, 10, 'RELEASED', JSON_OBJECT('scenario','INACTIVE_NOT_PARTICIPATING'), 0, 'seed', 'seed', b'0', 0)
ON DUPLICATE KEY UPDATE `detail_status`=VALUES(`detail_status`), `source_snapshot`=VALUES(`source_snapshot`),
  `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `proj_project_tree_version` (`id`, `root_project_id`, `tree_version`, `status`, `change_batch_id`,
  `node_count`, `path_count`, `activated_at`, `version`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 992002400001, 992002000000, 1, 'ACTIVE', 'FPROJ002-V18-SEED-TREE-V1', 33, 0, NOW(), 0, 'seed', 'seed', b'0', 0
WHERE NOT EXISTS (SELECT 1 FROM `proj_project_tree_version` WHERE `tenant_id`=0 AND `root_project_id`=992002000000 AND `tree_version`=1);

INSERT INTO `proj_project_tree_path` (`id`, `tree_version`, `root_project_id`, `ancestor_project_id`,
  `descendant_project_id`, `distance`, `version`, `creator`, `updater`, `deleted`, `tenant_id`)
WITH RECURSIVE `paths` AS (
  SELECT p.`id` `ancestor_id`, p.`id` `descendant_id`, 0 `distance`
  FROM `proj_project` p WHERE p.`tenant_id`=0 AND p.`root_id`=992002000000 AND p.`deleted`=b'0'
  UNION ALL
  SELECT x.`ancestor_id`, child.`id`, x.`distance` + 1
  FROM `paths` x JOIN `proj_project` child ON child.`tenant_id`=0 AND child.`parent_id`=x.`descendant_id` AND child.`deleted`=b'0'
), `numbered` AS (
  SELECT `ancestor_id`, `descendant_id`, `distance`, ROW_NUMBER() OVER (ORDER BY `ancestor_id`, `descendant_id`) `rn` FROM `paths`
)
SELECT 992002410000 + `rn`, 1, 992002000000, `ancestor_id`, `descendant_id`, `distance`,
  0, 'seed', 'seed', b'0', 0 FROM `numbered`
ON DUPLICATE KEY UPDATE `distance`=VALUES(`distance`), `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

UPDATE `proj_project_tree_version` v
SET v.`path_count`=(SELECT COUNT(*) FROM `proj_project_tree_path` p WHERE p.`tenant_id`=0
  AND p.`root_project_id`=v.`root_project_id` AND p.`tree_version`=v.`tree_version` AND p.`deleted`=b'0'),
  v.`updater`='seed', v.`update_time`=NOW()
WHERE v.`tenant_id`=0 AND v.`root_project_id`=992002000000 AND v.`tree_version`=1;

-- 策略历史同时覆盖系统等权和人工权重；人工策略为当前生效版本。
INSERT INTO `proj_project_progress_policy_revision` (`id`, `parent_project_id`, `revision_no`, `status`,
  `policy_type`, `effective_from`, `effective_to`, `supersedes_revision_id`, `version`, `creator`, `updater`, `deleted`, `tenant_id`) VALUES
(992002500001, 992002000000, 1, 'SUPERSEDED', 'SYSTEM_EQUAL', '2026-08-01 00:00:00', '2026-08-02 00:00:00', NULL, 1, 'seed', 'seed', b'0', 0),
(992002500002, 992002000000, 2, 'ACTIVE', 'MANUAL', '2026-08-02 00:00:00', NULL, 992002500001, 1, 'seed', 'seed', b'0', 0)
ON DUPLICATE KEY UPDATE `status`=VALUES(`status`), `effective_from`=VALUES(`effective_from`),
  `effective_to`=VALUES(`effective_to`), `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `proj_project_progress_policy_item` (`id`, `policy_revision_id`, `child_project_id`, `weight`,
  `include_status_snapshot`, `version`, `creator`, `updater`, `deleted`, `tenant_id`) VALUES
(992002510001, 992002500002, 992002000001, 50, JSON_ARRAY('ACTIVE','NORMAL_CLOSED'), 0, 'seed', 'seed', b'0', 0),
(992002510002, 992002500002, 992002000031, 30, JSON_ARRAY('ACTIVE','NORMAL_CLOSED'), 0, 'seed', 'seed', b'0', 0),
(992002510003, 992002500002, 992002000032, 20, JSON_ARRAY('ACTIVE','NORMAL_CLOSED'), 0, 'seed', 'seed', b'0', 0)
ON DUPLICATE KEY UPDATE `weight`=VALUES(`weight`), `include_status_snapshot`=VALUES(`include_status_snapshot`),
  `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `proj_project_progress_fact` (`id`, `project_id`, `fact_source_type`, `fact_source_id`, `fact_version`,
  `progress`, `source_watermark`, `occurred_at`, `version`, `creator`, `updater`, `deleted`, `tenant_id`) VALUES
(992002520001, 992002000001, 'SEED', 'FPROJ002-CHILD-1', 1, 40, 'FPROJ002-WM-READY', '2026-08-02 01:00:00', 0, 'seed', 'seed', b'0', 0),
(992002520002, 992002000031, 'SEED', 'FPROJ002-CHILD-2', 1, 50, 'FPROJ002-WM-READY', '2026-08-02 01:00:00', 0, 'seed', 'seed', b'0', 0),
(992002520003, 992002000032, 'SEED', 'FPROJ002-CHILD-3', 1, 100, 'FPROJ002-WM-READY', '2026-08-02 02:00:00', 0, 'seed', 'seed', b'0', 0)
ON DUPLICATE KEY UPDATE `progress`=VALUES(`progress`), `source_watermark`=VALUES(`source_watermark`),
  `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `proj_project_progress_snapshot` (`id`, `project_id`, `policy_revision_id`, `tree_version`,
  `source_watermark`, `snapshot_status`, `progress`, `missing_item_count`, `calculated_at`,
  `version`, `creator`, `updater`, `deleted`, `tenant_id`) VALUES
(992002530001, 992002000000, 992002500002, 1, 'FPROJ002-WM-PENDING', 'PENDING', NULL, 1, '2026-08-02 01:00:00', 0, 'seed', 'seed', b'0', 0),
(992002530002, 992002000000, 992002500002, 1, 'FPROJ002-WM-READY', 'READY', 55, 0, '2026-08-02 02:00:00', 0, 'seed', 'seed', b'0', 0)
ON DUPLICATE KEY UPDATE `snapshot_status`=VALUES(`snapshot_status`), `progress`=VALUES(`progress`),
  `missing_item_count`=VALUES(`missing_item_count`), `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `proj_project_progress_snapshot_detail` (`id`, `snapshot_id`, `child_project_id`, `fact_version`,
  `child_progress`, `normalized_weight`, `contribution`, `missing_reason`, `version`, `creator`, `updater`, `deleted`, `tenant_id`) VALUES
(992002540001, 992002530001, 992002000001, 1, 40, 50, 20, NULL, 0, 'seed', 'seed', b'0', 0),
(992002540002, 992002530001, 992002000031, 1, 50, 30, 15, NULL, 0, 'seed', 'seed', b'0', 0),
(992002540003, 992002530001, 992002000032, NULL, NULL, 20, NULL, 'FACT_MISSING', 0, 'seed', 'seed', b'0', 0),
(992002540004, 992002530002, 992002000001, 1, 40, 50, 20, NULL, 0, 'seed', 'seed', b'0', 0),
(992002540005, 992002530002, 992002000031, 1, 50, 30, 15, NULL, 0, 'seed', 'seed', b'0', 0),
(992002540006, 992002530002, 992002000032, 1, 100, 20, 20, NULL, 0, 'seed', 'seed', b'0', 0)
ON DUPLICATE KEY UPDATE `fact_version`=VALUES(`fact_version`), `child_progress`=VALUES(`child_progress`),
  `normalized_weight`=VALUES(`normalized_weight`), `contribution`=VALUES(`contribution`),
  `missing_reason`=VALUES(`missing_reason`), `updater`='seed', `update_time`=NOW(), `deleted`=b'0';
