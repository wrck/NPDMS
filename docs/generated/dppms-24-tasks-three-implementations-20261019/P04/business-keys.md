# P04 业务主键、原身份与配置键

[返回本任务](README.md)

账号：tenant_id + 精确username；角色：tenant_id + code；关系：user_id + role_id；范围：账号/公司/部门/语义角色及有效期、审计；每份t/fnd原ID独立追溯。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| t_user | t_user | user_id |  | — |
| t_user_info | t_user_info | id |  | — |
| fnd_user_info | fnd_user_info | id |  | — |
| FND_USER_ROLE | fnd_user_info | id |  | — |
| T_ROLE | t_role | sourceRoleId |  | — |
| FND_ROLE | fnd_roles | sourceRoleId |  | — |
| T_USER_ROLE | t_user_role | id |  | — |
| t_user_role | t_user_role | id |  | — |
| fnd_user_power | fnd_user_power | id |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| t_user | t_user | user_id |  | — |
| t_user_info | t_user_info | id |  | — |
| fnd_user_info | fnd_user_info | id |  | — |
| FND_USER_ROLE | fnd_user_info | id |  | — |
| T_ROLE | t_role | role_id |  | — |
| FND_ROLE | fnd_roles | id |  | — |
| T_USER_ROLE | t_user_role | id |  | — |
| t_user_role | t_user_role | id |  | — |
| fnd_user_power | fnd_user_power | id |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |

## C 新虚基类

对象键和来源键的实际保存表达式如下；正式行业务定位/幂等冲突继续受目标查询和指纹保护，不能只凭一个原ID覆盖现存行。

| 实现位置 | 身份/键原表达式 |
|---|---|
| [P04AccountSyncTask.java:102](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:102) | `static Map<String,SyncIdentityStore.Identity> bindings(BindingRead q){var out=new HashMap<String,SyncIdentityStore.Identity>();for(int i=0;i<q.keys.size();i+=500)out.putAll(q.context.identities().findByKeys(new SyncIdentityStore.Query(q.object,q.keys.subList(i,Math.min(i+500,q.keys.size())))));return out;}` |
| [P04AccountSyncTask.java:142](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:142) | `if(s.user!=null){if(s.action!=SyncWriteAction.UNCHANGED)saves.add(new SyncIdentityStore.Save("ACCOUNT",s.key,s.user.id,userFingerprint(s.user),s.previous));s.user.initialPasswordHash=null;for(var a:s.accounts)saves.add(new SyncIdentityStore.Save(a.kind(),a.id.toString(),s.user.id,SyncFingerprint.of(Arrays.asList(a.kind(),a.id,s.user.username,s.user.id)),s.aliasPrevious.get(a.kind()+":"+a.id)));}` |
| [P04AccountSyncTask.java:143](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:143) | `else if(s.role!=null)saves.add(new SyncIdentityStore.Save("ROLE",s.key,s.role.id,roleFingerprint(s.role),s.previous));` |
| [P04AccountSyncTask.java:144](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:144) | `else if(s.membership!=null)saves.add(new SyncIdentityStore.Save("MEMBERSHIP",s.key,s.membership.id,membershipFingerprint(s.membership),s.previous));else saves.add(new SyncIdentityStore.Save("SCOPE",s.key,s.scope.id,scopeFingerprint(s.scope),s.previous));` |
