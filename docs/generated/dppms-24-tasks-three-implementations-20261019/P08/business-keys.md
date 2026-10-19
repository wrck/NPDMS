# P08 业务主键、原身份与配置键

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p08)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

字典类型：tenant_id + type；字典项：tenant_id + dict_type + value；原id/缺失类型派生键分别追溯。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| fnd_basic_data_type | fnd_basic_data_type | dataTypeCode |  | — |
| fnd_basic_data | fnd_basic_data | id |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| fnd_basic_data_type | fnd_basic_data_type | id |  | — |
| fnd_basic_data_missing_type | fnd_basic_data | sourceId |  | — |
| fnd_basic_data | fnd_basic_data | id |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |

## C 新虚基类

对象键和来源键的实际保存表达式如下；正式行业务定位/幂等冲突继续受目标查询和指纹保护，不能只凭一个原ID覆盖现存行。

| 实现位置 | 身份/键原表达式 |
|---|---|
| [P08DictionarySyncTask.java:42](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P08DictionarySyncTask.java:42) | `var bindings=context.identities().findByKeys(new SyncIdentityStore.Query(domain,batch.rows().stream().map(DictionarySource::sourceKey).toList()));` |
| [P08DictionarySyncTask.java:109](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P08DictionarySyncTask.java:109) | `.map(row->new SyncIdentityStore.Save(domain,row.sourceKey(),row.getTarget().getId(),fingerprint(row,row.getTarget()),null)).toList());` |
