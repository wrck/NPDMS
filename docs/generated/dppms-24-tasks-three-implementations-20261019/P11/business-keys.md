# P11 业务主键、原身份与配置键

[返回本任务](README.md)

CRM itemCode；正式tenant_id + product_code。A/B既有来源键与版本策略见其targets配置；C按修剪后的产品编码批量upsert。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| PRODUCT | DPtech_v_product_info_single | itemCode |  | updateTime |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| PRODUCT/product | ast_product_official_info | product_code | UPSERT |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| PRODUCT | DPtech_v_product_info_single | itemCode |  | updateTime |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| PRODUCT/product | ast_product_official_info | product_code | UPSERT |

## C 新虚基类

对象键和来源键的实际保存表达式如下；正式行业务定位/幂等冲突继续受目标查询和指纹保护，不能只凭一个原ID覆盖现存行。

| 实现位置 | 身份/键原表达式 |
|---|---|
