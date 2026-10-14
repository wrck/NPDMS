-- P6 uses the declared CUT query/save/submit permissions in both the UI and API.
-- Register the permission catalog only; existing role assignments remain unchanged.
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT source.`id`, source.`name`, source.`permission`, 3, source.`sort`, 970445001,
       '', '', NULL, NULL, 0, b'1', b'1', b'1',
       'cut-special-20261013', NOW(), 'cut-special-20261013', NOW(), b'0'
FROM (
    SELECT 970462001 AS `id`, '查询割接闭环' AS `name`,
           'pms:cutover-task:query-closure' AS `permission`, 80 AS `sort`
    UNION ALL SELECT 970462002, '保存割接闭环', 'pms:cutover-task:save-closure', 81
    UNION ALL SELECT 970462003, '提交割接闭环', 'pms:cutover-task:submit-closure', 82
) source
WHERE EXISTS (SELECT 1 FROM `system_menu` parent WHERE parent.`id` = 970445001 AND parent.`deleted` = b'0')
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu` existing
      WHERE existing.`id` = source.`id`
         OR (existing.`permission` = source.`permission` AND existing.`deleted` = b'0')
  );
