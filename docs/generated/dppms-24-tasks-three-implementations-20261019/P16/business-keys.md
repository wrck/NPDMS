# P16 业务主键、原身份与配置键

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p16)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

先对二进制同project_type + project_code取MAX(projectId)，再校验规范行及原别名有效；正式主档按租户/项目编码与本套件来源归属保护。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| pm_project_header | pm_project_header | projectId |  | — |
| pm_project_identity | pm_project | originalProjectId |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| pm_project_header | pm_project_header | projectId |  | — |
| pm_project_identity | pm_project | originalProjectId |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |

## C 新虚基类

对象键和来源键的实际保存表达式如下；正式行业务定位/幂等冲突继续受目标查询和指纹保护，不能只凭一个原ID覆盖现存行。

| 实现位置 | 身份/键原表达式 |
|---|---|
| [P16ProjectSyncTask.java:53](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:53) | `var c=b.context();var keys=b.rows().stream().map(s->s.projectId.toString()).toList();var bindings=c.identities().findByKeys(new SyncIdentityStore.Query("PROJECT_MASTER",keys));var originals=c.identities().findByKeys(new SyncIdentityStore.Query("PROJECT_ORIGINAL",keys));` |
| [P16ProjectSyncTask.java:57](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:57) | `var partyBindings=c.identities().findByKeys(new SyncIdentityStore.Query("PROJECT_FINAL_CUSTOMER",keys));var currentParties=new HashMap<String,PartyRow>();` |
| [P16ProjectSyncTask.java:76](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:76) | `void prepareAliases(SyncBatch<Source,Parameters> b){var c=b.context();var canonical=b.rows().stream().map(s->s.canonicalProjectId.toString()).distinct().toList();var masters=c.identities().findByKeys(new SyncIdentityStore.Query("PROJECT_MASTER",canonical));var aliases=c.identities().findByKeys(new SyncIdentityStore.Query("PROJECT_ALIAS",b.rows().stream().map(s->s.projectId.toString()).toList()));` |
| [P16ProjectSyncTask.java:124](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:124) | `if(alias){saves.add(new SyncIdentityStore.Save("PROJECT_ALIAS",s.projectId.toString(),s.target.id,aliasFingerprint(s),s.aliasPrevious));continue;}` |
| [P16ProjectSyncTask.java:125](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:125) | `saves.add(new SyncIdentityStore.Save("PROJECT_MASTER",s.projectId.toString(),s.target.id,fingerprint(s.target),s.masterPrevious));saves.add(new SyncIdentityStore.Save("PROJECT_ORIGINAL",s.projectId.toString(),s.target.id,originalFingerprint(s),s.originalPrevious));` |
| [P16ProjectSyncTask.java:126](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:126) | `if(s.newParty)saves.add(new SyncIdentityStore.Save("PROJECT_FINAL_CUSTOMER",s.projectId.toString(),s.party.id,partyFingerprint(s.party),null));` |
