-- V256: engineering 模块存量 pms_eng_* 表逐业务对齐领域前缀（eng 非领域修正）
-- 从原始表名一步 RENAME 到最终名（不经中间名，避免错调已划好领域的新实现表）：
--   工程实施领域（SOL/IMP）：{领域}_eng_{实体}（保留 eng_ 段、实体名不变）；
--   非工程实施领域（PLT/KNO/RES）：{领域}_{实体}（无 eng_ 段）。
-- 目标名均与已存在领域新表核验无冲突（sol_site_survey 为 V249 新实现表保持不动）。
-- 字典编码 pms_eng_* 为系统字典数据，不在本迁移范围。

RENAME TABLE
  -- SOL 域（工程实施）
  `pms_eng_site_survey` TO `sol_eng_site_survey`,
  `pms_eng_requirement` TO `sol_eng_requirement`,
  `pms_eng_solution_source` TO `sol_eng_solution_source`,
  `pms_eng_solution` TO `sol_eng_solution`,
  `pms_eng_briefing` TO `sol_eng_briefing`,
  `pms_eng_resource_ready` TO `sol_eng_resource_ready`,
  -- PLT 域（非工程实施）
  `pms_eng_form_template` TO `plt_form_template`,
  `pms_eng_form_instance` TO `plt_form_instance`,
  `pms_eng_authorization` TO `plt_authorization`,
  -- IMP 域（工程实施）
  `pms_eng_arrival` TO `imp_eng_arrival`,
  `pms_eng_installation` TO `imp_eng_installation`,
  `pms_eng_configuration` TO `imp_eng_configuration`,
  `pms_eng_joint_test` TO `imp_eng_joint_test`,
  `pms_eng_issue` TO `imp_eng_issue`,
  `pms_eng_deliverable` TO `imp_eng_deliverable`,
  `pms_eng_material_requisition` TO `imp_eng_material_requisition`,
  `pms_eng_material_exchange` TO `imp_eng_material_exchange`,
  `pms_eng_external_procurement` TO `imp_eng_external_procurement`,
  `pms_eng_doc_template` TO `imp_eng_doc_template`,
  `pms_eng_doc_template_version` TO `imp_eng_doc_template_version`,
  -- KNO 域（非工程实施）
  `pms_eng_announcement` TO `kno_announcement`,
  `pms_eng_announcement_check` TO `kno_announcement_check`,
  -- RES 域（非工程实施）
  `pms_eng_outsource_request` TO `res_outsource_request`;

-- 菜单权限随 URL 领域前缀同步（幂等 REPLACE）
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-site-survey:', 'pms:sol-site-survey:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-site-survey:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-requirement:', 'pms:sol-requirement:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-requirement:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-solution:', 'pms:sol-solution:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-solution:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-resource:', 'pms:sol-resource:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-resource:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-briefing:', 'pms:sol-briefing:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-briefing:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-form-template:', 'pms:plt-form-template:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-form-template:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-form-instance:', 'pms:plt-form-instance:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-form-instance:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-outsource:', 'pms:res-outsource:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-outsource:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-arrival:', 'pms:imp-arrival:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-arrival:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-installation:', 'pms:imp-installation:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-installation:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-configuration:', 'pms:imp-configuration:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-configuration:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-joint-test:', 'pms:imp-joint-test:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-joint-test:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-issue:', 'pms:imp-issue:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-issue:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-deliverable:', 'pms:imp-deliverable:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-deliverable:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-material-req:', 'pms:imp-material-req:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-material-req:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-material-exch:', 'pms:imp-material-exch:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-material-exch:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-ext-proc:', 'pms:imp-ext-proc:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-ext-proc:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-doc-template:', 'pms:imp-doc-template:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-doc-template:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-risk:', 'pms:imp-risk:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-risk:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-announcement-check:', 'pms:kno-announcement-check:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-announcement-check:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-announcement:', 'pms:kno-announcement:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-announcement:%' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = REPLACE(`permission`, 'pms:eng-authorization:', 'pms:plt-authorization:'), `update_time` = NOW() WHERE `permission` LIKE 'pms:eng-authorization:%' AND `deleted` = b'0';
