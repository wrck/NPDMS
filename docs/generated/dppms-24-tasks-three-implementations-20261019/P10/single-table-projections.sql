-- P10 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE Account; dialect=SQLSERVER
SELECT
  -- Account.AccountNumber -> cus_customer_master.code; 已保存查询/映射
  s.[AccountNumber] AS [raw__code],
  -- Account.Name -> cus_customer_master.name; 已保存查询/映射
  s.[Name] AS [raw__name],
  -- Account.Telephone1 -> cus_customer_master.contact_phone; 已保存查询/映射
  s.[Telephone1] AS [raw__contact_phone],
  -- Account.EMailAddress1 -> cus_customer_master.contact_email; 已保存查询/映射
  s.[EMailAddress1] AS [raw__contact_email],
  -- Account.Address1_Composite -> cus_customer_master.address; 已保存查询/映射
  s.[Address1_Composite] AS [raw__address],
  -- Account.new_mkt_unit_id -> cus_customer_master.department_code; 已保存查询/映射
  s.[new_mkt_unit_id] AS [raw__department_code],
  -- Account.new_mkt_unit_idName -> cus_customer_master.department_name; 已保存查询/映射
  s.[new_mkt_unit_idName] AS [raw__department_name],
  -- Account.new_industry_level1_id -> cus_customer_master.market_code; 已保存查询/映射
  s.[new_industry_level1_id] AS [raw__market_code],
  -- Account.new_industry_level1_idName -> cus_customer_master.market_name; 已保存查询/映射
  s.[new_industry_level1_idName] AS [raw__market_name],
  -- Account.new_industry_level2_id -> cus_customer_master.system_code; 已保存查询/映射
  s.[new_industry_level2_id] AS [raw__system_code],
  -- Account.new_industry_level2_idName -> cus_customer_master.system_name; 已保存查询/映射
  s.[new_industry_level2_idName] AS [raw__system_name],
  -- Account.new_industry_level3_id -> cus_customer_master.expend_code; 已保存查询/映射
  s.[new_industry_level3_id] AS [raw__expend_code],
  -- Account.new_industry_level3_idName -> cus_customer_master.expend_name; 已保存查询/映射
  s.[new_industry_level3_idName] AS [raw__expend_name],
  -- Account.new_industry_level4_id -> cus_customer_master.industry_code; 已保存查询/映射
  s.[new_industry_level4_id] AS [raw__industry_code],
  -- Account.new_industry_level4_idName -> cus_customer_master.industry_name; 已保存查询/映射
  s.[new_industry_level4_idName] AS [raw__industry_name],
  -- Account.StateCode -> cus_customer_master.lifecycle_status; 已保存查询/映射
  s.[StateCode] AS [raw__lifecycle_status],
  -- Account.ModifiedOn -> cus_customer_master.data_as_of; 已保存查询/映射
  s.[ModifiedOn] AS [raw__data_as_of],
  -- Account.migrationIssue -> 来源预检问题，不写业务列; 已保存查询/映射
  s.[migrationIssue] AS [raw__migrationIssue],
  -- Account.AccountId -> CUS来源身份＋INT来源绑定; 来源身份
  s.[AccountId] AS [raw__AccountId]
FROM [Account] s;
