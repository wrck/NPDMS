-- V62: 将 F-PM02 示例项目树绑定到已有 S0～S6 完整链模板
--
-- V61 只创建合法项目树事实；本前向迁移将该专用示例树改绑 V55 已补齐的
-- 910001/revision 2（revision row 911003），保证后续下挂子项目冻结阶段、任务、
-- 里程碑、交付件、门禁及门禁引用的全环节定义。范围严格限制为本轮 seed 树。

UPDATE `proj_project`
SET `signing_method` = 'DIRECT_SIGN',
    `project_category` = 'ENGINEERING',
    `implementation_mode` = 'DIRECT_SERVICE',
    `lifecycle_template_id` = 910001,
    `lifecycle_template_revision_no` = 2,
    `template_load_method` = 'MANUAL_SELECTED',
    `process_definition_key` = 'PROC-PMS-DELIVERY-STD',
    `process_definition_version` = 'v2',
    `version` = `version` + 1,
    `updater` = 'seed',
    `update_time` = NOW()
WHERE `tenant_id` = 0
  AND `creator` = 'seed'
  AND `code_root_id` = 920001
  AND `deleted` = b'0';
