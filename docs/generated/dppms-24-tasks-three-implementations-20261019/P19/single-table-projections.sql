-- P19 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE pm_project_related_party; dialect=MYSQL
SELECT
  -- pm_project_related_party.id -> proj_project_party.source_record_key; 业务字段/正式关系
  s.`id` AS `raw__source_record_key`,
  -- pm_project_related_party.projectId -> proj_project_party.project_id; 业务字段/正式关系
  s.`projectId` AS `raw__project_id`,
  -- pm_project_related_party.partyRole -> proj_project_party.party_role; 业务字段/正式关系
  s.`partyRole` AS `raw__party_role`,
  -- pm_project_related_party.partyCode -> proj_project_party.party_code; 业务字段/正式关系
  s.`partyCode` AS `raw__party_code`,
  -- pm_project_related_party.partyName -> proj_project_party.party_name; 业务字段/正式关系
  s.`partyName` AS `raw__party_name`,
  -- pm_project_related_party.createTime -> proj_project_party.create_time; 业务字段/正式关系
  s.`createTime` AS `raw__create_time`,
  -- pm_project_related_party.createBy -> proj_project_party.creator; 业务字段/正式关系
  s.`createBy` AS `raw__creator`,
  -- pm_project_related_party.updateTime -> proj_project_party.update_time; 业务字段/正式关系
  s.`updateTime` AS `raw__update_time`,
  -- pm_project_related_party.updateBy -> proj_project_party.updater; 业务字段/正式关系
  s.`updateBy` AS `raw__updater`,
  -- pm_project_related_party.effectiveFrom -> proj_project_party.effective_from; 业务字段/正式关系
  s.`effectiveFrom` AS `raw__effective_from`,
  -- pm_project_related_party.effectiveTo -> proj_project_party.effective_to; 业务字段/正式关系
  s.`effectiveTo` AS `raw__effective_to`
FROM `pm_project_related_party` s;

-- SOURCE pm_project_state; dialect=MYSQL
SELECT
  -- pm_project_state.projectId -> PROJ项目扩展entity_id; 业务字段/正式关系
  s.`projectId` AS `raw__projectId`,
  -- pm_project_state.projectPlanState -> 暂不迁入; 已批准暂缓
  s.`projectPlanState` AS `raw__projectPlanState`,
  -- pm_project_state.projectplanTime -> 暂不迁入; 已批准暂缓
  s.`projectplanTime` AS `raw__projectplanTime`,
  -- pm_project_state.shipmentState -> plt_entity_extension_value.values_json.shipment_status; 业务字段/正式关系
  s.`shipmentState` AS `raw__shipmentState`,
  -- pm_project_state.shipmentTime -> plt_entity_extension_value.values_json.shipment_status_updated_at; 业务字段/正式关系
  s.`shipmentTime` AS `raw__shipmentTime`,
  -- pm_project_state.executionState -> plt_entity_extension_value.values_json.execution_status; 业务字段/正式关系
  s.`executionState` AS `raw__executionState`,
  -- pm_project_state.executionStateTime -> plt_entity_extension_value.values_json.execution_status_updated_at; 业务字段/正式关系
  s.`executionStateTime` AS `raw__executionStateTime`,
  -- pm_project_state.closeProcessState -> 不迁入; 已批准排除
  s.`closeProcessState` AS `raw__closeProcessState`,
  -- pm_project_state.closeProcessStateTime -> 不迁入; 已批准排除
  s.`closeProcessStateTime` AS `raw__closeProcessStateTime`
FROM `pm_project_state` s;
