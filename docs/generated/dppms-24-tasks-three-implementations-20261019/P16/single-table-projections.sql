-- P16 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE pm_project; dialect=MYSQL
SELECT
  -- pm_project.projectId -> int_sync_binding.source_key；int_sync_binding.target_id; 身份桥接
  s.`projectId` AS `raw__projectId`,
  -- pm_project.projectType -> 正式项目身份核验及绑定字段; 身份核验
  s.`projectType` AS `raw__projectType`,
  -- pm_project.projectCode -> 正式项目身份核验及绑定字段; 身份核验
  s.`projectCode` AS `raw__projectCode`,
  -- pm_project.projectName -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`projectName` AS `raw__projectName`,
  -- pm_project.projectState -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`projectState` AS `raw__projectState`,
  -- pm_project.isback -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`isback` AS `raw__isback`,
  -- pm_project.column001 -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`column001` AS `raw__column001`,
  -- pm_project.column002 -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`column002` AS `raw__column002`,
  -- pm_project.column003 -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`column003` AS `raw__column003`,
  -- pm_project.column004 -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`column004` AS `raw__column004`,
  -- pm_project.column005 -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`column005` AS `raw__column005`,
  -- pm_project.column006 -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`column006` AS `raw__column006`,
  -- pm_project.column007 -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`column007` AS `raw__column007`,
  -- pm_project.column008 -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`column008` AS `raw__column008`,
  -- pm_project.column009 -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`column009` AS `raw__column009`,
  -- pm_project.column010 -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`column010` AS `raw__column010`,
  -- pm_project.column011 -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`column011` AS `raw__column011`,
  -- pm_project.column012 -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`column012` AS `raw__column012`,
  -- pm_project.columno12_readonly -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`columno12_readonly` AS `raw__columno12_readonly`,
  -- pm_project.column013 -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`column013` AS `raw__column013`,
  -- pm_project.column014 -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`column014` AS `raw__column014`,
  -- pm_project.customerProjectName -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`customerProjectName` AS `raw__customerProjectName`,
  -- pm_project.salesType -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`salesType` AS `raw__salesType`,
  -- pm_project.majorProjectLevel -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`majorProjectLevel` AS `raw__majorProjectLevel`,
  -- pm_project.compId -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`compId` AS `raw__compId`,
  -- pm_project.createTime -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`createTime` AS `raw__createTime`,
  -- pm_project.createBy -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`createBy` AS `raw__createBy`,
  -- pm_project.updateTime -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`updateTime` AS `raw__updateTime`,
  -- pm_project.updateBy -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`updateBy` AS `raw__updateBy`,
  -- pm_project.effectiveFrom -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`effectiveFrom` AS `raw__effectiveFrom`,
  -- pm_project.effectiveTo -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`effectiveTo` AS `raw__effectiveTo`,
  -- pm_project.disabled -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`disabled` AS `raw__disabled`,
  -- pm_project.projectStartTime -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`projectStartTime` AS `raw__projectStartTime`,
  -- pm_project.projectRefreshTime -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`projectRefreshTime` AS `raw__projectRefreshTime`,
  -- pm_project.projectCloseTime -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`projectCloseTime` AS `raw__projectCloseTime`,
  -- pm_project.customInfo -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`customInfo` AS `raw__customInfo`,
  -- pm_project.customConfig -> 本任务不写；主档资料由P16承接; 其他任务/参考
  s.`customConfig` AS `raw__customConfig`
FROM `pm_project` s;
