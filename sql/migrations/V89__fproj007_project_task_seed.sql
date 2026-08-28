-- =============================================================================
-- F-PROJ-007 / PM-11：默认任务状态机、存量任务版本冻结和稳定权限种子。
-- 单租户运行仍使用受信tenant_id=0；同时为当前已登记租户准备首个发布版本。
-- =============================================================================

INSERT INTO `proj_task_state_machine_revision`
    (`revision_no`, `status`, `effective_from`, `published_by`, `published_at`,
     `version`, `creator`, `updater`, `tenant_id`)
SELECT 1, 'PUBLISHED', CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3),
       0, 'v89-fproj007', 'v89-fproj007', tenants.`tenant_id`
FROM (
    SELECT 0 AS `tenant_id`
    UNION
    SELECT `id` AS `tenant_id` FROM `system_tenant` WHERE `deleted` = b'0'
    UNION
    SELECT `tenant_id` FROM `proj_project`
    UNION
    SELECT `tenant_id` FROM `proj_project_task`
) tenants
WHERE NOT EXISTS (
    SELECT 1
    FROM `proj_task_state_machine_revision` revision
    WHERE revision.`tenant_id` = tenants.`tenant_id`
      AND revision.`revision_no` = 1
);

-- 核心状态机精确为4条线性迁移和4条非终态CANCEL迁移。
INSERT INTO `proj_task_state_transition`
    (`revision_id`, `from_status_code`, `action_code`, `to_status_code`,
     `standard_status_mapping`, `allowed_role_code`, `entry_condition`, `exit_condition`,
     `version`, `creator`, `updater`, `tenant_id`)
SELECT revision.`id`, transition.`from_status_code`, transition.`action_code`,
       transition.`to_status_code`, transition.`to_status_code`, transition.`allowed_role_code`,
       transition.`entry_condition`, transition.`exit_condition`,
       0, 'v89-fproj007', 'v89-fproj007', revision.`tenant_id`
FROM `proj_task_state_machine_revision` revision
JOIN (
    SELECT 'PENDING_ASSIGN' AS `from_status_code`, 'ASSIGN' AS `action_code`,
           'PENDING_START' AS `to_status_code`,
           'CURRENT_PROJECT_MANAGER_OR_AUTHORIZED_SERVICE_MANAGER_FOR_CROSS_REGION' AS `allowed_role_code`,
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:assign') AS `entry_condition`,
           JSON_OBJECT('schemaVersion', 1, 'currentAssignmentRequired', TRUE) AS `exit_condition`
    UNION ALL
    SELECT 'PENDING_START', 'START', 'IN_PROGRESS', 'CURRENT_EFFECTIVE_ASSIGNEE',
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:execute'),
           JSON_OBJECT('schemaVersion', 1, 'actualStartTimeRequired', TRUE)
    UNION ALL
    SELECT 'IN_PROGRESS', 'SUBMIT', 'PENDING_ACCEPT', 'CURRENT_EFFECTIVE_ASSIGNEE',
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:execute'),
           JSON_OBJECT('schemaVersion', 1, 'progress', 99)
    UNION ALL
    SELECT 'PENDING_ACCEPT', 'COMPLETE', 'DONE', 'CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER',
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:complete'),
           JSON_OBJECT('schemaVersion', 1, 'completionRuleSatisfied', TRUE)
    UNION ALL
    SELECT 'PENDING_ASSIGN', 'CANCEL', 'CLOSED', 'CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER',
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:complete'),
           JSON_OBJECT('schemaVersion', 1, 'reasonRequired', TRUE)
    UNION ALL
    SELECT 'PENDING_START', 'CANCEL', 'CLOSED', 'CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER',
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:complete'),
           JSON_OBJECT('schemaVersion', 1, 'reasonRequired', TRUE)
    UNION ALL
    SELECT 'IN_PROGRESS', 'CANCEL', 'CLOSED', 'CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER',
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:complete'),
           JSON_OBJECT('schemaVersion', 1, 'reasonRequired', TRUE)
    UNION ALL
    SELECT 'PENDING_ACCEPT', 'CANCEL', 'CLOSED', 'CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER',
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:complete'),
           JSON_OBJECT('schemaVersion', 1, 'reasonRequired', TRUE)
) transition
WHERE revision.`revision_no` = 1
  AND revision.`status` = 'PUBLISHED'
  AND NOT EXISTS (
      SELECT 1
      FROM `proj_task_state_transition` existing
      WHERE existing.`tenant_id` = revision.`tenant_id`
        AND existing.`revision_id` = revision.`id`
        AND existing.`from_status_code` = transition.`from_status_code`
        AND existing.`action_code` = transition.`action_code`
  );

