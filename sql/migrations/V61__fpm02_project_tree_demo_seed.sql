-- V61: F-PM02 项目树与进度汇总幂等示例数据
--
-- 依据：F-PM02 Feature Spec §7、Technical Plan §8、工程链 DoD 初始化数据要求。
-- 边界：仅提供可运行的合法示例事实；循环移动、跨租户父节点和权重合计不为
-- 100% 属于拒绝场景，由领域/API 测试与真实浏览器验收覆盖，不向数据库写入非法状态。

INSERT INTO `proj_project` (
  `id`, `project_code`, `code_root_id`, `project_sequence`, `code_rule_version`,
  `project_name`, `parent_id`, `root_id`, `tree_path`, `tree_depth`, `tree_sort`,
  `business_level_code`, `business_level_name`,
  `customer_code`, `customer_name`, `project_type`, `signing_method`,
  `project_category`, `implementation_mode`,
  `lifecycle_template_id`, `lifecycle_template_revision_no`, `template_load_method`,
  `process_definition_key`, `process_definition_version`,
  `source_type`, `status`, `progress`, `aggregation_weight`, `weight_source`,
  `version`, `creator`, `updater`, `deleted`, `tenant_id`
)
SELECT
  seed.id, seed.project_code, seed.code_root_id, seed.project_sequence, 'V1',
  seed.project_name, seed.parent_id, seed.root_id, seed.tree_path, seed.tree_depth,
  seed.tree_sort, seed.business_level_code, seed.business_level_name,
  'CUS-FPM02-DEMO', 'F-PM02示例客户', 'STANDARD', 'CHANNEL_SIGN',
  'GENERAL', 'DIRECT_SERVICE',
  910008, 1, 'MANUAL_SELECTED', 'PROC-PMS-DELIVERY-STD', 'v1',
  'MANUAL', 'S0', seed.progress, seed.aggregation_weight, seed.weight_source,
  0, 'seed', 'seed', b'0', 0
FROM (
  SELECT 920001 AS id, 'PJT-DEMO-920001' AS project_code, 920001 AS code_root_id,
         0 AS project_sequence, '华东交付示例根项目' AS project_name,
         NULL AS parent_id, 920001 AS root_id, '/' AS tree_path, 0 AS tree_depth,
         10 AS tree_sort, 'LEVEL_REGION' AS business_level_code, '大区' AS business_level_name,
         44.00 AS progress, NULL AS aggregation_weight, NULL AS weight_source
  UNION ALL
  SELECT 920002, 'PJT-DEMO-920001-SP1', 920001, 1, '上海办事处交付',
         920001, 920001, '/920001/', 1, 10, 'LEVEL_OFFICE', '办事处',
         20.00, 60.00, 'MANUAL'
  UNION ALL
  SELECT 920003, 'PJT-DEMO-920001-SP2', 920001, 2, '江苏办事处交付',
         920001, 920001, '/920001/', 1, 20, 'LEVEL_OFFICE', '办事处',
         80.00, 40.00, 'MANUAL'
  UNION ALL
  SELECT 920004, 'PJT-DEMO-920001-SP3', 920001, 3, '上海一号节点',
         920002, 920001, '/920001/920002/', 2, 10, 'LEVEL_NODE', '节点',
         10.00, NULL, 'DEFAULT_EQUAL'
  UNION ALL
  SELECT 920005, 'PJT-DEMO-920001-SP4', 920001, 4, '上海协同办事处节点',
         920002, 920001, '/920001/920002/', 2, 20, 'LEVEL_OFFICE', '办事处',
         30.00, NULL, 'DEFAULT_EQUAL'
  UNION ALL
  SELECT 920006, 'PJT-DEMO-920001-SP5', 920001, 5, '上海一号节点实施单元',
         920004, 920001, '/920001/920002/920004/', 3, 10, 'LEVEL_NODE', '节点',
         10.00, NULL, 'DEFAULT_EQUAL'
) AS seed
WHERE NOT EXISTS (
  SELECT 1
  FROM `proj_project` current_project
  WHERE current_project.tenant_id = 0
    AND (current_project.id = seed.id OR current_project.project_code = seed.project_code)
);

-- 子项目编码分配器会在 ROOT:<code_root_id> 命名空间中继续递增。种子已占用
-- SP1～SP5，因此 next_value 必须从 6 开始，避免真实浏览器下挂子项目时碰撞。
INSERT INTO `proj_project_code_sequence` (
  `id`, `code_namespace`, `next_value`, `version`, `creator`, `updater`, `deleted`, `tenant_id`
)
SELECT 920100, 'ROOT:920001', 6, 0, 'seed', 'seed', b'0', 0
WHERE NOT EXISTS (
  SELECT 1
  FROM `proj_project_code_sequence`
  WHERE tenant_id = 0 AND code_namespace = 'ROOT:920001'
);
