-- V252: 前端目录随领域归属修正归位（ACC 域业务迁 pms/acceptance、工期倒排迁 pms/engineering）
-- component 路径与前端 Vue 文件实际路径保持一致；幂等，可重复执行。
UPDATE `system_menu`
SET `component` = 'pms/acceptance/acceptance/index', `update_time` = NOW()
WHERE `component` = 'pms/project/acceptance/index' AND `deleted` = b'0';

UPDATE `system_menu`
SET `component` = 'pms/acceptance/acceptance-report/index', `update_time` = NOW()
WHERE `component` = 'pms/project/acceptance-report/index' AND `deleted` = b'0';

UPDATE `system_menu`
SET `component` = 'pms/acceptance/completion-certificate/index', `update_time` = NOW()
WHERE `component` = 'pms/project/completion-certificate/index' AND `deleted` = b'0';

UPDATE `system_menu`
SET `component` = 'pms/acceptance/deliverable-checklist/index', `update_time` = NOW()
WHERE `component` = 'pms/project/deliverable-checklist/index' AND `deleted` = b'0';

UPDATE `system_menu`
SET `component` = 'pms/acceptance/archive-document/index', `update_time` = NOW()
WHERE `component` = 'pms/project/archive-document/index' AND `deleted` = b'0';

UPDATE `system_menu`
SET `component` = 'pms/engineering/schedule-backward/index', `update_time` = NOW()
WHERE `component` = 'pms/project/schedule-backward/index' AND `deleted` = b'0';
