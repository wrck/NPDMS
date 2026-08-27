-- =============================================================================
-- F-SOL-003 / PRE-04：固定目录、代表性WorkBinding与稳定权限。
-- 新权限不自动授予角色；选择项运行时只消费发布时冻结的code/label。
-- =============================================================================

INSERT INTO `system_dict_type`
(`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`,
 `updater`, `update_time`, `deleted`, `deleted_time`)
SELECT 992103010001, 'PRE-04扩展示例', 'pms_requirement_analysis_extension_demo', 0,
       'F-SOL-003发布冻结与停用拒绝验收字典',
       'seed', NOW(), 'seed', NOW(), b'0', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type`
    WHERE `type` = 'pms_requirement_analysis_extension_demo' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data`
(`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`,
 `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(992103020001, 10, '标准等级', 'STANDARD', 'pms_requirement_analysis_extension_demo', 0,
 'primary', '', '启用选项，供单选/多选冻结验收', 'seed', NOW(), 'seed', NOW(), b'0'),
(992103020002, 20, '增强等级', 'ENHANCED', 'pms_requirement_analysis_extension_demo', 0,
 'success', '', '启用选项，供单选/多选冻结验收', 'seed', NOW(), 'seed', NOW(), b'0'),
(992103020003, 30, '已停用等级', 'DISABLED_OPTION', 'pms_requirement_analysis_extension_demo', 1,
 'info', '', '停用选项，发布必须拒绝', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `sort` = VALUES(`sort`), `label` = VALUES(`label`),
  `status` = VALUES(`status`), `remark` = VALUES(`remark`),
  `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0';

-- config_key是目录身份，但上游仅有普通索引：重复既有键时失败，单行则按键恢复。
CREATE TEMPORARY TABLE `_v100_requirement_catalog_key_guard` (
    `singleton` TINYINT NOT NULL PRIMARY KEY
);

INSERT INTO `_v100_requirement_catalog_key_guard` (`singleton`)
SELECT 1
FROM `infra_config`
WHERE `config_key` = 'pms.sol.requirement-analysis.catalog.v1';

INSERT INTO `infra_config`
(`category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'pms', 1, 'PRE-04固定V1章节目录',
       'pms.sol.requirement-analysis.catalog.v1',
       '{"schemaVersion":1,"catalogCode":"PRE_04_REQUIREMENT_ANALYSIS","catalogVersion":1,"sections":[["PROJECT_BACKGROUND","项目背景",true],["PROJECT_OBJECTIVE","项目目标",true],["NETWORK_TOPOLOGY","网络拓扑",true],["TRANSMISSION_REQUIREMENT","传输需求",false],["TRAFFIC_REQUIREMENT","流量需求",false],["BUSINESS_REQUIREMENT","业务需求",false],["IP_PLANNING","IP规划",false],["REDUNDANCY_REQUIREMENT","冗余需求",false],["SECURITY_PROTECTION","安全防护",false],["OPERATIONS_REQUIREMENT","运维需求",false],["LOGGING_REQUIREMENT","日志需求",false]]}',
       b'0', 'F-SOL-003固定目录展示种子；SOL核心规则仍由代码契约持有',
       'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_config`
    WHERE `config_key` = 'pms.sol.requirement-analysis.catalog.v1'
);

UPDATE `infra_config`
SET `category` = 'pms',
    `type` = 1,
    `name` = 'PRE-04固定V1章节目录',
    `value` = '{"schemaVersion":1,"catalogCode":"PRE_04_REQUIREMENT_ANALYSIS","catalogVersion":1,"sections":[["PROJECT_BACKGROUND","项目背景",true],["PROJECT_OBJECTIVE","项目目标",true],["NETWORK_TOPOLOGY","网络拓扑",true],["TRANSMISSION_REQUIREMENT","传输需求",false],["TRAFFIC_REQUIREMENT","流量需求",false],["BUSINESS_REQUIREMENT","业务需求",false],["IP_PLANNING","IP规划",false],["REDUNDANCY_REQUIREMENT","冗余需求",false],["SECURITY_PROTECTION","安全防护",false],["OPERATIONS_REQUIREMENT","运维需求",false],["LOGGING_REQUIREMENT","日志需求",false]]}',
    `visible` = b'0',
    `remark` = 'F-SOL-003固定目录展示种子；SOL核心规则仍由代码契约持有',
    `updater` = 'seed',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `config_key` = 'pms.sol.requirement-analysis.catalog.v1';

DROP TEMPORARY TABLE `_v100_requirement_catalog_key_guard`;

-- 独立高段示例，不修改任何既有PUBLISHED版本。两条ACTIVE低优先级模板可显式选择；
-- DRAFT负向候选用于验证停用字典拒绝，不参与项目匹配。
INSERT INTO `proj_project_template`
(`id`, `code`, `name`, `status`, `match_priority`, `description`, `system_reserved`,
 `creator`, `updater`, `tenant_id`)
VALUES
(992103040001, 'TPL-PRE04-NO-EXT', 'PRE-04无扩展示例', 'ACTIVE', 900,
 'F-SOL-003无扩展精确WorkBinding示例', b'0', 'seed', 'seed', 0),
(992103040002, 'TPL-PRE04-ALL-TYPES', 'PRE-04全类型扩展示例', 'ACTIVE', 910,
 'F-SOL-003六种扩展字段类型WorkBinding示例', b'0', 'seed', 'seed', 0),
(992103040003, 'TPL-PRE04-DISABLED', 'PRE-04停用字典负向示例', 'DRAFT', 920,
 'F-SOL-003发布拒绝停用字典选项示例', b'0', 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `proj_project_template_revision`
(`id`, `template_id`, `revision_no`, `status`, `signing_method`, `project_category`,
 `implementation_method`, `major_project_level`, `process_definition_key`,
 `process_definition_version`, `validation_summary`, `published_by`, `published_time`,
 `creator`, `updater`, `tenant_id`)
VALUES
(992103050001, 992103040001, 1, 'PUBLISHED', 'DIRECT_SIGN', 'ENGINEERING',
 'DIRECT_SERVICE', NULL, 'PROC-PMS-DELIVERY-STD', 'v2', 'F-SOL-003合法无扩展种子',
 'seed', NOW(), 'seed', 'seed', 0),
(992103050002, 992103040002, 1, 'PUBLISHED', 'CHANNEL_SIGN', 'GENERAL',
 NULL, NULL, 'PROC-PMS-DELIVERY-STD', 'v2', 'F-SOL-003合法全类型种子',
 'seed', NOW(), 'seed', 'seed', 0),
(992103050003, 992103040003, 0, 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL,
 NULL, NULL, NULL, 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `proj_project_template_stage_definition`
(`template_revision_id`, `stage_code`, `name`, `sort_order`, `entry_criteria`, `exit_criteria`,
 `creator`, `updater`, `tenant_id`)
VALUES
(992103050001, 'S0', '项目启动', 0, '项目创建', '启动准备完成', 'seed', 'seed', 0),
(992103050001, 'S1', '工前准备', 1, '项目进入S1', '需求分析完成', 'seed', 'seed', 0),
(992103050001, 'S2', '施工计划', 2, '工前准备完成', '施工计划完成', 'seed', 'seed', 0),
(992103050001, 'S3', '方案编审', 3, '施工计划完成', '实施方案完成', 'seed', 'seed', 0),
(992103050001, 'S4', '实施部署', 4, '方案编审完成', '实施部署完成', 'seed', 'seed', 0),
(992103050001, 'S5', '验收交维', 5, '实施部署完成', '验收交维完成', 'seed', 'seed', 0),
(992103050001, 'S6', '项目闭环', 6, '验收交维完成', '项目闭环完成', 'seed', 'seed', 0),
(992103050002, 'S0', '项目启动', 0, '项目创建', '启动准备完成', 'seed', 'seed', 0),
(992103050002, 'S1', '工前准备', 1, '项目进入S1', '需求分析完成', 'seed', 'seed', 0),
(992103050002, 'S2', '施工计划', 2, '工前准备完成', '施工计划完成', 'seed', 'seed', 0),
(992103050002, 'S3', '方案编审', 3, '施工计划完成', '实施方案完成', 'seed', 'seed', 0),
(992103050002, 'S4', '实施部署', 4, '方案编审完成', '实施部署完成', 'seed', 'seed', 0),
(992103050002, 'S5', '验收交维', 5, '实施部署完成', '验收交维完成', 'seed', 'seed', 0),
(992103050002, 'S6', '项目闭环', 6, '验收交维完成', '项目闭环完成', 'seed', 'seed', 0),
(992103050003, 'S1', '工前准备', 1, '项目进入S1', '需求分析完成', 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

INSERT INTO `proj_project_template_task_definition`
(`template_revision_id`, `stage_definition_key`, `task_definition_key`, `task_code`, `name`,
 `stage_code`, `priority`, `sort_order`, `description`, `work_binding_type_code`, `binding_config`,
 `permission_policy_ref`, `completion_rule_type_code`, `completion_rule_config`, `definition_version`,
 `creator`, `updater`, `tenant_id`)
VALUES
(992103050001, 'S0', 'T-START', 'T-START', '项目启动准备', 'S0', 2, 10, '完成项目启动准备',
 'TASK_NATIVE', '{"schemaVersion":1}', 'PROJECT_TASK_NATIVE_DEFAULT', 'TASK_NATIVE_STATUS',
 '{"schemaVersion":1,"requiredStatus":"COMPLETED"}', 1, 'seed', 'seed', 0),
(992103050001, 'S2', 'T-PLAN', 'T-PLAN', '施工计划编制', 'S2', 2, 10, '完成施工计划',
 'TASK_NATIVE', '{"schemaVersion":1}', 'PROJECT_TASK_NATIVE_DEFAULT', 'TASK_NATIVE_STATUS',
 '{"schemaVersion":1,"requiredStatus":"COMPLETED"}', 1, 'seed', 'seed', 0),
(992103050001, 'S3', 'T-DESIGN', 'T-DESIGN', '实施方案编审', 'S3', 2, 10, '完成实施方案编审',
 'TASK_NATIVE', '{"schemaVersion":1}', 'PROJECT_TASK_NATIVE_DEFAULT', 'TASK_NATIVE_STATUS',
 '{"schemaVersion":1,"requiredStatus":"COMPLETED"}', 1, 'seed', 'seed', 0),
(992103050001, 'S4', 'T-DEPLOY', 'T-DEPLOY', '实施部署', 'S4', 2, 10, '完成实施部署',
 'TASK_NATIVE', '{"schemaVersion":1}', 'PROJECT_TASK_NATIVE_DEFAULT', 'TASK_NATIVE_STATUS',
 '{"schemaVersion":1,"requiredStatus":"COMPLETED"}', 1, 'seed', 'seed', 0),
(992103050001, 'S5', 'T-ACCEPT', 'T-ACCEPT', '验收交维', 'S5', 2, 10, '完成验收交维',
 'TASK_NATIVE', '{"schemaVersion":1}', 'PROJECT_TASK_NATIVE_DEFAULT', 'TASK_NATIVE_STATUS',
 '{"schemaVersion":1,"requiredStatus":"COMPLETED"}', 1, 'seed', 'seed', 0),
(992103050001, 'S6', 'T-CLOSE', 'T-CLOSE', '项目闭环', 'S6', 2, 10, '完成项目闭环',
 'TASK_NATIVE', '{"schemaVersion":1}', 'PROJECT_TASK_NATIVE_DEFAULT', 'TASK_NATIVE_STATUS',
 '{"schemaVersion":1,"requiredStatus":"COMPLETED"}', 1, 'seed', 'seed', 0),
(992103050002, 'S0', 'T-START', 'T-START', '项目启动准备', 'S0', 2, 10, '完成项目启动准备',
 'TASK_NATIVE', '{"schemaVersion":1}', 'PROJECT_TASK_NATIVE_DEFAULT', 'TASK_NATIVE_STATUS',
 '{"schemaVersion":1,"requiredStatus":"COMPLETED"}', 1, 'seed', 'seed', 0),
(992103050002, 'S2', 'T-PLAN', 'T-PLAN', '施工计划编制', 'S2', 2, 10, '完成施工计划',
 'TASK_NATIVE', '{"schemaVersion":1}', 'PROJECT_TASK_NATIVE_DEFAULT', 'TASK_NATIVE_STATUS',
 '{"schemaVersion":1,"requiredStatus":"COMPLETED"}', 1, 'seed', 'seed', 0),
(992103050002, 'S3', 'T-DESIGN', 'T-DESIGN', '实施方案编审', 'S3', 2, 10, '完成实施方案编审',
 'TASK_NATIVE', '{"schemaVersion":1}', 'PROJECT_TASK_NATIVE_DEFAULT', 'TASK_NATIVE_STATUS',
 '{"schemaVersion":1,"requiredStatus":"COMPLETED"}', 1, 'seed', 'seed', 0),
(992103050002, 'S4', 'T-DEPLOY', 'T-DEPLOY', '实施部署', 'S4', 2, 10, '完成实施部署',
 'TASK_NATIVE', '{"schemaVersion":1}', 'PROJECT_TASK_NATIVE_DEFAULT', 'TASK_NATIVE_STATUS',
 '{"schemaVersion":1,"requiredStatus":"COMPLETED"}', 1, 'seed', 'seed', 0),
(992103050002, 'S5', 'T-ACCEPT', 'T-ACCEPT', '验收交维', 'S5', 2, 10, '完成验收交维',
 'TASK_NATIVE', '{"schemaVersion":1}', 'PROJECT_TASK_NATIVE_DEFAULT', 'TASK_NATIVE_STATUS',
 '{"schemaVersion":1,"requiredStatus":"COMPLETED"}', 1, 'seed', 'seed', 0),
(992103050002, 'S6', 'T-CLOSE', 'T-CLOSE', '项目闭环', 'S6', 2, 10, '完成项目闭环',
 'TASK_NATIVE', '{"schemaVersion":1}', 'PROJECT_TASK_NATIVE_DEFAULT', 'TASK_NATIVE_STATUS',
 '{"schemaVersion":1,"requiredStatus":"COMPLETED"}', 1, 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

INSERT INTO `proj_project_template_task_definition`
(`template_revision_id`, `stage_definition_key`, `task_definition_key`, `parent_task_definition_key`,
 `task_code`, `name`, `parent_task_code`, `stage_code`, `priority`, `sort_order`, `estimated_hours`,
 `satisfaction_timing`, `description`, `work_binding_type_code`, `target_context_code`,
 `target_object_type`, `target_object_key`, `component_key`, `dynamic_form_revision_id`,
 `approval_definition_key`, `binding_config`, `permission_policy_ref`, `completion_rule_type_code`,
 `completion_rule_config`, `gate_ref`, `definition_version`, `creator`, `create_time`, `updater`,
 `update_time`, `deleted`, `deleted_time`, `tenant_id`)
VALUES
(992103050001, 'S1', 'T-REQ-ANALYSIS', NULL, 'T-REQ-ANALYSIS', '需求分析', NULL, 'S1', 2, 10, 8.0,
 NULL, 'PRE-04无扩展精确命中', 'BUSINESS_OBJECT', 'SOL', 'REQUIREMENT_ANALYSIS',
 'PRE_04_REQUIREMENT_ANALYSIS', NULL, NULL, NULL,
 '{"schemaVersion":1,"catalogCode":"PRE_04_REQUIREMENT_ANALYSIS","catalogVersion":1,"extensionItems":[]}',
 'PRE_04_REQUIREMENT_ANALYSIS_DEFAULT', 'BUSINESS_OBJECT_STATUS',
 '{"schemaVersion":1,"requiredStatus":"COMPLETED"}', NULL, 1,
 'seed', NOW(), 'seed', NOW(), b'0', NULL, 0),
(992103050002, 'S1', 'T-REQ-ANALYSIS', NULL, 'T-REQ-ANALYSIS', '需求分析', NULL, 'S1', 2, 10, 8.0,
 NULL, 'PRE-04全部扩展字段类型', 'BUSINESS_OBJECT', 'SOL', 'REQUIREMENT_ANALYSIS',
 'PRE_04_REQUIREMENT_ANALYSIS', NULL, NULL, NULL,
 '{"schemaVersion":1,"catalogCode":"PRE_04_REQUIREMENT_ANALYSIS","catalogVersion":1,"extensionItems":[{"fieldCode":"CUSTOM_RICH_TEXT","fieldName":"补充说明","fieldTypeCode":"RICH_TEXT","required":false,"sortOrder":210},{"fieldCode":"CUSTOM_TEXT","fieldName":"联系人说明","fieldTypeCode":"TEXT","required":false,"sortOrder":220},{"fieldCode":"CUSTOM_NUMBER","fieldName":"容量指标","fieldTypeCode":"NUMBER","required":false,"sortOrder":230},{"fieldCode":"CUSTOM_BOOLEAN","fieldName":"是否具备扩容条件","fieldTypeCode":"BOOLEAN","required":false,"sortOrder":240},{"fieldCode":"CUSTOM_SINGLE_SELECT","fieldName":"保障等级","fieldTypeCode":"SINGLE_SELECT","required":true,"dictionaryType":"pms_requirement_analysis_extension_demo","optionSnapshot":[{"code":"ENHANCED","label":"增强等级"},{"code":"STANDARD","label":"标准等级"}],"sortOrder":250},{"fieldCode":"CUSTOM_MULTI_SELECT","fieldName":"适用等级","fieldTypeCode":"MULTI_SELECT","required":false,"dictionaryType":"pms_requirement_analysis_extension_demo","optionSnapshot":[{"code":"ENHANCED","label":"增强等级"},{"code":"STANDARD","label":"标准等级"}],"sortOrder":260}]}',
 'PRE_04_REQUIREMENT_ANALYSIS_DEFAULT', 'BUSINESS_OBJECT_STATUS',
 '{"schemaVersion":1,"requiredStatus":"COMPLETED"}', NULL, 1,
 'seed', NOW(), 'seed', NOW(), b'0', NULL, 0),
(992103050003, 'S1', 'T-REQ-ANALYSIS-DISABLED-DEMO', NULL,
 'T-REQ-ANALYSIS-DISABLED-DEMO', '需求分析停用字典负向候选', NULL, 'S1', 3, 999, 1.0,
 NULL, '仅用于验证发布拒绝停用字典选项', 'BUSINESS_OBJECT', 'SOL',
 'REQUIREMENT_ANALYSIS', 'PRE_04_REQUIREMENT_ANALYSIS', NULL, NULL, NULL,
 '{"schemaVersion":1,"catalogCode":"PRE_04_REQUIREMENT_ANALYSIS","catalogVersion":1,"extensionItems":[{"fieldCode":"DISABLED_SELECT_DEMO","fieldName":"停用选项负向","fieldTypeCode":"SINGLE_SELECT","required":false,"dictionaryType":"pms_requirement_analysis_extension_demo","optionSnapshot":[{"code":"DISABLED_OPTION","label":"已停用等级"}],"sortOrder":990}]}',
 'PRE_04_REQUIREMENT_ANALYSIS_DEFAULT', 'BUSINESS_OBJECT_STATUS',
 '{"schemaVersion":1,"requiredStatus":"COMPLETED"}', NULL, 1,
 'seed', NOW(), 'seed', NOW(), b'0', NULL, 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

INSERT INTO `proj_project_template_milestone_definition`
(`template_revision_id`, `milestone_code`, `name`, `stage_code`, `timing`, `criteria`,
 `creator`, `updater`, `tenant_id`)
VALUES
(992103050001, 'M-REQ-ANALYSIS', '需求分析完成', 'S1', '需求分析完成后', '形成当前有效需求分析版本', 'seed', 'seed', 0),
(992103050001, 'M-CLOSED', '项目闭环', 'S6', '闭环任务完成后', '项目闭环条件满足', 'seed', 'seed', 0),
(992103050002, 'M-REQ-ANALYSIS', '需求分析完成', 'S1', '需求分析完成后', '形成当前有效需求分析版本', 'seed', 'seed', 0),
(992103050002, 'M-CLOSED', '项目闭环', 'S6', '闭环任务完成后', '项目闭环条件满足', 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

INSERT INTO `proj_project_template_deliverable_definition`
(`template_revision_id`, `deliverable_code`, `name`, `stage_code`, `task_code`, `required`,
 `creator`, `updater`, `tenant_id`)
VALUES
(992103050001, 'D-REQ-ANALYSIS', '需求分析完成版', 'S1', 'T-REQ-ANALYSIS', b'1', 'seed', 'seed', 0),
(992103050001, 'D-CLOSE', '项目闭环记录', 'S6', 'T-CLOSE', b'1', 'seed', 'seed', 0),
(992103050002, 'D-REQ-ANALYSIS', '需求分析完成版', 'S1', 'T-REQ-ANALYSIS', b'1', 'seed', 'seed', 0),
(992103050002, 'D-CLOSE', '项目闭环记录', 'S6', 'T-CLOSE', b'1', 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

INSERT INTO `proj_project_template_gate_definition`
(`template_revision_id`, `gate_code`, `name`, `gate_type`, `stage_code`, `description`,
 `creator`, `updater`, `tenant_id`)
VALUES
(992103050001, 'G-S0-EXIT', '启动准出', 'EXIT', 'S0', '启动任务完成', 'seed', 'seed', 0),
(992103050001, 'G-S1-EXIT', '工前准出', 'EXIT', 'S1', '需求分析完成', 'seed', 'seed', 0),
(992103050001, 'G-S2-EXIT', '计划准出', 'EXIT', 'S2', '施工计划完成', 'seed', 'seed', 0),
(992103050001, 'G-S3-EXIT', '方案准出', 'EXIT', 'S3', '实施方案完成', 'seed', 'seed', 0),
(992103050001, 'G-S4-EXIT', '实施准出', 'EXIT', 'S4', '实施部署完成', 'seed', 'seed', 0),
(992103050001, 'G-S5-EXIT', '验收准出', 'EXIT', 'S5', '验收交维完成', 'seed', 'seed', 0),
(992103050001, 'G-S6-EXIT', '闭环准出', 'EXIT', 'S6', '项目闭环完成', 'seed', 'seed', 0),
(992103050002, 'G-S0-EXIT', '启动准出', 'EXIT', 'S0', '启动任务完成', 'seed', 'seed', 0),
(992103050002, 'G-S1-EXIT', '工前准出', 'EXIT', 'S1', '需求分析完成', 'seed', 'seed', 0),
(992103050002, 'G-S2-EXIT', '计划准出', 'EXIT', 'S2', '施工计划完成', 'seed', 'seed', 0),
(992103050002, 'G-S3-EXIT', '方案准出', 'EXIT', 'S3', '实施方案完成', 'seed', 'seed', 0),
(992103050002, 'G-S4-EXIT', '实施准出', 'EXIT', 'S4', '实施部署完成', 'seed', 'seed', 0),
(992103050002, 'G-S5-EXIT', '验收准出', 'EXIT', 'S5', '验收交维完成', 'seed', 'seed', 0),
(992103050002, 'G-S6-EXIT', '闭环准出', 'EXIT', 'S6', '项目闭环完成', 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

INSERT INTO `proj_project_template_gate_reference`
(`template_revision_id`, `gate_code`, `ref_type`, `ref_code`, `ref_version`,
 `creator`, `updater`, `tenant_id`)
VALUES
(992103050001, 'G-S0-EXIT', 'TASK', 'T-START', NULL, 'seed', 'seed', 0),
(992103050001, 'G-S1-EXIT', 'TASK', 'T-REQ-ANALYSIS', NULL, 'seed', 'seed', 0),
(992103050001, 'G-S2-EXIT', 'TASK', 'T-PLAN', NULL, 'seed', 'seed', 0),
(992103050001, 'G-S3-EXIT', 'TASK', 'T-DESIGN', NULL, 'seed', 'seed', 0),
(992103050001, 'G-S4-EXIT', 'TASK', 'T-DEPLOY', NULL, 'seed', 'seed', 0),
(992103050001, 'G-S5-EXIT', 'TASK', 'T-ACCEPT', NULL, 'seed', 'seed', 0),
(992103050001, 'G-S6-EXIT', 'TASK', 'T-CLOSE', NULL, 'seed', 'seed', 0),
(992103050002, 'G-S0-EXIT', 'TASK', 'T-START', NULL, 'seed', 'seed', 0),
(992103050002, 'G-S1-EXIT', 'TASK', 'T-REQ-ANALYSIS', NULL, 'seed', 'seed', 0),
(992103050002, 'G-S2-EXIT', 'TASK', 'T-PLAN', NULL, 'seed', 'seed', 0),
(992103050002, 'G-S3-EXIT', 'TASK', 'T-DESIGN', NULL, 'seed', 'seed', 0),
(992103050002, 'G-S4-EXIT', 'TASK', 'T-DEPLOY', NULL, 'seed', 'seed', 0),
(992103050002, 'G-S5-EXIT', 'TASK', 'T-ACCEPT', NULL, 'seed', 'seed', 0),
(992103050002, 'G-S6-EXIT', 'TASK', 'T-CLOSE', NULL, 'seed', 'seed', 0)
ON DUPLICATE KEY UPDATE `template_revision_id` = `template_revision_id`;

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(198794, '需求分析查询', 'pms:requirement-analysis:query', 3, 200, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198795, '需求分析管理', 'pms:requirement-analysis:manage', 3, 210, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
  `parent_id` = VALUES(`parent_id`), `sort` = VALUES(`sort`), `status` = 0,
  `visible` = b'1', `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0';

-- 新权限由基础平台显式授权；本迁移不写system_role_menu。
