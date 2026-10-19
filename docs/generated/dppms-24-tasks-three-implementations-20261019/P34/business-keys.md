# P34 业务主键、原身份与配置键

[返回本任务](README.md)

无原稳定唯一主键；原SN+四版本完整值及重复出现序号仅用于逐历史行技术身份，不去重丢原行。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| fb_soft_version | fb_soft_version | factoryRowKey | serial_number,conp,cpld,boot,pcb | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| fb_soft_version | fb_soft_version | factoryRowKey | serial_number,conp,cpld,boot,pcb | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |

## C 新虚基类

对象键和来源键的实际保存表达式如下；正式行业务定位/幂等冲突继续受目标查询和指纹保护，不能只凭一个原ID覆盖现存行。

| 实现位置 | 身份/键原表达式 |
|---|---|
| [P34FactoryHistorySyncTask.java:37](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P34FactoryHistorySyncTask.java:37) | `row.setSourceKey(digest+":"+occurrence[0]);` |
| [P34FactoryHistorySyncTask.java:43](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P34FactoryHistorySyncTask.java:43) | `var byKey=context.identities().findByKeys(new SyncIdentityStore.Query("FACTORY_HISTORY",batch.rows().stream().map(FactorySyncSource::getSourceKey).toList()));` |
| [P34FactoryHistorySyncTask.java:82](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P34FactoryHistorySyncTask.java:82) | `return new SyncIdentityStore.Save("FACTORY_HISTORY",source.getSourceKey(),row.getId(),fingerprint(row),null);` |
