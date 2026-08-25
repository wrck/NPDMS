-- F-PROJ-004 / PM-07：项目类别值域收敛、维护权限及匹配示例复用声明。
-- 不改写proj_project存量MAIN/SUB或手工项目重大级别；异常值保留原证据供人工处置。

UPDATE `system_dict_data`
SET `status` = 1, `remark` = '历史项目树结构值；PM-07起不再作为项目类别候选',
    `updater` = 'seed', `update_time` = NOW()
WHERE `dict_type` = 'pms_project_category'
  AND `value` IN ('MAIN', 'SUB')
  AND `deleted` = b'0';

INSERT INTO `system_dict_data`
(`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`,
 `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(992004020001, 1, '普通类', 'GENERAL', 'pms_project_category', 0, 'primary', '',
 'PM-07平台项目类别', 'seed', NOW(), 'seed', NOW(), b'0'),
(992004020002, 2, '工程类', 'ENGINEERING', 'pms_project_category', 0, 'warning', '',
 'PM-07平台项目类别', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `sort`=VALUES(`sort`), `label`=VALUES(`label`),
  `status`=0, `color_type`=VALUES(`color_type`), `remark`=VALUES(`remark`),
  `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(198740, '项目属性调整', 'pms:project:classify', 3, 80, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`),
  `parent_id`=VALUES(`parent_id`), `sort`=VALUES(`sort`), `status`=0,
  `visible`=b'1', `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

-- 匹配组合不复制模板正文：V54与V59的高段示例模板已经覆盖唯一命中、部分限定、
-- 优先级让位、无匹配、多匹配和停用不参与。本Feature通过契约测试重新验证这些组合。
