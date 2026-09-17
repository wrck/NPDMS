-- V257: 菜单路由 path 随接口领域前缀切换（表迁移与后端URL/权限已完成，本批收口前端路由层）
-- 与 V256 菜单 permission REPLACE 同一映射；schedule-backward 一并对齐 SOL 领域。幂等。

UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-site-survey', 'sol-site-survey'), `update_time` = NOW() WHERE `path` LIKE 'eng-site-survey%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-requirement', 'sol-requirement'), `update_time` = NOW() WHERE `path` LIKE 'eng-requirement%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-solution', 'sol-solution'), `update_time` = NOW() WHERE `path` LIKE 'eng-solution%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-resource', 'sol-resource'), `update_time` = NOW() WHERE `path` LIKE 'eng-resource%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-briefing', 'sol-briefing'), `update_time` = NOW() WHERE `path` LIKE 'eng-briefing%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-form-template', 'plt-form-template'), `update_time` = NOW() WHERE `path` LIKE 'eng-form-template%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-form-instance', 'plt-form-instance'), `update_time` = NOW() WHERE `path` LIKE 'eng-form-instance%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-outsource', 'res-outsource'), `update_time` = NOW() WHERE `path` LIKE 'eng-outsource%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-arrival', 'imp-arrival'), `update_time` = NOW() WHERE `path` LIKE 'eng-arrival%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-installation', 'imp-installation'), `update_time` = NOW() WHERE `path` LIKE 'eng-installation%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-configuration', 'imp-configuration'), `update_time` = NOW() WHERE `path` LIKE 'eng-configuration%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-joint-test', 'imp-joint-test'), `update_time` = NOW() WHERE `path` LIKE 'eng-joint-test%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-issue', 'imp-issue'), `update_time` = NOW() WHERE `path` LIKE 'eng-issue%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-deliverable', 'imp-deliverable'), `update_time` = NOW() WHERE `path` LIKE 'eng-deliverable%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-material-req', 'imp-material-req'), `update_time` = NOW() WHERE `path` LIKE 'eng-material-req%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-material-exch', 'imp-material-exch'), `update_time` = NOW() WHERE `path` LIKE 'eng-material-exch%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-ext-proc', 'imp-ext-proc'), `update_time` = NOW() WHERE `path` LIKE 'eng-ext-proc%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-doc-template', 'imp-doc-template'), `update_time` = NOW() WHERE `path` LIKE 'eng-doc-template%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-risk', 'imp-risk'), `update_time` = NOW() WHERE `path` LIKE 'eng-risk%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-announcement-check', 'kno-announcement-check'), `update_time` = NOW() WHERE `path` LIKE 'eng-announcement-check%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-announcement', 'kno-announcement'), `update_time` = NOW() WHERE `path` LIKE 'eng-announcement%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'eng-authorization', 'plt-authorization'), `update_time` = NOW() WHERE `path` LIKE 'eng-authorization%' AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = REPLACE(`path`, 'schedule-backward', 'sol-schedule-backward'), `update_time` = NOW() WHERE `path` = 'schedule-backward' AND `deleted` = b'0';
