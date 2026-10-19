# P21 业务主键、原身份与配置键

[返回本任务](README.md)

展示缓存全量快照，无原表稳定唯一键；保留每条重复，技术行序/新ID不代表自然业务键。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| LEASE | pm_project_product_lease_line_from_crm | cacheRowOrdinal |  | — |
| CONFIG | pm_project_product_config_level_info_from_crm | cacheRowOrdinal |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| LEASE/cache | proj_project_lease_product | 无跨轮业务键；完整快照行 | INSERT_ONLY |
| CONFIG/cache | proj_project_product_configuration | 无跨轮业务键；完整快照行 | INSERT_ONLY |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| LEASE | pm_project_product_lease_line_from_crm | cacheRowOrdinal |  | — |
| CONFIG | pm_project_product_config_level_info_from_crm | cacheRowOrdinal |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| LEASE/cache | proj_project_lease_product | 无跨轮业务键；完整快照行 | INSERT_ONLY |
| CONFIG/cache | proj_project_product_configuration | 无跨轮业务键；完整快照行 | INSERT_ONLY |

## C 新虚基类

对象键和来源键的实际保存表达式如下；正式行业务定位/幂等冲突继续受目标查询和指纹保护，不能只凭一个原ID覆盖现存行。

| 实现位置 | 身份/键原表达式 |
|---|---|
