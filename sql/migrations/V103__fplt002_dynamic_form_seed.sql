-- =============================================================================
-- F-PLT-002：动态表单文件类别、菜单权限和三类确定性示例。
-- 菜单/字典可按稳定键修复；模板与修订仅首次插入，不覆盖已发布载荷或用户状态。
-- =============================================================================

INSERT INTO `system_dict_data`
(`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`,
 `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 992202030001, 20, '动态表单受控附件', 'DYNAMIC_FORM_ATTACHMENT',
       'pms_file_category', 0, 'primary', '',
       'F-PLT-002 PmsFileArtifact字段文件类别', 'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `dict_type` = 'pms_file_category'
      AND `value` = 'DYNAMIC_FORM_ATTACHMENT' AND `deleted` = b'0'
);

UPDATE `system_dict_data`
SET `sort` = 20, `label` = '动态表单受控附件', `status` = 0,
    `color_type` = 'primary', `css_class` = '',
    `remark` = 'F-PLT-002 PmsFileArtifact字段文件类别',
    `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0'
WHERE `dict_type` = 'pms_file_category' AND `value` = 'DYNAMIC_FORM_ATTACHMENT';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(198800, '动态表单模板', 'pms:dynamic-form-template:query', 2, 30, 19271,
 'dynamic-form-template', 'ep:document', 'pms/platform/dynamic-form/template/index',
 'PmsDynamicFormTemplate', 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198801, '动态表单模板管理', 'pms:dynamic-form-template:manage', 3, 10, 198800,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198802, '动态表单模板发布', 'pms:dynamic-form-template:publish', 3, 20, 198800,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198803, '动态表单实例', 'pms:dynamic-form-instance:query', 2, 40, 19271,
 'dynamic-form-instance', 'ep:tickets', 'pms/platform/dynamic-form/instance/index',
 'PmsDynamicFormInstance', 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198804, '动态表单实例创建', 'pms:dynamic-form-instance:create', 3, 10, 198803,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198805, '动态表单实例更新', 'pms:dynamic-form-instance:update', 3, 20, 198803,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
  `type` = VALUES(`type`), `sort` = VALUES(`sort`), `parent_id` = VALUES(`parent_id`),
  `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
  `component_name` = VALUES(`component_name`), `status` = 0, `visible` = b'1',
  `keep_alive` = b'1', `always_show` = b'1', `updater` = 'seed',
  `update_time` = NOW(), `deleted` = b'0';

-- 三个示例模板仅在稳定编码不存在时创建；后续重跑不改其可用性、版本或发布指针。
INSERT INTO `plt_dynamic_form_template`
(`id`, `template_code`, `template_name`, `category_code`, `description`,
 `availability_code`, `current_published_revision_id`, `version`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 992202010001, 'PLT_EXAMPLE_GENERAL_FORM', '通用动态表单示例', 'GENERAL',
       '包含代表性普通值、布局、普通上传和受控FileArtifact字段',
       'ENABLED', NULL, 0, 'seed', NOW(), 'seed', NOW(), b'0', 0
WHERE NOT EXISTS (
    SELECT 1 FROM `plt_dynamic_form_template`
    WHERE `tenant_id` = 0 AND (`id` = 992202010001
        OR (`template_code` = 'PLT_EXAMPLE_GENERAL_FORM' AND `deleted` = b'0'))
);

INSERT INTO `plt_dynamic_form_template`
(`id`, `template_code`, `template_name`, `category_code`, `description`,
 `availability_code`, `current_published_revision_id`, `version`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 992202010002, 'PLT_EXAMPLE_DISABLED_FORM', '停用动态表单示例', 'GENERAL',
       '已发布但不可参与人工选择', 'DISABLED', NULL, 0,
       'seed', NOW(), 'seed', NOW(), b'0', 0
WHERE NOT EXISTS (
    SELECT 1 FROM `plt_dynamic_form_template`
    WHERE `tenant_id` = 0 AND (`id` = 992202010002
        OR (`template_code` = 'PLT_EXAMPLE_DISABLED_FORM' AND `deleted` = b'0'))
);

INSERT INTO `plt_dynamic_form_template`
(`id`, `template_code`, `template_name`, `category_code`, `description`,
 `availability_code`, `current_published_revision_id`, `version`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 992202010003, 'PLT_EXAMPLE_DRAFT_FORM', '仅草稿动态表单示例', 'GENERAL',
       '没有当前已发布修订，不可参与人工选择', 'DISABLED', NULL, 0,
       'seed', NOW(), 'seed', NOW(), b'0', 0
WHERE NOT EXISTS (
    SELECT 1 FROM `plt_dynamic_form_template`
    WHERE `tenant_id` = 0 AND (`id` = 992202010003
        OR (`template_code` = 'PLT_EXAMPLE_DRAFT_FORM' AND `deleted` = b'0'))
);

INSERT INTO `plt_dynamic_form_template_revision`
(`id`, `template_id`, `revision_no`, `status_code`, `draft_marker`, `source_revision_id`,
 `form_conf_json`, `form_rules_json`, `engine_code`, `designer_version`, `renderer_version`,
 `published_by`, `published_at`, `version`, `creator`, `create_time`, `updater`,
 `update_time`, `deleted`, `tenant_id`)
SELECT 992202020001, 992202010001, 1, 'PUBLISHED', NULL, NULL,
       '{"form":{"inline":false,"labelPosition":"right","labelWidth":"120px"},"submitBtn":false,"resetBtn":false}',
       '[{"type":"input","field":"subject","title":"主题"},{"type":"Editor","field":"description","title":"说明"},{"type":"select","field":"category","title":"分类","options":[{"value":"GENERAL","label":"通用"},{"value":"SPECIAL","label":"专项"}]},{"type":"switch","field":"enabled","title":"启用"},{"type":"inputNumber","field":"quantity","title":"数量"},{"type":"row","children":[{"type":"col","props":{"span":12},"children":[{"type":"input","field":"contactName","title":"联系人"}]},{"type":"col","props":{"span":12},"children":[{"type":"input","field":"contactPhone","title":"联系电话"}]}]},{"type":"UploadFile","field":"ordinaryFiles","title":"普通上传"},{"type":"PmsFileArtifact","field":"controlledFiles","title":"受控附件"}]',
       'FORM_CREATE_ELEMENT_PLUS', '3.4.0', '3.2.38', 1, NOW(), 0,
       'seed', NOW(), 'seed', NOW(), b'0', 0
WHERE NOT EXISTS (
    SELECT 1 FROM `plt_dynamic_form_template_revision`
    WHERE `tenant_id` = 0 AND (`id` = 992202020001
        OR (`template_id` = 992202010001 AND `revision_no` = 1))
);

INSERT INTO `plt_dynamic_form_template_revision`
(`id`, `template_id`, `revision_no`, `status_code`, `draft_marker`, `source_revision_id`,
 `form_conf_json`, `form_rules_json`, `engine_code`, `designer_version`, `renderer_version`,
 `published_by`, `published_at`, `version`, `creator`, `create_time`, `updater`,
 `update_time`, `deleted`, `tenant_id`)
SELECT 992202020002, 992202010002, 1, 'PUBLISHED', NULL, NULL,
       '{"form":{"inline":false},"submitBtn":false,"resetBtn":false}',
       '[{"type":"input","field":"disabledExampleValue","title":"示例值"}]',
       'FORM_CREATE_ELEMENT_PLUS', '3.4.0', '3.2.38', 1, NOW(), 0,
       'seed', NOW(), 'seed', NOW(), b'0', 0
WHERE NOT EXISTS (
    SELECT 1 FROM `plt_dynamic_form_template_revision`
    WHERE `tenant_id` = 0 AND (`id` = 992202020002
        OR (`template_id` = 992202010002 AND `revision_no` = 1))
);

INSERT INTO `plt_dynamic_form_template_revision`
(`id`, `template_id`, `revision_no`, `status_code`, `draft_marker`, `source_revision_id`,
 `form_conf_json`, `form_rules_json`, `engine_code`, `designer_version`, `renderer_version`,
 `published_by`, `published_at`, `version`, `creator`, `create_time`, `updater`,
 `update_time`, `deleted`, `tenant_id`)
SELECT 992202020003, 992202010003, 1, 'DRAFT', 1, NULL,
       '{}', '[]', 'FORM_CREATE_ELEMENT_PLUS', '3.4.0', '3.2.38', NULL, NULL, 0,
       'seed', NOW(), 'seed', NOW(), b'0', 0
WHERE NOT EXISTS (
    SELECT 1 FROM `plt_dynamic_form_template_revision`
    WHERE `tenant_id` = 0 AND (`id` = 992202020003
        OR (`template_id` = 992202010003 AND `revision_no` = 1))
);

-- 只补齐首次种子的发布指针；已存在的非空指针、版本和可用性不被重置。
UPDATE `plt_dynamic_form_template`
SET `current_published_revision_id` = 992202020001,
    `updater` = 'seed', `update_time` = NOW()
WHERE `tenant_id` = 0 AND `id` = 992202010001
  AND `current_published_revision_id` IS NULL AND `creator` = 'seed' AND `version` = 0
  AND EXISTS (SELECT 1 FROM `plt_dynamic_form_template_revision`
              WHERE `tenant_id` = 0 AND `id` = 992202020001
                AND `template_id` = 992202010001 AND `status_code` = 'PUBLISHED');

UPDATE `plt_dynamic_form_template`
SET `current_published_revision_id` = 992202020002,
    `updater` = 'seed', `update_time` = NOW()
WHERE `tenant_id` = 0 AND `id` = 992202010002
  AND `current_published_revision_id` IS NULL AND `creator` = 'seed' AND `version` = 0
  AND EXISTS (SELECT 1 FROM `plt_dynamic_form_template_revision`
              WHERE `tenant_id` = 0 AND `id` = 992202020002
                AND `template_id` = 992202010002 AND `status_code` = 'PUBLISHED');

-- 本迁移不写system_role_menu，不播种WorkBinding、PRE-04或其他业务Context事实。
