DROP PROCEDURE IF EXISTS `fins001_assert_seed_identity`;
DELIMITER $$
CREATE PROCEDURE `fins001_assert_seed_identity`()
BEGIN
    CREATE TEMPORARY TABLE `fins001_expected_dict_type` (
        `id` bigint NOT NULL,
        `name` varchar(100) NOT NULL,
        `type` varchar(100) NOT NULL,
        `status` tinyint NOT NULL,
        `remark` varchar(500) NOT NULL,
        PRIMARY KEY (`id`),
        UNIQUE KEY (`type`)
    );

    INSERT INTO `fins001_expected_dict_type`
    (`id`, `name`, `type`, `status`, `remark`) VALUES
    (2991, 'PMS-巡检规则分类', 'pms_inspection_rule_category', 0, 'INS-03 INS-09正式巡检规则分类'),
    (2992, 'PMS-巡检规则严重度', 'pms_inspection_rule_severity', 0, 'INS-03 INS-09正式巡检规则严重度');

    CREATE TEMPORARY TABLE `fins001_expected_dict_data` (
        `id` bigint NOT NULL,
        `sort` int NOT NULL,
        `label` varchar(100) NOT NULL,
        `value` varchar(100) NOT NULL,
        `dict_type` varchar(100) NOT NULL,
        `status` tinyint NOT NULL,
        `color_type` varchar(100) NOT NULL,
        `css_class` varchar(100) NOT NULL,
        `remark` varchar(500) NOT NULL,
        PRIMARY KEY (`id`),
        UNIQUE KEY (`dict_type`, `value`)
    );

    INSERT INTO `fins001_expected_dict_data`
    (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`) VALUES
    (22930, 10, '基础', 'BASIC', 'pms_inspection_rule_category', 0, 'primary', '', 'F-INS-001'),
    (22931, 20, '运行状态', 'OPERATING_STATUS', 'pms_inspection_rule_category', 0, 'success', '', 'F-INS-001'),
    (22932, 30, '日志', 'LOG', 'pms_inspection_rule_category', 0, 'info', '', 'F-INS-001'),
    (22933, 40, '业务状态', 'BUSINESS_STATUS', 'pms_inspection_rule_category', 0, 'primary', '', 'F-INS-001'),
    (22934, 50, '冗余', 'REDUNDANCY', 'pms_inspection_rule_category', 0, 'warning', '', 'F-INS-001'),
    (22935, 60, '路由', 'ROUTING', 'pms_inspection_rule_category', 0, 'success', '', 'F-INS-001'),
    (22936, 70, '安全', 'SECURITY', 'pms_inspection_rule_category', 0, 'danger', '', 'F-INS-001'),
    (22937, 80, '转发通道', 'FORWARDING_CHANNEL', 'pms_inspection_rule_category', 0, 'info', '', 'F-INS-001'),
    (22938, 90, '负载均衡', 'LOAD_BALANCING', 'pms_inspection_rule_category', 0, 'warning', '', 'F-INS-001'),
    (22939, 100, '流量清洗', 'TRAFFIC_CLEANING', 'pms_inspection_rule_category', 0, 'danger', '', 'F-INS-001'),
    (22940, 10, '一般', 'GENERAL', 'pms_inspection_rule_severity', 0, 'info', '', 'F-INS-001'),
    (22941, 20, '严重', 'SEVERE', 'pms_inspection_rule_severity', 0, 'warning', '', 'F-INS-001'),
    (22942, 30, '致命', 'FATAL', 'pms_inspection_rule_severity', 0, 'danger', '', 'F-INS-001');

    CREATE TEMPORARY TABLE `fins001_expected_menu` (
        `id` bigint NOT NULL,
        `name` varchar(50) NOT NULL,
        `permission` varchar(100) NOT NULL,
        `type` tinyint NOT NULL,
        `sort` int NOT NULL,
        `parent_id` bigint NOT NULL,
        `path` varchar(200) NOT NULL,
        `icon` varchar(100) NOT NULL,
        `component` varchar(255) DEFAULT NULL,
        `component_name` varchar(255) DEFAULT NULL,
        `status` tinyint NOT NULL,
        `visible` bit(1) NOT NULL,
        `keep_alive` bit(1) NOT NULL,
        `always_show` bit(1) NOT NULL,
        PRIMARY KEY (`id`)
    );

    INSERT INTO `fins001_expected_menu`
    (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`) VALUES
    (199510, '巡检规则版本', 'pms:inspection-rule:query', 2, 6, 19265, 'inspection-rule', 'ep:list', 'pms/service/inspection-rule/index', 'PmsInspectionRule', 0, b'1', b'1', b'1'),
    (199511, '巡检规则查询', 'pms:inspection-rule:query', 3, 10, 199510, '', '', NULL, NULL, 0, b'1', b'1', b'1'),
    (199512, '巡检规则维护', 'pms:inspection-rule:manage', 3, 20, 199510, '', '', NULL, NULL, 0, b'1', b'1', b'1'),
    (199513, '巡检规则安全审核', 'pms:inspection-rule:security-review', 3, 30, 199510, '', '', NULL, NULL, 0, b'1', b'1', b'1'),
    (199514, '巡检规则发布', 'pms:inspection-rule:publish', 3, 40, 199510, '', '', NULL, NULL, 0, b'1', b'1', b'1'),
    (199515, '巡检规则停用', 'pms:inspection-rule:disable', 3, 50, 199510, '', '', NULL, NULL, 0, b'1', b'1', b'1'),
    (199516, '巡检规则选择', 'pms:inspection-rule:select', 3, 60, 199510, '', '', NULL, NULL, 0, b'1', b'1', b'1');

    IF EXISTS (
        SELECT 1
        FROM `system_dict_type` actual
        JOIN `fins001_expected_dict_type` expected
          ON actual.`id` = expected.`id` OR actual.`type` = expected.`type`
        WHERE actual.`id` <> expected.`id`
           OR actual.`type` <> expected.`type`
           OR actual.`name` <> expected.`name`
           OR actual.`status` <> expected.`status`
           OR NOT (actual.`remark` <=> expected.`remark`)
           OR actual.`deleted` <> b'0'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'F-INS-001 dictionary type identity conflict';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM `system_dict_data` actual
        JOIN `fins001_expected_dict_data` expected
          ON actual.`id` = expected.`id`
          OR (actual.`dict_type` = expected.`dict_type` AND actual.`value` = expected.`value`)
        WHERE actual.`id` <> expected.`id`
           OR actual.`dict_type` <> expected.`dict_type`
           OR actual.`value` <> expected.`value`
           OR actual.`label` <> expected.`label`
           OR actual.`sort` <> expected.`sort`
           OR actual.`status` <> expected.`status`
           OR NOT (actual.`color_type` <=> expected.`color_type`)
           OR NOT (actual.`css_class` <=> expected.`css_class`)
           OR NOT (actual.`remark` <=> expected.`remark`)
           OR actual.`deleted` <> b'0'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'F-INS-001 dictionary data identity conflict';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM `system_menu` actual
        JOIN `fins001_expected_menu` expected
          ON actual.`id` = expected.`id`
          OR (actual.`parent_id` = expected.`parent_id`
              AND actual.`name` = expected.`name`
              AND actual.`type` = expected.`type`)
        WHERE actual.`id` <> expected.`id`
           OR actual.`name` <> expected.`name`
           OR actual.`permission` <> expected.`permission`
           OR actual.`type` <> expected.`type`
           OR actual.`sort` <> expected.`sort`
           OR actual.`parent_id` <> expected.`parent_id`
           OR NOT (actual.`path` <=> expected.`path`)
           OR NOT (actual.`icon` <=> expected.`icon`)
           OR NOT (actual.`component` <=> expected.`component`)
           OR NOT (actual.`component_name` <=> expected.`component_name`)
           OR actual.`status` <> expected.`status`
           OR actual.`visible` <> expected.`visible`
           OR actual.`keep_alive` <> expected.`keep_alive`
           OR actual.`always_show` <> expected.`always_show`
           OR actual.`deleted` <> b'0'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'F-INS-001 menu identity conflict';
    END IF;

    INSERT INTO `system_dict_type`
    (`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
    SELECT expected.`id`, expected.`name`, expected.`type`, expected.`status`, expected.`remark`,
           'fins001-seed', NOW(), 'fins001-seed', NOW(), b'0', NULL
    FROM `fins001_expected_dict_type` expected
    WHERE NOT EXISTS (
        SELECT 1 FROM `system_dict_type` actual WHERE actual.`id` = expected.`id`
    );

    INSERT INTO `system_dict_data`
    (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
    SELECT expected.`id`, expected.`sort`, expected.`label`, expected.`value`, expected.`dict_type`, expected.`status`,
           expected.`color_type`, expected.`css_class`, expected.`remark`,
           'fins001-seed', NOW(), 'fins001-seed', NOW(), b'0'
    FROM `fins001_expected_dict_data` expected
    WHERE NOT EXISTS (
        SELECT 1 FROM `system_dict_data` actual WHERE actual.`id` = expected.`id`
    );

    INSERT INTO `system_menu`
    (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
    SELECT expected.`id`, expected.`name`, expected.`permission`, expected.`type`, expected.`sort`,
           expected.`parent_id`, expected.`path`, expected.`icon`, expected.`component`, expected.`component_name`,
           expected.`status`, expected.`visible`, expected.`keep_alive`, expected.`always_show`,
           'fins001-seed', NOW(), 'fins001-seed', NOW(), b'0'
    FROM `fins001_expected_menu` expected
    WHERE NOT EXISTS (
        SELECT 1 FROM `system_menu` actual WHERE actual.`id` = expected.`id`
    );
END$$
DELIMITER ;
CALL `fins001_assert_seed_identity`();
DROP PROCEDURE IF EXISTS `fins001_assert_seed_identity`;
