# P04 前后置处理

[返回本任务](README.md)

前置：严格既定账号范围，FND优先；EHR精确工号和三项姓名例外；391→88、两组FND别名及五个仅角色账号沿既有裁决。

后置：账号→角色→成员关系→公司部门范围；原admin只作审计引用；保存各原ID、缓存刷新；无源密码迁入。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

当前候选合并P04/P05/P06/P07；历史预览仍是四个组件，不能算合并候选已验收。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateHistoricalDefinition | update | 否 | [RoleMigrationMapper.xml:39](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/resources/mapper/permission/RoleMigrationMapper.xml:39) |
| restoreHistoricalAudit | update | 否 | [RoleMigrationMapper.xml:44](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/resources/mapper/permission/RoleMigrationMapper.xml:44) |
| updateHistoricalRelation | update | 否 | [UserScopeMigrationMapper.xml:58](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/resources/mapper/permission/UserScopeMigrationMapper.xml:58) |
| updateHistoricalProfile | update | 否 | [UserMigrationMapper.xml:62](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/resources/mapper/user/UserMigrationMapper.xml:62) |
| restoreNullHistoricalUpdater | update | 否 | [UserMigrationMapper.xml:70](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/resources/mapper/user/UserMigrationMapper.xml:70) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

复用账号解释器、替换为DirectAccount*持久化；5015为旧来源投影读数，ADOPTED等是预览动作。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| deletePosts | update | 否 | [DirectAccountAuxiliaryMapper.xml:10](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectAccountAuxiliaryMapper.xml:10) |
| updateHistoricalDefinition | update | 否 | [DirectAccountRoleMapper.xml:39](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectAccountRoleMapper.xml:39) |
| updateHistoricalRelation | update | 否 | [DirectAccountScopeMapper.xml:58](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectAccountScopeMapper.xml:58) |
| updateHistoricalProfile | update | 否 | [DirectAccountUserMapper.xml:39](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectAccountUserMapper.xml:39) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

获批任职历史存custom_info.employmentAssignments；isSysUser原值存systemPresetUser，不授管理员；330任职/1176逻辑账号；12632是四步骤候选行计数。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateUser | update | 否 | [P04TargetMapper.xml:6](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:6) |
| updateRole | update | 否 | [P04TargetMapper.xml:9](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:9) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### createParameters

[P04AccountSyncTask.java:31](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:31)

```java
protected Parameters createParameters(SyncContext<?> c){return new Parameters();}
```

### work

[P04AccountSyncTask.java:32](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:32)

```java
public void work(SyncContext<Parameters> c){
  var p=c.parameters();load(c);planUsers(c);
  if(mapper.oneSourcePerTargetIndex()>0){if(!c.preview())fail("METADATA_MULTI_SOURCE_INDEX_REQUIRED:uk_int_base_sync_binding_target");c.runState().warning("P04_EXECUTION_SCHEMA_REQUIRED:uk_int_base_sync_binding_target");}syncData(c.forData("User",false,false));planRoles(c);syncData(c.forData("Role",false,false));planMemberships(c);syncData(c.forData("Membership",false,false));planScopes(c);syncData(c.forData("Scope",false,false));
  c.runState().warning("P04_ORIGINAL_READ:"+p.rawCount+";EXCLUDED_ACCOUNTS:"+p.excludedAccounts+";ORPHAN_PROFILES:"+p.orphanProfiles);
 }
```

### syncDataInsertAfter

[P04AccountSyncTask.java:141](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:141)

```java
protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){var saves=new ArrayList<SyncIdentityStore.Save>();for(var s:b.rows()){
  if(s.user!=null){if(s.action!=SyncWriteAction.UNCHANGED)saves.add(new SyncIdentityStore.Save("ACCOUNT",s.key,s.user.id,userFingerprint(s.user),s.previous));s.user.initialPasswordHash=null;for(var a:s.accounts)saves.add(new SyncIdentityStore.Save(a.kind(),a.id.toString(),s.user.id,SyncFingerprint.of(Arrays.asList(a.kind(),a.id,s.user.username,s.user.id)),s.aliasPrevious.get(a.kind()+":"+a.id)));}
  else if(s.role!=null)saves.add(new SyncIdentityStore.Save("ROLE",s.key,s.role.id,roleFingerprint(s.role),s.previous));
  else if(s.membership!=null)saves.add(new SyncIdentityStore.Save("MEMBERSHIP",s.key,s.membership.id,membershipFingerprint(s.membership),s.previous));else saves.add(new SyncIdentityStore.Save("SCOPE",s.key,s.scope.id,scopeFingerprint(s.scope),s.previous));
 }for(int i=0;i<saves.size();i+=500)b.context().identities().saveAll(saves.subList(i,Math.min(i+500,saves.size())));}
```

### syncDataAfter

[P04AccountSyncTask.java:146](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:146)

```java
protected void syncDataAfter(SyncContext<Parameters> c){if(c.dataName().equals("Role")||c.dataName().equals("Membership")){String name=c.dataName().equals("Role")?cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants.ROLE:cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants.USER_ROLE_ID_LIST;var cache=caches.getCache(name);if(cache!=null)org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){public void afterCommit(){cache.clear();}});}}
```

