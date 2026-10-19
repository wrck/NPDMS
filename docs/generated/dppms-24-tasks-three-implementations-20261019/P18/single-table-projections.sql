-- P18 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE pm_project_contract; dialect=MYSQL
SELECT
  -- pm_project_contract.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- pm_project_contract.contractNo -> com_project_contract_relation.contract_id; 业务字段/正式关系
  s.`contractNo` AS `raw__contractNo`,
  -- pm_project_contract.projectGroupCode -> com_project_contract_relation.project_id；com_project_contract_relation.contract_id; 业务字段/正式关系
  s.`projectGroupCode` AS `raw__project_id`,
  -- pm_project_contract.createTime -> com_project_contract_relation.create_time; 业务字段/正式关系
  s.`createTime` AS `raw__create_time`,
  -- pm_project_contract.createBy -> com_project_contract_relation.creator; 业务字段/正式关系
  s.`createBy` AS `raw__creator`,
  -- pm_project_contract.updateTime -> com_project_contract_relation.update_time; 业务字段/正式关系
  s.`updateTime` AS `raw__update_time`,
  -- pm_project_contract.updateBy -> com_project_contract_relation.updater; 业务字段/正式关系
  s.`updateBy` AS `raw__updater`
FROM `pm_project_contract` s;

-- SOURCE pm_project_group; dialect=MYSQL
SELECT
  -- pm_project_group.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- pm_project_group.projectGroupCode -> com_project_contract_relation的精确展开依据; 关联依据
  s.`projectGroupCode` AS `raw__projectGroupCode`,
  -- pm_project_group.projectGroupName -> 不迁入; 已批准排除
  s.`projectGroupName` AS `raw__projectGroupName`,
  -- pm_project_group.projectType -> proj_project.project_type（原查询匹配约束）; 关联依据
  s.`projectType` AS `raw__project_type`,
  -- pm_project_group.createTime -> 不迁入; 已批准排除
  s.`createTime` AS `raw__createTime`,
  -- pm_project_group.createBy -> 不迁入; 已批准排除
  s.`createBy` AS `raw__createBy`,
  -- pm_project_group.updateTime -> 不迁入; 已批准排除
  s.`updateTime` AS `raw__updateTime`,
  -- pm_project_group.updateBy -> 不迁入; 已批准排除
  s.`updateBy` AS `raw__updateBy`
FROM `pm_project_group` s;

-- SOURCE pm_project_group_relationship; dialect=MYSQL
SELECT
  -- pm_project_group_relationship.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- pm_project_group_relationship.projectGroupCode -> com_project_contract_relation.contract_id; 业务字段/正式关系
  s.`projectGroupCode` AS `raw__contract_id`,
  -- pm_project_group_relationship.projectCode -> com_project_contract_relation.project_id；com_project_contract_relation.status; 业务字段/正式关系
  s.`projectCode` AS `raw__project_id`,
  -- pm_project_group_relationship.mergeBranchMark -> 不迁入; 已批准排除
  s.`mergeBranchMark` AS `raw__mergeBranchMark`,
  -- pm_project_group_relationship.smsProjectCode -> com_crm_execution_order.project_code（正式合同→订单→执行单链）; 正式链映射尚未闭合
  s.`smsProjectCode` AS `raw__project_code`,
  -- pm_project_group_relationship.createTime -> com_project_contract_relation.create_time; 业务字段/正式关系
  s.`createTime` AS `raw__create_time`,
  -- pm_project_group_relationship.createBy -> com_project_contract_relation.creator; 业务字段/正式关系
  s.`createBy` AS `raw__creator`,
  -- pm_project_group_relationship.updateTime -> com_project_contract_relation.update_time; 业务字段/正式关系
  s.`updateTime` AS `raw__update_time`,
  -- pm_project_group_relationship.updateBy -> com_project_contract_relation.updater; 业务字段/正式关系
  s.`updateBy` AS `raw__updater`
FROM `pm_project_group_relationship` s;
