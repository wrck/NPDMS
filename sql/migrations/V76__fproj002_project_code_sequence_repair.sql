-- F-PROJ-002：根据既有项目事实补齐子项目编码命名空间流水。
-- 种子或历史导入可能已占用 project_sequence，但未同步创建 ROOT:<code_root_id> 流水行；
-- 下一次拆分必须从当前最大序号之后继续，且不得降低已有运行时流水。
INSERT INTO `proj_project_code_sequence` (
  `code_namespace`, `next_value`, `version`, `creator`, `updater`, `deleted`, `tenant_id`
)
SELECT
  CONCAT('ROOT:', project_group.code_root_id),
  project_group.next_value,
  0,
  'seed',
  'seed',
  b'0',
  project_group.tenant_id
FROM (
  SELECT
    `tenant_id`,
    `code_root_id`,
    MAX(`project_sequence`) + 1 AS next_value
  FROM `proj_project`
  WHERE `deleted` = b'0'
    AND `code_root_id` IS NOT NULL
    AND `project_sequence` IS NOT NULL
  GROUP BY `tenant_id`, `code_root_id`
) project_group
ON DUPLICATE KEY UPDATE
  `next_value` = GREATEST(`proj_project_code_sequence`.`next_value`, VALUES(`next_value`)),
  `updater` = 'seed',
  `deleted` = b'0';
