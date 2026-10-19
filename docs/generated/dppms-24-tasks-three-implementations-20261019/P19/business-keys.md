# P19 业务主键、原身份与配置键

C当前只使用PROJECT_PARTY + 原相关方id；PROJECT_STATE暂不处理。

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。四个项目状态扩展已暂缓，不读状态原表或正式扩展定义；相关方预览现停在原项目ID冲突。需解决同一40项项目身份冲突，不能接管已有相关方或项目。

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p19)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

原相关方id和原状态id分别追溯；正式project_id + 来源身份，四项状态属性不充当项目生命周期状态。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| pm_project_related_party | pm_project_related_party | id |  | — |
| pm_project_state | pm_project_state | projectId |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| pm_project_related_party | pm_project_related_party | id |  | — |
| pm_project_state | pm_project_state | projectId |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |

## C 新虚基类

对象键和来源键的实际保存表达式如下；正式行业务定位/幂等冲突继续受目标查询和指纹保护，不能只凭一个原ID覆盖现存行。

| 实现位置 | 身份/键原表达式 |
|---|---|
| [P19ProjectFactsSyncTask.java:24](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P19ProjectFactsSyncTask.java:24) | `var c=b.context();boolean state=c.dataName().equals("ProjectState");var keys=b.rows().stream().map(s->s.sourceId.toString()).toList();var bindings=c.identities().findByKeys(new SyncIdentityStore.Query(state?"PROJECT_STATE":"PROJECT_PARTY",keys));` |
| [P19ProjectFactsSyncTask.java:65](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P19ProjectFactsSyncTask.java) | `@Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){boolean state=b.context().dataName().equals("ProjectState");b.context().identities().saveAll(b.rows().stream().filter(s->s.action!=SyncWriteAction.UNCHANGED).map(s->new SyncIdentityStore.Save(state?"PROJECT_STATE":"PROJECT_PARTY",s.sourceId.toString(),state?s.projectId:s.target.id,state?stateFingerprint(s.extension):fingerprint(s.target),s.previous)).toList());}` |
