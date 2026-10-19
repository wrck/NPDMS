# P35 业务主键、原身份与配置键

C来源键分别为SOFTWARE_BATCH/原批次id、SOFTWARE_OBSERVATION/原版本id；原logId经本次批次规划或本任务已有绑定承接，禁止旧任务绑定。

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。八个软件历史扩展已暂缓，不读写其属性或定义；基础批次/版本历史预览现停在原项目当前身份冲突。正式父软件批次仍只用本任务独立来源身份和本次规划，不接管旧任务记录。

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p35)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

原批次id和原版本观察id分别追溯；logId是版本历史到批次的必要父关联，同一正式项目内解析。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| SOFTWARE_BATCH | pm_project_soft_change_logs | id |  | — |
| SOFTWARE_VERSION | pm_project_soft_version | id |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| SOFTWARE_BATCH | pm_project_soft_change_logs | id |  | — |
| SOFTWARE_VERSION | pm_project_soft_version | id |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |

## C 新虚基类

对象键和来源键的实际保存表达式如下；正式行业务定位/幂等冲突继续受目标查询和指纹保护，不能只凭一个原ID覆盖现存行。

| 实现位置 | 身份/键原表达式 |
|---|---|
| [P35SoftwareHistorySyncTask.java:34](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P35SoftwareHistorySyncTask.java:34) | `var refs=projects.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(c.tenantId(),c.actorId(),List.copyOf(selected.values()),c.preview()));var keys=b.rows().stream().map(s->s.sourceId.toString()).toList();var bindings=c.identities().findByKeys(new SyncIdentityStore.Query(batch?"SOFTWARE_BATCH":"SOFTWARE_OBSERVATION",keys));` |
| [P35SoftwareHistorySyncTask.java:60](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P35SoftwareHistorySyncTask.java:60) | `@Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){var saves=new ArrayList<SyncIdentityStore.Save>();for(var s:b.rows()){if(s.batch!=null){if(b.context().parameters().batches.putIfAbsent(s.sourceId,new PlannedBatch(s.batch.id,s.batch.projectId))!=null\|\|b.context().parameters().batches.size()>b.context().maxRows())fail("SOURCE_BATCH_DUPLICATE_OR_CAPACITY");if(s.action==SyncWriteAction.INSERT)saves.add(new SyncIdentityStore.Save("SOFTWARE_BATCH",s.sourceId.toString(),s.batch.id,batchFingerprint(s.batch),null));}else if(s.action==SyncWriteAction.INSERT)saves.add(new SyncIdentityStore.Save("SOFTWARE_OBSERVATION",s.sourceId.toString(),s.observation.id,observationFingerprint(s.observation,s.attributes),null));}b.context().identities().saveAll(saves);}` |
