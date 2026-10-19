# P01 业务主键、原身份与配置键

[返回本任务](README.md)

公司/部门：tenant_id + code；原compID/depID/fnd id仅作来源身份，父部门按已核实父键解析。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| COMPANY | ehr_company | compID |  | — |
| DEPARTMENT | ehr_department | depID |  | — |
| fnd_department | fnd_department | id |  | — |
| ehr_department | ehr_department | depID |  | — |
| COMPANY_REFERENCE | t_company | sourceCompanyId |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| COMPANY | ehr_company | sourceId |  | — |
| DEPARTMENT | ehr_department | sourceId |  | — |
| fnd_department | fnd_department | sourceId |  | — |
| ehr_department | ehr_department | sourceId |  | — |
| COMPANY_REFERENCE | t_company | sourceCompanyId |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |

## C 新虚基类

对象键和来源键的实际保存表达式如下；正式行业务定位/幂等冲突继续受目标查询和指纹保护，不能只凭一个原ID覆盖现存行。

| 实现位置 | 身份/键原表达式 |
|---|---|
| [P01OrganizationSyncTask.java:83](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P01OrganizationSyncTask.java:83) | `for(var kind:List.of("COMPANY","DEPARTMENT")){var selected=b.rows().stream().filter(s->kind.equals(s.kind)).map(s->s.code).toList();if(!selected.isEmpty())bindings.putAll(c.identities().findByKeys(new SyncIdentityStore.Query(kind,selected)).entrySet().stream().collect(java.util.stream.Collectors.toMap(e->kind+":"+e.getKey(),Map.Entry::getValue)));}` |
| [P01OrganizationSyncTask.java:111](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P01OrganizationSyncTask.java:111) | `@Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){b.context().identities().saveAll(b.rows().stream().filter(s->s.action==SyncWriteAction.INSERT).map(s->new SyncIdentityStore.Save(s.kind,s.code,s.target.id,fingerprint(s.target),null)).toList());}` |
