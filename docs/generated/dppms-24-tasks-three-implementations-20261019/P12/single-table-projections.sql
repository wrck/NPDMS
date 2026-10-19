-- P12 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE fb_items; dialect=MYSQL
SELECT
  -- fb_items.id -> 不迁入业务字段; 已批准排除
  s.`id` AS `raw__id`,
  -- fb_items.item -> ast_product_official_info.product_code; 最新裁决；实现待调整
  s.`item` AS `raw__product_code`,
  -- fb_items.describe_ -> ast_product_official_info.product_name；ast_product_official_info.product_desc; 最新裁决；实现待调整
  s.`describe_` AS `raw__product_name`,
  -- fb_items.itemname -> ast_product_official_info.product_model; 最新裁决；实现待调整
  s.`itemname` AS `raw__product_model`
FROM `fb_items` s;
