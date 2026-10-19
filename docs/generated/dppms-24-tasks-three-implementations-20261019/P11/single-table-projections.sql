-- P11 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE DPtech_v_product_info_single; dialect=SQLSERVER
SELECT
  -- DPtech_v_product_info_single.itemCode -> ast_product_official_info.product_code; 已保存查询/映射
  s.[itemCode] AS [raw__product_code],
  -- DPtech_v_product_info_single.itemModel -> ast_product_official_info.product_model; 已保存查询/映射
  s.[itemModel] AS [raw__product_model],
  -- DPtech_v_product_info_single.itemDesc -> ast_product_official_info.product_desc; 已保存查询/映射
  s.[itemDesc] AS [raw__product_desc],
  -- DPtech_v_product_info_single.remark -> ast_product_official_info.technical_spec; 已保存查询/映射
  s.[remark] AS [raw__technical_spec],
  -- DPtech_v_product_info_single.updateTime -> ast_product_official_info.synced_at; 已保存查询/映射
  s.[updateTime] AS [raw__synced_at],
  -- DPtech_v_product_info_single.statecode -> ast_product_official_info.status; 已保存查询/映射
  s.[statecode] AS [raw__status],
  -- DPtech_v_product_info_single.createTime -> 来源窗口排序参考; 来源选择参考
  s.[createTime] AS [raw__createTime]
FROM [DPtech_v_product_info_single] s;
