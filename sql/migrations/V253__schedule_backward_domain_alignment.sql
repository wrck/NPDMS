-- V253: 工期倒排对齐 engineering 领域规则（归属修正后续）
-- 表名 pms_schedule_backward* -> pms_eng_schedule_backward*（对齐模块 pms_eng_* 命名）
-- 菜单权限 pms:schedule-backward:* -> pms:eng-schedule-backward:*（对齐模块 pms:eng-* 权限规则）
-- URL /pms/schedule-backward -> /pms/eng-schedule-backward 由代码承载，本脚本只处理表与菜单。

RENAME TABLE
  `pms_schedule_backward` TO `pms_eng_schedule_backward`,
  `pms_schedule_backward_item` TO `pms_eng_schedule_backward_item`;

UPDATE `system_menu`
SET `permission` = 'pms:eng-schedule-backward:query', `update_time` = NOW()
WHERE `permission` = 'pms:schedule-backward:query' AND `deleted` = b'0';

UPDATE `system_menu`
SET `permission` = 'pms:eng-schedule-backward:create', `update_time` = NOW()
WHERE `permission` = 'pms:schedule-backward:create' AND `deleted` = b'0';

UPDATE `system_menu`
SET `permission` = 'pms:eng-schedule-backward:update', `update_time` = NOW()
WHERE `permission` = 'pms:schedule-backward:update' AND `deleted` = b'0';

UPDATE `system_menu`
SET `permission` = 'pms:eng-schedule-backward:delete', `update_time` = NOW()
WHERE `permission` = 'pms:schedule-backward:delete' AND `deleted` = b'0';

UPDATE `system_menu`
SET `permission` = 'pms:eng-schedule-backward:calculate', `update_time` = NOW()
WHERE `permission` = 'pms:schedule-backward:calculate' AND `deleted` = b'0';

UPDATE `system_menu`
SET `permission` = 'pms:eng-schedule-backward:apply', `update_time` = NOW()
WHERE `permission` = 'pms:schedule-backward:apply' AND `deleted` = b'0';
