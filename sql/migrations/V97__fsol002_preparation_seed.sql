-- =============================================================================
-- F-SOL-002 / PRE-02：六类工勘项、固定V1表单目录、示例绑定及稳定权限。
-- 新权限不自动授予角色；只调整seed-owned DRAFT模板的现场工勘任务定义。
-- =============================================================================

INSERT INTO `system_dict_type`
(`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`,
 `updater`, `update_time`, `deleted`, `deleted_time`)
SELECT 992102010001, 'PRE-02工勘项', 'pms_preparation_survey_item_code', 0,
       'F-SOL-002固定V1工勘项编码',
       'seed', NOW(), 'seed', NOW(), b'0', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type`
    WHERE `type` = 'pms_preparation_survey_item_code' AND `deleted` = b'0'
);

UPDATE `system_dict_type`
SET `name` = 'PRE-02工勘项', `status` = 0,
    `remark` = 'F-SOL-002固定V1工勘项编码',
    `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0', `deleted_time` = NULL
WHERE `type` = 'pms_preparation_survey_item_code';

INSERT INTO `system_dict_data`
(`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`,
 `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(992102020001, 10, '供电', 'POWER', 'pms_preparation_survey_item_code', 0, 'primary', '',
 '现场供电条件工勘', 'seed', NOW(), 'seed', NOW(), b'0'),
(992102020002, 20, '网络端口', 'NETWORK_PORT', 'pms_preparation_survey_item_code', 0, 'primary', '',
 '现场网络端口条件工勘', 'seed', NOW(), 'seed', NOW(), b'0'),
(992102020003, 30, '光纤', 'FIBER', 'pms_preparation_survey_item_code', 0, 'primary', '',
 '现场光纤条件工勘', 'seed', NOW(), 'seed', NOW(), b'0'),
(992102020004, 40, '机柜', 'CABINET', 'pms_preparation_survey_item_code', 0, 'primary', '',
 '现场机柜条件工勘', 'seed', NOW(), 'seed', NOW(), b'0'),
(992102020005, 50, '网线', 'NETWORK_CABLE', 'pms_preparation_survey_item_code', 0, 'primary', '',
 '现场网线条件工勘', 'seed', NOW(), 'seed', NOW(), b'0'),
(992102020006, 60, '光模块', 'OPTICAL_MODULE', 'pms_preparation_survey_item_code', 0, 'primary', '',
 '现场光模块条件工勘', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `sort` = VALUES(`sort`), `label` = VALUES(`label`),
  `status` = 0, `color_type` = VALUES(`color_type`), `remark` = VALUES(`remark`),
  `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0';

-- V1固定表单目录只通过既有ConfigApi按稳定键读取；Feature不提供目录编辑入口。
INSERT INTO `infra_config`
(`id`, `category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(992102030001, 'pms', 1, 'PRE-02固定V1表单目录',
 'pms.sol.preparation.site-survey.form-catalog.v1',
 '{"schemaVersion":1,"catalogCode":"PRE_02_SITE_SURVEY","catalogVersion":1,"commonFields":[{"fieldCode":"siteCondition","fieldType":"TEXT","required":true,"maxLength":1000,"sortOrder":10}],"forms":[{"formCode":"POWER","formVersion":1},{"formCode":"NETWORK_PORT","formVersion":1},{"formCode":"FIBER","formVersion":1},{"formCode":"CABINET","formVersion":1},{"formCode":"NETWORK_CABLE","formVersion":1},{"formCode":"OPTICAL_MODULE","formVersion":1}]}',
 b'0', 'F-SOL-002固定V1目录；发布校验和实例化只读并冻结精确Schema',
 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `category` = 'pms', `type` = 1,
  `name` = VALUES(`name`), `value` = VALUES(`value`), `visible` = b'0',
  `remark` = VALUES(`remark`), `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0';

-- 示例配置同时覆盖必需证据、无来源、OA来源、可豁免和停用项。
UPDATE `proj_project_template_task_definition` d
JOIN `proj_project_template_revision` r
  ON r.`tenant_id` = d.`tenant_id`
 AND r.`id` = d.`template_revision_id`
 AND r.`deleted` = b'0'
SET d.`work_binding_type_code` = 'BUSINESS_OBJECT',
    d.`target_context_code` = 'SOL',
    d.`target_object_type` = 'SITE_SURVEY_PREPARATION',
    d.`target_object_key` = 'PRE_02_SITE_SURVEY',
    d.`binding_config` = '{"schemaVersion":1,"preparationTemplateCode":"PRE_02_SITE_SURVEY","preparationTemplateRevision":1,"fixedFormCatalogVersion":1,"itemConfiguration":[{"itemCode":"POWER","itemName":"供电","enabled":true,"formCode":"POWER","formVersion":1,"evidenceRequired":true,"sourceRequirementCode":"NONE","waiverAllowed":false,"approvalRoleCode":"SERVICE_MANAGER_L1","sortOrder":10},{"itemCode":"NETWORK_PORT","itemName":"网络端口","enabled":true,"formCode":"NETWORK_PORT","formVersion":1,"evidenceRequired":false,"sourceRequirementCode":"NONE","waiverAllowed":false,"approvalRoleCode":"SERVICE_MANAGER_L1","sortOrder":20},{"itemCode":"FIBER","itemName":"光纤","enabled":true,"formCode":"FIBER","formVersion":1,"evidenceRequired":true,"sourceRequirementCode":"OA_REQUIRED","waiverAllowed":true,"approvalRoleCode":"SERVICE_MANAGER_L1","sortOrder":30},{"itemCode":"CABINET","itemName":"机柜","enabled":true,"formCode":"CABINET","formVersion":1,"evidenceRequired":true,"sourceRequirementCode":"NONE","waiverAllowed":true,"approvalRoleCode":"SERVICE_MANAGER_L1","sortOrder":40},{"itemCode":"NETWORK_CABLE","itemName":"网线","enabled":true,"formCode":"NETWORK_CABLE","formVersion":1,"evidenceRequired":false,"sourceRequirementCode":"NONE","waiverAllowed":false,"approvalRoleCode":"SERVICE_MANAGER_L1","sortOrder":50},{"itemCode":"OPTICAL_MODULE","itemName":"光模块","enabled":false,"formCode":"OPTICAL_MODULE","formVersion":1,"evidenceRequired":false,"sourceRequirementCode":"NONE","waiverAllowed":false,"approvalRoleCode":"SERVICE_MANAGER_L1","sortOrder":60}]}',
    d.`permission_policy_ref` = 'PRE_02_SITE_SURVEY_DEFAULT',
    d.`completion_rule_type_code` = 'BUSINESS_OBJECT_STATUS',
    d.`completion_rule_config` = '{"schemaVersion":1,"requiredStatus":"DONE"}',
    d.`definition_version` = d.`definition_version` + 1,
    d.`updater` = 'seed',
    d.`update_time` = NOW()
WHERE r.`status` = 'DRAFT'
  AND r.`creator` = 'seed'
  AND d.`creator` = 'seed'
  AND d.`task_code` = 'T-SURVEY-EXEC'
  AND d.`work_binding_type_code` = 'TASK_NATIVE';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(198790, '工勘准备查询', 'pms:preparation-survey:query', 3, 160, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198791, '工勘准备管理', 'pms:preparation-survey:manage', 3, 170, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198792, '工勘项填写', 'pms:preparation-survey:fill', 3, 180, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198793, '工勘豁免审批', 'pms:preparation-survey:waiver-approve', 3, 190, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
  `parent_id` = VALUES(`parent_id`), `sort` = VALUES(`sort`), `status` = 0,
  `visible` = b'1', `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0';

-- 新权限由基础平台显式授权；本迁移不写system_role_menu。