-- 存量任务按同租户确定性冻结默认发布版本；不根据任务名称、URL或旧状态猜测绑定。
UPDATE `proj_project_task` task
JOIN `proj_task_state_machine_revision` revision
  ON revision.`tenant_id` = task.`tenant_id`
 AND revision.`revision_no` = 1
 AND revision.`status` = 'PUBLISHED'
SET task.`state_machine_revision_id` = revision.`id`
WHERE task.`state_machine_revision_id` IS NULL;

DROP TEMPORARY TABLE IF EXISTS `_fproj007_assert`;
CREATE TEMPORARY TABLE `_fproj007_assert` (`id` TINYINT NOT NULL PRIMARY KEY);
INSERT INTO `_fproj007_assert` VALUES (1);
INSERT INTO `_fproj007_assert` (`id`)
SELECT 1
WHERE EXISTS (
    SELECT 1
    FROM `proj_project_task` task
    LEFT JOIN `proj_task_state_machine_revision` revision
      ON revision.`tenant_id` = task.`tenant_id`
     AND revision.`id` = task.`state_machine_revision_id`
    WHERE task.`state_machine_revision_id` IS NULL OR revision.`id` IS NULL
);
TRUNCATE TABLE `_fproj007_assert`;
INSERT INTO `_fproj007_assert` VALUES (1);
INSERT INTO `_fproj007_assert` (`id`)
SELECT 1
WHERE EXISTS (
    SELECT 1
    FROM `proj_task_state_machine_revision` revision
    LEFT JOIN `proj_task_state_transition` transition
      ON transition.`tenant_id` = revision.`tenant_id`
     AND transition.`revision_id` = revision.`id`
    WHERE revision.`revision_no` = 1
      AND revision.`status` = 'PUBLISHED'
    GROUP BY revision.`tenant_id`, revision.`id`
    HAVING COUNT(transition.`id`) <> 8
);
DROP TEMPORARY TABLE `_fproj007_assert`;

ALTER TABLE `proj_project_task`
    MODIFY COLUMN `state_machine_revision_id` BIGINT NOT NULL
        COMMENT '创建时冻结的任务状态机版本ID',
    ADD CONSTRAINT `fk_proj_task_state_revision`
        FOREIGN KEY (`tenant_id`, `state_machine_revision_id`)
        REFERENCES `proj_task_state_machine_revision` (`tenant_id`, `id`);

-- 复用既有任务入口为V1.8项目任务入口，不创建第二棵导航树。
UPDATE `system_menu`
SET `name` = '项目任务', `permission` = 'pms:project-task:query',
    `status` = 0, `visible` = b'1', `updater` = 'seed', `update_time` = NOW(),
    `deleted` = b'0'
WHERE `id` = 18014;

UPDATE `system_menu`
SET `name` = CASE `id`
        WHEN 18030 THEN '项目任务查询'
        WHEN 18031 THEN '项目任务创建'
        WHEN 18032 THEN '项目任务更新'
        ELSE `name`
    END,
    `permission` = CASE `id`
        WHEN 18030 THEN 'pms:project-task:query'
        WHEN 18031 THEN 'pms:project-task:create'
        WHEN 18032 THEN 'pms:project-task:update'
        ELSE `permission`
    END,
    `parent_id` = 18014, `status` = 0, `visible` = b'1',
    `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` IN (18030, 18031, 18032);

-- V1.7删除能力退役：保留菜单历史行，撤销有效角色关联，不物理删除历史任务。
UPDATE `system_menu`
SET `status` = 1, `visible` = b'0', `updater` = 'seed', `update_time` = NOW()
WHERE `id` = 18033;

UPDATE `system_role_menu`
SET `deleted` = b'1', `updater` = 'seed', `update_time` = NOW()
WHERE `menu_id` = 18033 AND `deleted` = b'0';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(198760, '项目任务移动', 'pms:project-task:move', 3, 40, 18014, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198761, '项目任务指派', 'pms:project-task:assign', 3, 50, 18014, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198762, '本人任务执行', 'pms:project-task:execute', 3, 60, 18014, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198763, '项目任务完成', 'pms:project-task:complete', 3, 70, 18014, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198764, '任务状态机配置', 'pms:project-task-state:manage', 3, 80, 18014, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
  `parent_id` = VALUES(`parent_id`), `sort` = VALUES(`sort`), `status` = 0,
  `visible` = b'1', `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0';

-- 新写权限不在迁移中授予任何角色；功能权限由基础平台授权，项目范围由服务端重验。
