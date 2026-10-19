# P20 前后置处理

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p20)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

前置：有效规范项目和唯一上游SMS原行精确对应；数量、审计、现存完整清单保护。

后置：保存正式真实产品清单及上游来源事实；保留原业务码，可空产品ID留空，不产生设备分配。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

ProjectRealProduct Owner及原线范围。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectRealProductMapper，7818条预览全为缺正式项目ISSUE。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

完整清单显式规划，必要project_id必须正式；当前在正式项目来源身份前置处FAILED。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### work

[P20RealProductSyncTask.java:19](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P20RealProductSyncTask.java:19)

```java
@Override public void work(SyncContext<Void> c){if(c.preview())c.runState().warning("SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED");syncData(c.forData("RealProduct",false,false));}
```

### syncDataInsertBefore

[P20RealProductSyncTask.java:20](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P20RealProductSyncTask.java:20)

```java
@Override protected void syncDataInsertBefore(SyncBatch<Source,Void> b){
  var c=b.context();var references=new HashMap<String,BaseSyncProjectReferences.Identity>();
  var canonical=b.rows().stream().map(s->Objects.toString(s.canonicalProjectId,null)).distinct().toList();
  for(int i=0;i<canonical.size();i+=1000)references.putAll(projects.resolve(new BaseSyncProjectReferences.Query(c.tenantId(),c.actorId(),c.connectionId(),canonical.subList(i,Math.min(i+1000,canonical.size())),c.preview())));
  var bindings=new HashMap<String,SyncIdentityStore.Identity>();var sourceKeys=b.rows().stream().map(s->s.sourceId.toString()).toList();
  for(int i=0;i<sourceKeys.size();i+=500)bindings.putAll(c.identities().findByKeys(new SyncIdentityStore.Query("REAL_PRODUCT",sourceKeys.subList(i,Math.min(i+500,sourceKeys.size())))));
  var ids=references.values().stream().map(BaseSyncProjectReferences.Identity::id).distinct().toList();var current=new HashMap<Long,Row>();var occupied=new HashSet<Long>();
  for(int i=0;i<ids.size();i+=500)for(var row:mapper.selectProjects(new ProjectsQuery(c.tenantId(),ids.subList(i,Math.min(i+500,ids.size())),c.preview()))){if(current.putIfAbsent(row.id,row)!=null || current.size()>c.maxRows())fail("TARGET_SCOPE_TOO_LARGE_OR_DUPLICATE");occupied.add(row.projectId);}
  var sourceBusiness=new HashSet<String>();var sourceIds=new HashSet<Long>();
  for(var s:b.rows()){
   validate(s);if(!sourceIds.add(s.sourceId))fail("SOURCE_ID_DUPLICATE");var p=references.get(s.canonicalProjectId.toString());
   if(!s.projectType.equals(p.projectType()))fail("PROJECT_TYPE_CHANGED");
   if(p.version()!=null&&!Objects.equals(p.projectCode(),s.originalProjectCode))fail("PROJECT_CODE_DIFFERS_FROM_SOURCE");
   var row=convert(s);row.projectId=p.id();row.tenantId=c.tenantId();
   if(!sourceBusiness.add(business(row)))fail("SOURCE_BUSINESS_DUPLICATE");
   var binding=bindings.get(s.sourceId.toString());s.action=SyncWriteAction.UNCHANGED;
   if(binding!=null){var old=current.get(binding.targetId());if(old==null || Boolean.TRUE.equals(old.deleted) || !binding.fingerprint().equals(fingerprint(old)) || !business(old).equals(business(row)))fail("EXISTING_IMMUTABLE_LIST_CHANGED");s.target=old;continue;}
   if(occupied.contains(p.id()))fail("EXISTING_PROJECT_LIST_CANNOT_APPEND");
   row.id=c.preview()?null:IdWorker.getId();row.creator=Long.toString(c.actorId());row.updater=row.creator;row.createTime=c.startedAt();row.updateTime=c.startedAt();row.deleted=false;
   s.target=row;s.action=SyncWriteAction.INSERT;
  }
 }
```

### syncDataInsertAfter

[P20RealProductSyncTask.java:56](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P20RealProductSyncTask.java:56)

```java
@Override protected void syncDataInsertAfter(SyncBatch<Source,Void> b){b.context().identities().saveAll(b.rows().stream().filter(s->s.action==SyncWriteAction.INSERT).map(s->new SyncIdentityStore.Save("REAL_PRODUCT",s.sourceId.toString(),s.target.id,fingerprint(s.target),null)).toList());}
```

