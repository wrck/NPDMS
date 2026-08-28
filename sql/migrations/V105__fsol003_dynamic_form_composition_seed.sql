-- =============================================================================
-- F-SOL-003：可直接实例化的PRE-04动态表单及WorkBinding V2代表性种子。
-- 新建不可变PLT发布修订与新的项目模板发布修订；不修改既有发布定义，不授予角色。
-- =============================================================================

INSERT INTO `plt_dynamic_form_template`
(`id`, `template_code`, `template_name`, `category_code`, `description`,
 `availability_code`, `current_published_revision_id`, `version`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 992203010001, 'SOL_PRE04_REQUIREMENT_ANALYSIS_V1', '需求分析标准模板',
       'REQUIREMENT_ANALYSIS', '11项固定核心内容及每项独立受控附件槽位',
       'ENABLED', NULL, 1, 'seed', NOW(), 'seed', NOW(), b'0', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `plt_dynamic_form_template`
    WHERE `tenant_id` = 1 AND (`id` = 992203010001
        OR (`template_code` = 'SOL_PRE04_REQUIREMENT_ANALYSIS_V1' AND `deleted` = b'0'))
);

INSERT INTO `plt_dynamic_form_template_revision`
(`id`, `template_id`, `revision_no`, `status_code`, `draft_marker`, `source_revision_id`,
 `form_conf_json`, `form_rules_json`, `engine_code`, `designer_version`, `renderer_version`,
 `published_by`, `published_at`, `version`, `creator`, `create_time`, `updater`,
 `update_time`, `deleted`, `tenant_id`)
SELECT 992203020001, 992203010001, 1, 'PUBLISHED', NULL, NULL,
       '{"form":{"inline":false,"labelPosition":"top","labelWidth":"120px"},"submitBtn":false,"resetBtn":false}',
       '[{"type":"Editor","field":"PROJECT_BACKGROUND","title":"项目背景","validate":[{"required":true,"message":"请填写项目背景"}]},{"type":"PmsFileArtifact","field":"PROJECT_BACKGROUND__ATTACHMENTS","title":"项目背景附件"},{"type":"Editor","field":"PROJECT_OBJECTIVE","title":"项目目标","validate":[{"required":true,"message":"请填写项目目标"}]},{"type":"PmsFileArtifact","field":"PROJECT_OBJECTIVE__ATTACHMENTS","title":"项目目标附件"},{"type":"Editor","field":"NETWORK_TOPOLOGY","title":"网络拓扑","validate":[{"required":true,"message":"请填写网络拓扑"}]},{"type":"PmsFileArtifact","field":"NETWORK_TOPOLOGY__ATTACHMENTS","title":"网络拓扑附件"},{"type":"Editor","field":"TRANSMISSION_REQUIREMENT","title":"传输需求"},{"type":"PmsFileArtifact","field":"TRANSMISSION_REQUIREMENT__ATTACHMENTS","title":"传输需求附件"},{"type":"Editor","field":"TRAFFIC_REQUIREMENT","title":"流量需求"},{"type":"PmsFileArtifact","field":"TRAFFIC_REQUIREMENT__ATTACHMENTS","title":"流量需求附件"},{"type":"Editor","field":"BUSINESS_REQUIREMENT","title":"业务需求"},{"type":"PmsFileArtifact","field":"BUSINESS_REQUIREMENT__ATTACHMENTS","title":"业务需求附件"},{"type":"Editor","field":"IP_PLANNING","title":"IP规划"},{"type":"PmsFileArtifact","field":"IP_PLANNING__ATTACHMENTS","title":"IP规划附件"},{"type":"Editor","field":"REDUNDANCY_REQUIREMENT","title":"冗余需求"},{"type":"PmsFileArtifact","field":"REDUNDANCY_REQUIREMENT__ATTACHMENTS","title":"冗余需求附件"},{"type":"Editor","field":"SECURITY_PROTECTION","title":"安全防护"},{"type":"PmsFileArtifact","field":"SECURITY_PROTECTION__ATTACHMENTS","title":"安全防护附件"},{"type":"Editor","field":"OPERATIONS_REQUIREMENT","title":"运维需求"},{"type":"PmsFileArtifact","field":"OPERATIONS_REQUIREMENT__ATTACHMENTS","title":"运维需求附件"},{"type":"Editor","field":"LOGGING_REQUIREMENT","title":"日志需求"},{"type":"PmsFileArtifact","field":"LOGGING_REQUIREMENT__ATTACHMENTS","title":"日志需求附件"}]',
       'FORM_CREATE_ELEMENT_PLUS', '3.4.0', '3.2.38', 1, NOW(), 1,
       'seed', NOW(), 'seed', NOW(), b'0', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `plt_dynamic_form_template_revision`
    WHERE `tenant_id` = 1 AND (`id` = 992203020001
        OR (`template_id` = 992203010001 AND `revision_no` = 1))
);

