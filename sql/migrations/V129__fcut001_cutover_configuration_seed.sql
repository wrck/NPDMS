-- F-CUT-001 dictionaries: retain legacy values and add the ten/five stable CUT-07 options.
INSERT IGNORE INTO `system_dict_type`
(`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
VALUES
(2990, 'PMS-设备类型', 'pms_device_type', 0, 'CUT-07设备类型主字典，支持系统管理员扩展', 'seed', NOW(), 'seed', NOW(), b'0', NULL);

INSERT IGNORE INTO `system_dict_data`
(`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(22900, 11, '设备替换（原厂新旧整机）', 'DEVICE_REPLACE_WHOLE', 'pms_cutover_type', 0, 'primary', '', 'CUT-07', 'seed', NOW(), 'seed', NOW(), b'0'),
(22901, 12, '设备替换（原厂新旧板卡）', 'DEVICE_REPLACE_BOARD', 'pms_cutover_type', 0, 'primary', '', 'CUT-07', 'seed', NOW(), 'seed', NOW(), b'0'),
(22902, 13, '设备替换（替换友商设备）', 'DEVICE_REPLACE_VENDOR', 'pms_cutover_type', 0, 'primary', '', 'CUT-07', 'seed', NOW(), 'seed', NOW(), b'0'),
(22903, 14, '设备入网（新开局）', 'DEVICE_ONBOARD', 'pms_cutover_type', 0, 'success', '', 'CUT-07', 'seed', NOW(), 'seed', NOW(), b'0'),
(22904, 15, '版本升级', 'VERSION_UPGRADE', 'pms_cutover_type', 0, 'warning', '', 'CUT-07', 'seed', NOW(), 'seed', NOW(), b'0'),
(22905, 16, '灾备演练', 'DISASTER_RECOVERY_DRILL', 'pms_cutover_type', 0, 'info', '', 'CUT-07', 'seed', NOW(), 'seed', NOW(), b'0'),
(22906, 17, '配置变更', 'CONFIGURATION_CHANGE', 'pms_cutover_type', 0, 'info', '', 'CUT-07', 'seed', NOW(), 'seed', NOW(), b'0'),
(22907, 18, '网络结构调整', 'NETWORK_TOPOLOGY_CHANGE', 'pms_cutover_type', 0, 'info', '', 'CUT-07', 'seed', NOW(), 'seed', NOW(), b'0'),
(22908, 19, '版本补丁', 'VERSION_PATCH', 'pms_cutover_type', 0, 'warning', '', 'CUT-07', 'seed', NOW(), 'seed', NOW(), b'0'),
(22909, 20, '特征库升级', 'SIGNATURE_UPGRADE', 'pms_cutover_type', 0, 'warning', '', 'CUT-07', 'seed', NOW(), 'seed', NOW(), b'0'),
(22910, 11, 'VSM双机', 'VSM', 'pms_network_mode', 0, 'primary', '', 'CUT-07', 'seed', NOW(), 'seed', NOW(), b'0'),
(22911, 12, '静默双机', 'SILENT_DUAL', 'pms_network_mode', 0, 'primary', '', 'CUT-07', 'seed', NOW(), 'seed', NOW(), b'0'),
(22912, 13, 'DRP双机', 'DRP_DUAL', 'pms_network_mode', 0, 'primary', '', 'CUT-07', 'seed', NOW(), 'seed', NOW(), b'0'),
(22913, 14, '普通双机', 'NORMAL_DUAL', 'pms_network_mode', 0, 'primary', '', 'CUT-07', 'seed', NOW(), 'seed', NOW(), b'0'),
(22914, 15, '集群', 'CLUSTER', 'pms_network_mode', 0, 'warning', '', 'CUT-07', 'seed', NOW(), 'seed', NOW(), b'0'),
(22920, 1, '防火墙', 'FW', 'pms_device_type', 0, 'primary', '', 'CUT-07示例', 'seed', NOW(), 'seed', NOW(), b'0'),
(22921, 2, '交换机', 'SW', 'pms_device_type', 0, 'success', '', 'CUT-07示例', 'seed', NOW(), 'seed', NOW(), b'0'),
(22922, 3, '应用交付设备', 'ADX', 'pms_device_type', 0, 'warning', '', 'CUT-07示例', 'seed', NOW(), 'seed', NOW(), b'0');

INSERT INTO `cut_cutover_configuration_revision`
(`id`, `configuration_code`, `configuration_name`, `revision_no`, `status_code`, `effective_from`,
 `dictionary_snapshot`, `dimension_definition_snapshot`, `plan_template_section_snapshot`,
 `validation_result_snapshot`, `change_summary`, `published_by`, `published_at`, `version`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(970000000000150001, 'CUTOVER_DEFAULT', '割接默认配置', 1, 'PUBLISHED', NOW(),
 JSON_OBJECT('cutoverType', 'pms_cutover_type', 'networkMode', 'pms_network_mode',
             'deviceType', 'pms_device_type', 'cutoverLevel', 'pms_risk_level'),
 JSON_ARRAY(
   JSON_OBJECT('code','CUTOVER_TYPE','name','割接类型','dataType','STRING','valueSource','DICT:pms_cutover_type','owner','CUT','contextPath','task.cutoverType','enabled',true),
   JSON_OBJECT('code','NETWORK_MODE','name','组网模式','dataType','STRING','valueSource','DICT:pms_network_mode','owner','CUT','contextPath','task.networkMode','enabled',true),
   JSON_OBJECT('code','DEVICE_TYPE','name','设备类型','dataType','STRING','valueSource','DICT:pms_device_type','owner','SYSTEM','contextPath','task.deviceType','enabled',true),
   JSON_OBJECT('code','CUTOVER_LEVEL','name','割接等级','dataType','STRING','valueSource','DICT:pms_risk_level','owner','CUT','contextPath','assessment.level','enabled',true)
 ),
 JSON_ARRAY(JSON_OBJECT('stableSectionKey','OVERVIEW','title','割接概述','sortOrder',10,
                        'cutoverTypeCodes',JSON_ARRAY(),'levelCodes',JSON_ARRAY('A','B','C','D'),'required',true)),
 JSON_ARRAY(), 'F-CUT-001正式字典及最小可独立示例配置', 1, NOW(), 0,
 'seed', NOW(), 'seed', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE
 `configuration_name`=VALUES(`configuration_name`), `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `cut_cutover_checklist_item_definition_revision`
(`id`, `configuration_revision_id`, `stable_item_key`, `item_definition_version`, `item_type_code`,
 `item_name`, `item_description`, `interface_format_code`, `interface_schema`, `feedback_format_code`,
 `required_flag`, `work_mode_code`, `external_source_config`, `subtable_code`, `status_code`, `sort_order`,
 `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(970000000000150101, 970000000000150001, 'SURVEY_CUTOVER_BACKGROUND', 1, 'BUSINESS_SURVEY',
 '割接背景', '描述本次割接背景', 'INPUT', JSON_OBJECT('maxLength',1000), 'TEXT', b'1', 'MANUAL', NULL, NULL,
 'ENABLED', 10, 0, 'seed', NOW(), 'seed', NOW(), b'0', 1),
(970000000000150102, 970000000000150001, 'RISK_SYSTEM_LOG', 1, 'RISK',
 '系统日志告警检查', '检查割接前系统日志与告警', 'TABLE', JSON_OBJECT(), 'BOOLEAN_REMARK', b'1', 'MANUAL', NULL, NULL,
 'ENABLED', 20, 0, 'seed', NOW(), 'seed', NOW(), b'0', 1),
(970000000000150103, 970000000000150001, 'DUAL_VSM_CASCADE', 1, 'DUAL_MACHINE_CHECK',
 'VSM级联检查示例', '示例检查项，不代表附件完整清单', 'TABLE', JSON_OBJECT(), 'BOOLEAN_REMARK', b'1', 'MANUAL', NULL, 'VSM',
 'ENABLED', 30, 0, 'seed', NOW(), 'seed', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE `item_name`=VALUES(`item_name`), `item_description`=VALUES(`item_description`),
 `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `cut_cutover_checklist_binding_rule_revision`
(`id`, `configuration_revision_id`, `stable_rule_key`, `item_definition_id`, `item_definition_version`,
 `dimension_condition_snapshot`, `priority`, `status_code`, `version`, `creator`, `create_time`, `updater`,
 `update_time`, `deleted`, `tenant_id`)
VALUES
(970000000000150201, 970000000000150001, 'RULE_SURVEY_ABC', 970000000000150101, 1,
 JSON_OBJECT('CUTOVER_LEVEL', JSON_ARRAY('A','B','C')), 10, 'ENABLED', 0, 'seed', NOW(), 'seed', NOW(), b'0', 1),
(970000000000150202, 970000000000150001, 'RULE_DUAL_VSM', 970000000000150103, 1,
 JSON_OBJECT('NETWORK_MODE', JSON_ARRAY('VSM')), 20, 'ENABLED', 0, 'seed', NOW(), 'seed', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE `dimension_condition_snapshot`=VALUES(`dimension_condition_snapshot`),
 `priority`=VALUES(`priority`), `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(199500, '割接配置', 'pms:cutover-config:query', 2, 66, 18000, 'cutover-config', 'ep:setting',
 'pms/cutover/cutover-config/index', 'PmsCutoverConfig', 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(199501, '割接配置查询', 'pms:cutover-config:query', 3, 10, 199500, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(199502, '割接配置维护', 'pms:cutover-config:manage', 3, 20, 199500, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(199503, '割接配置发布', 'pms:cutover-config:publish', 3, 30, 199500, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(199504, '割接配置停用', 'pms:cutover-config:disable', 3, 40, 199500, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`), `type`=VALUES(`type`),
 `sort`=VALUES(`sort`), `parent_id`=VALUES(`parent_id`), `path`=VALUES(`path`), `icon`=VALUES(`icon`),
 `component`=VALUES(`component`), `component_name`=VALUES(`component_name`), `status`=0,
 `visible`=b'1', `updater`='seed', `update_time`=NOW(), `deleted`=b'0';
