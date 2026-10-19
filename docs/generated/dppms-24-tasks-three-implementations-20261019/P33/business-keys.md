# P33 业务主键、原身份与配置键

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p33)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

原安装记录id；正式安装历史新ID；设备当前记录唯一性/有效期按设备和既定Owner契约保护。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| INSTALLATION_HISTORY | pm_project_shipment | id |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| INSTALLATION_HISTORY | pm_project_shipment | id |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |

## C 新虚基类

对象键和来源键的实际保存表达式如下；正式行业务定位/幂等冲突继续受目标查询和指纹保护，不能只凭一个原ID覆盖现存行。

| 实现位置 | 身份/键原表达式 |
|---|---|
| [P33InstallationSyncTask.java:37](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:37) | `var bindings=new HashMap<String,SyncIdentityStore.Identity>();var keys=b.rows().stream().map(s->s.sourceId.toString()).toList();for(int i=0;i<keys.size();i+=500)bindings.putAll(c.identities().findByKeys(new SyncIdentityStore.Query("INSTALLATION_HISTORY",keys.subList(i,Math.min(i+500,keys.size())))));` |
| [P33InstallationSyncTask.java:59](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:59) | `@Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){var saves=b.rows().stream().filter(s->s.action==SyncWriteAction.INSERT).map(s->new SyncIdentityStore.Save("INSTALLATION_HISTORY",s.sourceId.toString(),s.target.id,fingerprint(s.target,s),null)).toList();for(int i=0;i<saves.size();i+=500)b.context().identities().saveAll(saves.subList(i,Math.min(i+500,saves.size())));}` |