UPDATE `plt_dynamic_form_template`
SET `current_published_revision_id` = 992203020001,
    `updater` = 'seed', `update_time` = NOW()
WHERE `tenant_id` = 1 AND `id` = 992203010001
  AND `current_published_revision_id` IS NULL AND `creator` = 'seed'
  AND EXISTS (SELECT 1 FROM `plt_dynamic_form_template_revision`
              WHERE `tenant_id` = 1 AND `id` = 992203020001
                AND `template_id` = 992203010001 AND `status_code` = 'PUBLISHED');

-- 兼容性与选择负向组合：缺核心字段、重复核心字段、停用兼容模板、用途不匹配模板。
INSERT INTO `plt_dynamic_form_template`
(`id`, `template_code`, `template_name`, `category_code`, `description`,
 `availability_code`, `current_published_revision_id`, `version`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(992203010002, 'SOL_PRE04_MISSING_CORE_EXAMPLE', '需求分析缺核心字段示例',
 'REQUIREMENT_ANALYSIS', '缺少LOGGING_REQUIREMENT及其附件槽位，不可绑定PRE-04',
 'ENABLED', NULL, 1, 'seed', NOW(), 'seed', NOW(), b'0', 1),
(992203010003, 'SOL_PRE04_DUPLICATE_CORE_EXAMPLE', '需求分析重复核心字段示例',
 'REQUIREMENT_ANALYSIS', 'PROJECT_BACKGROUND字段重复，不可绑定PRE-04',
 'ENABLED', NULL, 1, 'seed', NOW(), 'seed', NOW(), b'0', 1),
(992203010004, 'SOL_PRE04_DISABLED_COMPATIBLE', '需求分析停用兼容示例',
 'REQUIREMENT_ANALYSIS', '结构兼容但模板已停用，不可新建WorkBinding',
 'DISABLED', NULL, 1, 'seed', NOW(), 'seed', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE `id` = `id`;

SET @v105_pre04_compatible_rules = (
    SELECT `form_rules_json` FROM `plt_dynamic_form_template_revision`
    WHERE `tenant_id` = 1 AND `id` = 992203020001
);

INSERT INTO `plt_dynamic_form_template_revision`
(`id`, `template_id`, `revision_no`, `status_code`, `draft_marker`, `source_revision_id`,
 `form_conf_json`, `form_rules_json`, `engine_code`, `designer_version`, `renderer_version`,
 `published_by`, `published_at`, `version`, `creator`, `create_time`, `updater`,
 `update_time`, `deleted`, `tenant_id`)
VALUES
(992203020002, 992203010002, 1, 'PUBLISHED', NULL, NULL,
 '{"form":{"labelPosition":"top"},"submitBtn":false,"resetBtn":false}',
 '[{"type":"Editor","field":"PROJECT_BACKGROUND","title":"项目背景"},{"type":"PmsFileArtifact","field":"PROJECT_BACKGROUND__ATTACHMENTS","title":"项目背景附件"}]',
 'FORM_CREATE_ELEMENT_PLUS', '3.4.0', '3.2.38', 1, NOW(), 1,
 'seed', NOW(), 'seed', NOW(), b'0', 1),
(992203020003, 992203010003, 1, 'PUBLISHED', NULL, NULL,
 '{"form":{"labelPosition":"top"},"submitBtn":false,"resetBtn":false}',
 '[{"type":"Editor","field":"PROJECT_BACKGROUND","title":"项目背景"},{"type":"Editor","field":"PROJECT_BACKGROUND","title":"重复项目背景"},{"type":"PmsFileArtifact","field":"PROJECT_BACKGROUND__ATTACHMENTS","title":"项目背景附件"}]',
 'FORM_CREATE_ELEMENT_PLUS', '3.4.0', '3.2.38', 1, NOW(), 1,
 'seed', NOW(), 'seed', NOW(), b'0', 1),
(992203020004, 992203010004, 1, 'PUBLISHED', NULL, NULL,
 '{"form":{"labelPosition":"top"},"submitBtn":false,"resetBtn":false}',
 @v105_pre04_compatible_rules,
 'FORM_CREATE_ELEMENT_PLUS', '3.4.0', '3.2.38', 1, NOW(), 1,
 'seed', NOW(), 'seed', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE `id` = `id`;

UPDATE `plt_dynamic_form_template` t
JOIN `plt_dynamic_form_template_revision` r
  ON r.`tenant_id` = t.`tenant_id` AND r.`template_id` = t.`id` AND r.`revision_no` = 1
SET t.`current_published_revision_id` = r.`id`, t.`updater` = 'seed', t.`update_time` = NOW()
WHERE t.`tenant_id` = 1 AND t.`id` IN (992203010002, 992203010003, 992203010004)
  AND t.`current_published_revision_id` IS NULL AND t.`creator` = 'seed';

-- 无匹配负向使用项目模板匹配模型自身表达：PUBLIC_TENDER/ENGINEERING/REMOTE/NATIONAL
-- 不对应V100/V105任一ACTIVE发布模板；不得用一个Schema不兼容的PLT模板冒充“无匹配”。

-- 基于V100的完整S0-S6代表性模板复制tenant=1身份并产生新发布修订；旧tenant=0保持不变。
INSERT INTO `proj_project_template`
(`id`, `code`, `name`, `status`, `match_priority`, `description`, `system_reserved`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`, `tenant_id`)
SELECT 992203040001, sourceTemplate.`code`, sourceTemplate.`name`, sourceTemplate.`status`,
       sourceTemplate.`match_priority`, 'F-SOL-003动态表单WorkBinding V2浏览器验收模板',
       sourceTemplate.`system_reserved`, 'seed', NOW(), 'seed', NOW(), b'0', NULL, 1
FROM `proj_project_template` sourceTemplate
WHERE sourceTemplate.`tenant_id` = 0 AND sourceTemplate.`id` = 992103040001
  AND sourceTemplate.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `proj_project_template` targetTemplate
                  WHERE targetTemplate.`tenant_id` = 1
                    AND (targetTemplate.`id` = 992203040001
                      OR targetTemplate.`code` = sourceTemplate.`code`));

INSERT INTO `proj_project_template_revision`
(`id`, `template_id`, `revision_no`, `status`, `signing_method`, `project_category`,
 `implementation_method`, `major_project_level`, `process_definition_key`,
 `process_definition_version`, `validation_summary`, `published_by`, `published_time`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`, `tenant_id`)
SELECT 992203050001, 992203040001, 2, 'PUBLISHED', `signing_method`, `project_category`,
       `implementation_method`, `major_project_level`, `process_definition_key`,
       `process_definition_version`, 'F-SOL-003动态表单WorkBinding V2完整生命周期种子',
       'seed', NOW(), 'seed', NOW(), 'seed', NOW(), b'0', NULL, 1
FROM `proj_project_template_revision`
WHERE `tenant_id` = 0 AND `id` = 992103050001
  AND NOT EXISTS (SELECT 1 FROM `proj_project_template_revision`
                  WHERE `tenant_id` = 1 AND `id` = 992203050001);

INSERT INTO `proj_project_template_stage_definition`
(`template_revision_id`, `stage_code`, `name`, `sort_order`, `entry_criteria`, `exit_criteria`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`, `tenant_id`)
SELECT 992203050001, `stage_code`, `name`, `sort_order`, `entry_criteria`, `exit_criteria`,
       'seed', NOW(), 'seed', NOW(), b'0', NULL, 1
FROM `proj_project_template_stage_definition` s
WHERE s.`tenant_id` = 0 AND s.`template_revision_id` = 992103050001
  AND NOT EXISTS (SELECT 1 FROM `proj_project_template_stage_definition` t
                  WHERE t.`tenant_id` = 1 AND t.`template_revision_id` = 992203050001
                    AND t.`stage_code` = s.`stage_code`);

INSERT INTO `proj_project_template_task_definition`
(`template_revision_id`, `stage_definition_key`, `task_definition_key`, `parent_task_definition_key`,
 `task_code`, `name`, `parent_task_code`, `stage_code`, `priority`, `sort_order`, `estimated_hours`,
 `satisfaction_timing`, `description`, `work_binding_type_code`, `target_context_code`,
 `target_object_type`, `target_object_key`, `component_key`, `dynamic_form_revision_id`,
 `approval_definition_key`, `binding_config`, `permission_policy_ref`, `completion_rule_type_code`,
 `completion_rule_config`, `gate_ref`, `definition_version`, `creator`, `create_time`, `updater`,
 `update_time`, `deleted`, `deleted_time`, `tenant_id`)
SELECT 992203050001, `stage_definition_key`, `task_definition_key`, `parent_task_definition_key`,
       `task_code`, `name`, `parent_task_code`, `stage_code`, `priority`, `sort_order`, `estimated_hours`,
       `satisfaction_timing`, `description`, `work_binding_type_code`, `target_context_code`,
       `target_object_type`, `target_object_key`, `component_key`,
       CASE WHEN `task_definition_key` = 'T-REQ-ANALYSIS' THEN NULL ELSE `dynamic_form_revision_id` END,
       `approval_definition_key`,
       CASE WHEN `task_definition_key` = 'T-REQ-ANALYSIS'
            THEN CAST('{"schemaVersion":2,"dynamicFormTemplateId":992203010001,"dynamicFormTemplateRevisionId":992203020001,"dynamicFormRevisionNo":1,"dynamicFormRevisionFactVersion":1}' AS JSON)
            ELSE `binding_config` END,
       `permission_policy_ref`, `completion_rule_type_code`, `completion_rule_config`, `gate_ref`,
       CASE WHEN `task_definition_key` = 'T-REQ-ANALYSIS' THEN 2 ELSE `definition_version` END,
       'seed', NOW(), 'seed', NOW(), b'0', NULL, 1
FROM `proj_project_template_task_definition` s
WHERE s.`tenant_id` = 0 AND s.`template_revision_id` = 992103050001
  AND NOT EXISTS (SELECT 1 FROM `proj_project_template_task_definition` t
                  WHERE t.`tenant_id` = 1 AND t.`template_revision_id` = 992203050001
                    AND t.`task_definition_key` = s.`task_definition_key`);

INSERT INTO `proj_project_template_milestone_definition`
(`template_revision_id`, `milestone_code`, `name`, `stage_code`, `timing`, `criteria`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`, `tenant_id`)
SELECT 992203050001, `milestone_code`, `name`, `stage_code`, `timing`, `criteria`,
       'seed', NOW(), 'seed', NOW(), b'0', NULL, 1
FROM `proj_project_template_milestone_definition` s
WHERE s.`tenant_id` = 0 AND s.`template_revision_id` = 992103050001
  AND NOT EXISTS (SELECT 1 FROM `proj_project_template_milestone_definition` t
                  WHERE t.`tenant_id` = 1 AND t.`template_revision_id` = 992203050001
                    AND t.`milestone_code` = s.`milestone_code`);

INSERT INTO `proj_project_template_deliverable_definition`
(`template_revision_id`, `deliverable_code`, `name`, `stage_code`, `task_code`, `required`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`, `tenant_id`)
SELECT 992203050001, `deliverable_code`, `name`, `stage_code`, `task_code`, `required`,
       'seed', NOW(), 'seed', NOW(), b'0', NULL, 1
FROM `proj_project_template_deliverable_definition` s
WHERE s.`tenant_id` = 0 AND s.`template_revision_id` = 992103050001
  AND NOT EXISTS (SELECT 1 FROM `proj_project_template_deliverable_definition` t
                  WHERE t.`tenant_id` = 1 AND t.`template_revision_id` = 992203050001
                    AND t.`deliverable_code` = s.`deliverable_code`);

INSERT INTO `proj_project_template_gate_definition`
(`template_revision_id`, `gate_code`, `name`, `gate_type`, `stage_code`, `description`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`, `tenant_id`)
SELECT 992203050001, `gate_code`, `name`, `gate_type`, `stage_code`, `description`,
       'seed', NOW(), 'seed', NOW(), b'0', NULL, 1
FROM `proj_project_template_gate_definition` s
WHERE s.`tenant_id` = 0 AND s.`template_revision_id` = 992103050001
  AND NOT EXISTS (SELECT 1 FROM `proj_project_template_gate_definition` t
                  WHERE t.`tenant_id` = 1 AND t.`template_revision_id` = 992203050001
                    AND t.`gate_code` = s.`gate_code`);

INSERT INTO `proj_project_template_gate_reference`
(`template_revision_id`, `gate_code`, `ref_type`, `ref_code`, `ref_version`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`, `tenant_id`)
SELECT 992203050001, `gate_code`, `ref_type`, `ref_code`, `ref_version`,
       'seed', NOW(), 'seed', NOW(), b'0', NULL, 1
FROM `proj_project_template_gate_reference` s
WHERE s.`tenant_id` = 0 AND s.`template_revision_id` = 992103050001
  AND NOT EXISTS (SELECT 1 FROM `proj_project_template_gate_reference` t
                  WHERE t.`tenant_id` = 1 AND t.`template_revision_id` = 992203050001
                    AND t.`gate_code` = s.`gate_code`
                    AND t.`ref_type` = s.`ref_type` AND t.`ref_code` = s.`ref_code`);

-- F-SOL-003 产品级浏览器验收聚合：只建立PROJ项目、范围、任务与当前执行契约。
-- SOL根、PLT实例/引用、幂等、审计与Outbox均由公开应用命令在验收过程中产生。
INSERT INTO `proj_project`
(`id`, `project_code`, `code_root_id`, `project_sequence`, `code_rule_version`,
 `project_name`, `parent_id`, `root_id`, `tree_path`, `tree_depth`, `tree_sort`,
 `manager_id`, `manager_employee_no`, `manager_name`, `project_type`,
 `signing_method`, `project_category`, `implementation_mode`, `major_project_level`,
 `lifecycle_template_id`, `lifecycle_template_revision_no`, `template_load_method`,
 `process_definition_key`, `process_definition_version`, `creation_reason`,
 `source_type`, `status`, `progress`, `lifecycle_status`, `current_stage`,
 `assignment_status`, `task_tree_version`, `task_progress_version`, `version`,
 `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 992203060001, 'FSOL003-DYNAMIC-FORM-ACCEPTANCE', 992203060001, 0, 'V1',
       'F-SOL-003动态表单浏览器验收项目', NULL, 992203060001, '/', 0, 10,
       1, 'SEED-ADMIN', '超级管理员', 'STANDARD',
       revision.`signing_method`, revision.`project_category`, revision.`implementation_method`,
       revision.`major_project_level`, revision.`template_id`, revision.`revision_no`,
       'MANUAL_SELECTED', revision.`process_definition_key`, revision.`process_definition_version`,
       'F-SOL-003动态表单版本化公开UI/API闭环验收',
       'MIGRATION', 'S1', 0, 'ACTIVE', 'S1', 'ASSIGNED', 1, 0, 0,
       'seed', 'seed', b'0', 1
FROM `proj_project_template_revision` revision
WHERE revision.`tenant_id` = 1 AND revision.`id` = 992203050001
  AND revision.`status` = 'PUBLISHED' AND revision.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `proj_project` project
                  WHERE project.`tenant_id` = 1
                    AND (project.`id` = 992203060001
                      OR project.`project_code` = 'FSOL003-DYNAMIC-FORM-ACCEPTANCE'));

INSERT INTO `proj_project_tree_version`
(`id`, `root_project_id`, `tree_version`, `status`, `change_batch_id`, `node_count`,
 `path_count`, `activated_at`, `version`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 992203061001, project.`id`, 1, 'ACTIVE',
       'FSOL003-DYNAMIC-FORM-ACCEPTANCE-TREE-V1', 1, 1, '2026-08-28 00:00:00',
       0, 'seed', 'seed', b'0', 1
FROM `proj_project` project
WHERE project.`tenant_id` = 1 AND project.`id` = 992203060001
  AND project.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `proj_project_tree_version` treeVersion
                  WHERE treeVersion.`tenant_id` = 1
                    AND (treeVersion.`id` = 992203061001
                      OR (treeVersion.`root_project_id` = 992203060001
                        AND treeVersion.`tree_version` = 1)));

INSERT INTO `proj_project_tree_path`
(`id`, `tree_version`, `root_project_id`, `ancestor_project_id`, `descendant_project_id`,
 `distance`, `version`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 992203062001, 1, project.`id`, project.`id`, project.`id`, 0,
       0, 'seed', 'seed', b'0', 1
FROM `proj_project` project
JOIN `proj_project_tree_version` treeVersion
  ON treeVersion.`tenant_id` = project.`tenant_id`
 AND treeVersion.`root_project_id` = project.`id`
 AND treeVersion.`tree_version` = 1
 AND treeVersion.`status` = 'ACTIVE'
 AND treeVersion.`deleted` = b'0'
WHERE project.`tenant_id` = 1 AND project.`id` = 992203060001
  AND project.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `proj_project_tree_path` treePath
                  WHERE treePath.`tenant_id` = 1
                    AND (treePath.`id` = 992203062001
                      OR (treePath.`root_project_id` = 992203060001
                        AND treePath.`tree_version` = 1
                        AND treePath.`ancestor_project_id` = 992203060001
                        AND treePath.`descendant_project_id` = 992203060001)));

INSERT INTO `proj_project_member_assignment`
(`id`, `project_id`, `user_id`, `employee_no`, `member_name`, `member_role`,
 `assignment_type`, `responsibility`, `effective_from`, `effective_to`, `status`,
 `version`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 992203063001, project.`id`, 1, 'SEED-ADMIN', 'F-SOL-003验收项目经理',
       'PROJECT_MANAGER', 'PRIMARY', 'F-SOL-003动态表单浏览器验收',
       '2026-08-28 00:00:00', NULL, 'ACTIVE', 0, 'seed', 'seed', b'0', 1
FROM `proj_project` project
WHERE project.`tenant_id` = 1 AND project.`id` = 992203060001
  AND project.`manager_id` = 1 AND project.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `proj_project_member_assignment` assignment
                  WHERE assignment.`tenant_id` = 1
                    AND (assignment.`id` = 992203063001
                      OR (assignment.`project_id` = 992203060001
                        AND assignment.`user_id` = 1
                        AND assignment.`member_role` = 'PROJECT_MANAGER'
                        AND assignment.`effective_from` = '2026-08-28 00:00:00')));

INSERT INTO `proj_project_member_assignment`
(`id`, `project_id`, `user_id`, `employee_no`, `member_name`, `member_role`,
 `assignment_type`, `responsibility`, `effective_from`, `effective_to`, `status`,
 `version`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 992203063002, project.`id`, 1, 'SEED-ADMIN', 'F-SOL-003验收一级服务经理',
       'SERVICE_MANAGER_L1', 'PRIMARY', 'F-SOL-003动态表单浏览器验收',
       '2026-08-28 00:00:00', NULL, 'ACTIVE', 0, 'seed', 'seed', b'0', 1
FROM `proj_project` project
WHERE project.`tenant_id` = 1 AND project.`id` = 992203060001
  AND project.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `proj_project_member_assignment` assignment
                  WHERE assignment.`tenant_id` = 1
                    AND (assignment.`id` = 992203063002
                      OR (assignment.`project_id` = 992203060001
                        AND assignment.`user_id` = 1
                        AND assignment.`member_role` = 'SERVICE_MANAGER_L1'
                        AND assignment.`assignment_type` = 'PRIMARY'
                        AND assignment.`effective_from` = '2026-08-28 00:00:00')));

INSERT INTO `proj_project_task`
(`id`, `project_id`, `task_code`, `name`, `parent_task_code`, `parent_task_id`,
 `root_task_id`, `tree_depth`, `business_level_code`, `milestone_id`,
 `plan_start_time`, `plan_end_time`, `actual_start_time`, `actual_end_time`, `progress`,
 `state_machine_revision_id`, `stage_code`, `priority`, `sort_order`, `estimated_hours`,
 `satisfaction_timing`, `description`, `source_definition_id`, `status`, `version`,
 `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 992203070001, project.`id`, definition.`task_code`, definition.`name`, NULL, NULL,
       992203070001, 0, NULL, NULL, NULL, NULL, NULL, NULL, 0,
       stateMachine.`id`, definition.`stage_code`, definition.`priority`, definition.`sort_order`,
       definition.`estimated_hours`, definition.`satisfaction_timing`, definition.`description`,
       definition.`id`, 'PENDING_ASSIGN', 0, 'seed', 'seed', b'0', 1
FROM `proj_project` project
JOIN `proj_project_template_revision` revision
  ON revision.`tenant_id` = project.`tenant_id`
 AND revision.`template_id` = project.`lifecycle_template_id`
 AND revision.`revision_no` = project.`lifecycle_template_revision_no`
JOIN `proj_project_template_task_definition` definition
  ON definition.`tenant_id` = revision.`tenant_id`
 AND definition.`template_revision_id` = revision.`id`
JOIN `proj_task_state_machine_revision` stateMachine
  ON stateMachine.`tenant_id` = project.`tenant_id`
 AND stateMachine.`revision_no` = 1
 AND stateMachine.`status` = 'PUBLISHED'
WHERE project.`tenant_id` = 1 AND project.`id` = 992203060001
  AND project.`deleted` = b'0' AND revision.`id` = 992203050001
  AND revision.`status` = 'PUBLISHED' AND revision.`deleted` = b'0'
  AND definition.`task_definition_key` = 'T-REQ-ANALYSIS'
  AND definition.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `proj_project_task` task
                  WHERE task.`tenant_id` = 1
                    AND (task.`id` = 992203070001
                      OR (task.`project_id` = 992203060001
                        AND task.`task_code` = definition.`task_code`)));

INSERT INTO `proj_task_tree_path`
(`id`, `project_id`, `ancestor_task_id`, `descendant_task_id`, `distance`,
 `version`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 992203071001, task.`project_id`, task.`id`, task.`id`, 0,
       0, 'seed', 'seed', b'0', 1
FROM `proj_project_task` task
WHERE task.`tenant_id` = 1 AND task.`id` = 992203070001 AND task.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `proj_task_tree_path` taskPath
                  WHERE taskPath.`tenant_id` = 1
                    AND (taskPath.`id` = 992203071001
                      OR (taskPath.`project_id` = 992203060001
                        AND taskPath.`ancestor_task_id` = 992203070001
                        AND taskPath.`descendant_task_id` = 992203070001)));

INSERT INTO `proj_project_task_execution_contract`
(`id`, `tenant_id`, `project_task_id`, `template_task_definition_id`,
 `work_binding_type_code`, `target_context_code`, `target_object_type`, `target_object_key`,
 `component_key`, `dynamic_form_revision_id`, `approval_instance_id`,
 `binding_parameter_snapshot`, `permission_policy_ref`, `completion_rule_type_code`,
 `completion_rule_snapshot`, `gate_ref`, `source_definition_version`, `contract_version`,
 `effective_from`, `effective_to`, `version`, `creator`, `updater`, `deleted`)
SELECT 992203080001, 1, task.`id`, definition.`id`,
       'BUSINESS_OBJECT', 'SOL', 'REQUIREMENT_ANALYSIS', 'PRE_04_REQUIREMENT_ANALYSIS',
       NULL, NULL, NULL, definition.`binding_config`, definition.`permission_policy_ref`,
       definition.`completion_rule_type_code`, definition.`completion_rule_config`,
       definition.`gate_ref`, definition.`definition_version`, 1,
       '2026-08-28 00:00:00', NULL, 0, 'seed', 'seed', b'0'
FROM `proj_project_task` task
JOIN `proj_project_template_task_definition` definition
  ON definition.`tenant_id` = task.`tenant_id`
 AND definition.`id` = task.`source_definition_id`
WHERE task.`tenant_id` = 1 AND task.`id` = 992203070001
  AND task.`project_id` = 992203060001 AND task.`deleted` = b'0'
  AND definition.`template_revision_id` = 992203050001
  AND definition.`task_definition_key` = 'T-REQ-ANALYSIS'
  AND definition.`binding_config` = CAST('{"schemaVersion":2,"dynamicFormTemplateId":992203010001,"dynamicFormTemplateRevisionId":992203020001,"dynamicFormRevisionNo":1,"dynamicFormRevisionFactVersion":1}' AS JSON)
  AND NOT EXISTS (SELECT 1 FROM `proj_project_task_execution_contract` contract
                  WHERE contract.`tenant_id` = 1
                    AND (contract.`id` = 992203080001
                      OR (contract.`project_task_id` = 992203070001
                        AND contract.`effective_to` IS NULL)));

-- 本迁移不写system_role_menu；新权限继续由管理员显式授权。
