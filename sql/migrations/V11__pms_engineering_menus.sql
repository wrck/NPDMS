-- =============================================================================
-- V11: PMS 工程实施域可见菜单（T-V1-ENG-A / T-V1-ENG-B / T-V1-ENG-C UI 闭环）
-- 父菜单 18000 项目交付（V4 已存在）；本迁移补齐 10 个工程实施可见菜单。
-- 使用 ID 19009~19018 避免与 V9 已占用的 18031~18040 按钮 ID 冲突。
-- =============================================================================
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- 工程实施菜单（18000 下，sort 51~60）
(19009, '现场工勘', 'pms:eng-site-survey:query', 2, 51, 18000, 'eng-site-survey', 'ep:position',
 'pms/engineering/site-survey/index', 'PmsEngSiteSurvey', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19010, '需求与接口', 'pms:eng-requirement:query', 2, 52, 18000, 'eng-requirement', 'ep:document',
 'pms/engineering/requirement/index', 'PmsEngRequirement', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19011, '实施方案', 'pms:eng-solution:query', 2, 53, 18000, 'eng-solution', 'ep:notebook',
 'pms/engineering/solution/index', 'PmsEngSolution', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19012, '资源就绪', 'pms:eng-resource:query', 2, 54, 18000, 'eng-resource', 'ep:box',
 'pms/engineering/resource/index', 'PmsEngResource', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19013, '到货签收', 'pms:eng-arrival:query', 2, 55, 18000, 'eng-arrival', 'ep:van',
 'pms/engineering/arrival/index', 'PmsEngArrival', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19014, '硬件安装', 'pms:eng-installation:query', 2, 56, 18000, 'eng-installation', 'ep:cpu',
 'pms/engineering/installation/index', 'PmsEngInstallation', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19015, '配置调试', 'pms:eng-configuration:query', 2, 57, 18000, 'eng-configuration', 'ep:set-up',
 'pms/engineering/configuration/index', 'PmsEngConfiguration', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19016, '业务联调', 'pms:eng-joint-test:query', 2, 58, 18000, 'eng-joint-test', 'ep:connection',
 'pms/engineering/joint-test/index', 'PmsEngJointTest', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19017, '实施问题', 'pms:eng-issue:query', 2, 59, 18000, 'eng-issue', 'ep:warning',
 'pms/engineering/issue/index', 'PmsEngIssue', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19018, '交付件归集', 'pms:eng-deliverable:query', 2, 60, 18000, 'eng-deliverable', 'ep:folder',
 'pms/engineering/deliverable/index', 'PmsEngDeliverable', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `permission` = VALUES(`permission`), `path` = VALUES(`path`),
 `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
 `parent_id` = VALUES(`parent_id`), `type` = VALUES(`type`), `sort` = VALUES(`sort`),
 `icon` = VALUES(`icon`), `update_time` = NOW(), `deleted` = b'0';
