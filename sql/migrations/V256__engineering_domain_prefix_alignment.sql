-- V256: engineering 模块存量 pms_eng_* 表逐业务对齐领域前缀（eng 非领域修正）
-- 判定依据 02/08a/09 分册：SOL（工勘/需求/方案/交底/资源）、IMP（到货/安装/配置/联调/问题/交付件/物料/采购/文档模板）、
-- KNO（公告）、RES（外协）、PLT（动态表单/设备授权，09 分册 DAC 凭证用 plt_ 前缀先例）。
-- 09 已声明且未被占用的目标表名直接采用（installation/configuration/joint_test 为当前承载前向对齐）；
-- 已被新链占用的用领域前缀直译（arrival/deliverable）；字典编码 pms_eng_* 为系统字典数据，不在本迁移范围。

RENAME TABLE
  -- SOL 域
  `pms_eng_site_survey` TO `sol_site_survey`,
  `pms_eng_requirement` TO `sol_requirement`,
  `pms_eng_solution_source` TO `sol_solution_source`,
  `pms_eng_solution` TO `sol_solution`,
  `pms_eng_briefing` TO `sol_briefing`,
  `pms_eng_resource_ready` TO `sol_resource_ready`,
  -- PLT 域（动态表单旧链与设备授权旧链）
  `pms_eng_form_template` TO `plt_form_template`,
  `pms_eng_form_instance` TO `plt_form_instance`,
  `pms_eng_authorization` TO `plt_authorization`,
  -- IMP 域
  `pms_eng_arrival` TO `imp_arrival`,
  `pms_eng_installation` TO `imp_installation_record`,
  `pms_eng_configuration` TO `imp_configuration_collection_result`,
  `pms_eng_joint_test` TO `imp_joint_debugging_result`,
  `pms_eng_issue` TO `imp_issue`,
  `pms_eng_deliverable` TO `imp_deliverable`,
  `pms_eng_material_requisition` TO `imp_material_requisition`,
  `pms_eng_material_exchange` TO `imp_material_exchange`,
  `pms_eng_external_procurement` TO `imp_external_procurement`,
  `pms_eng_doc_template` TO `imp_doc_template`,
  `pms_eng_doc_template_version` TO `imp_doc_template_version`,
  -- KNO 域
  `pms_eng_announcement` TO `kno_announcement`,
  `pms_eng_announcement_check` TO `kno_announcement_check`,
  -- RES 域
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
