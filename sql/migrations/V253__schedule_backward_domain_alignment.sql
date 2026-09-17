-- V253: 工期倒排对齐 SOL 领域（归属修正：自 project 迁入 engineering 模块的后续）
-- 从原始表名一步 RENAME 到最终领域表名；URL /pms/schedule-backward -> /pms/sol-schedule-backward、
-- 权限 pms:schedule-backward:* -> pms:sol-schedule-backward:* 由代码承载。

RENAME TABLE
  `pms_schedule_backward` TO `sol_schedule_backward`,
  `pms_schedule_backward_item` TO `sol_schedule_backward_item`;

UPDATE `system_menu`
SET `permission` = 'pms:sol-schedule-backward:query', `update_time` = NOW()
WHERE `permission` = 'pms:schedule-backward:query' AND `deleted` = b'0';

UPDATE `system_menu`
SET `permission` = 'pms:sol-schedule-backward:create', `update_time` = NOW()
WHERE `permission` = 'pms:schedule-backward:create' AND `deleted` = b'0';

UPDATE `system_menu`
SET `permission` = 'pms:sol-schedule-backward:update', `update_time` = NOW()
WHERE `permission` = 'pms:schedule-backward:update' AND `deleted` = b'0';

UPDATE `system_menu`
SET `permission` = 'pms:sol-schedule-backward:delete', `update_time` = NOW()
WHERE `permission` = 'pms:schedule-backward:delete' AND `deleted` = b'0';

UPDATE `system_menu`
SET `permission` = 'pms:sol-schedule-backward:calculate', `update_time` = NOW()
WHERE `permission` = 'pms:schedule-backward:calculate' AND `deleted` = b'0';

UPDATE `system_menu`
SET `permission` = 'pms:sol-schedule-backward:apply', `update_time` = NOW()
WHERE `permission` = 'pms:schedule-backward:apply' AND `deleted` = b'0';
