# P22 业务主键、原身份与配置键

当前P22以[订单行延后及原部门编码报告](../base-sync-p22-order-line-preview-20261019.md)和[实际XML](../base-sync-p22-order-line-preview-20261019.sql)为准：p.column001原查询直接带入；department_id/order_line_id均先-1，后处理仅限当前独立绑定；正式订单缺失不阻断读源。当前真实预览缺原部门编码，正数量14293条；2976条零数量按已有裁决待处理。下方旧阶段流程按此修订解释。

C范围来源键为DPPMS:pm_project_product_line:原projectId:原行id；部门业务匹配键为tenant_id + 精确编码，数字department_id先-1。

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。正式订单行缺失：首条源记录id=1的精确订单号/行号/产品码在目标匹配0条，完整项目合同关联也为0条。343858条范围中14525条缺原部门编码，其余非空编码均可按租户匹配。需正式订单输入及缺失部门业务键；数值部门ID用-1不替代缺失编码。

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p22)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

原实施范围id/数量明细id；正式project_id + 合同/订单行/办事处来源业务键及必要范围父关联。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| pm_project_product_line | pm_project_product_line | id |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| pm_project_product_line | pm_project_product_line | id |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |

## C 新虚基类

对象键和来源键的实际保存表达式如下；正式行业务定位/幂等冲突继续受目标查询和指纹保护，不能只凭一个原ID覆盖现存行。

| 实现位置 | 身份/键原表达式 |
|---|---|
| [P22ProductScopeSyncTask.java:24](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P22ProductScopeSyncTask.java:24) | `var bindings=c.identities().findByKeys(new SyncIdentityStore.Query("PRODUCT_SCOPE",keys));` |
| [P22ProductScopeSyncTask.java:78](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P22ProductScopeSyncTask.java:78) | `@Override protected void syncDataInsertAfter(SyncBatch<Source,Void> b){b.context().identities().saveAll(b.rows().stream().filter(s->s.action==SyncWriteAction.INSERT).map(s->new SyncIdentityStore.Save("PRODUCT_SCOPE",s.sourceId.toString(),s.target.id,fingerprint(s.target,s.detail),null)).toList());}` |
