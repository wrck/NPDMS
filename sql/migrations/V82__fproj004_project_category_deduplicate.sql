-- F-PROJ-004 / PM-07：收敛 V52 与 V81 重复的启用项目类别字典项。
-- 保留 V52 记录作为历史证据，仅停用；V81 高段记录作为当前唯一启用值。

UPDATE `system_dict_data`
SET `status` = 1,
    `remark` = '历史重复值；由F-PROJ-004高段字典项承接',
    `updater` = 'seed',
    `update_time` = NOW()
WHERE `dict_type` = 'pms_project_category'
  AND `value` = 'GENERAL'
  AND `id` <> 992004020001
  AND `deleted` = b'0';

UPDATE `system_dict_data`
SET `status` = 1,
    `remark` = '历史重复值；由F-PROJ-004高段字典项承接',
    `updater` = 'seed',
    `update_time` = NOW()
WHERE `dict_type` = 'pms_project_category'
  AND `value` = 'ENGINEERING'
  AND `id` <> 992004020002
  AND `deleted` = b'0';
