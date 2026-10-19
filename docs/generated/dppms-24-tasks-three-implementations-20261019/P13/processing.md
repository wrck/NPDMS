# P13 前后置处理

[返回本任务](README.md)

前置：有效规范type10项目范围；同业务键完整最新修订，不用多个MAX拼接载荷；可空组织ID不强制回填。

后置：保存表头及每个原始id的来源身份；消费者人工字段不覆盖。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

原执行单Owner处理规范修订和目录引用；历史99895条全为ISSUE。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updatePrimaryProjectIfUnbound | update | 否 | [CrmExecutionOrderMapper.xml:30](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/executionorder/CrmExecutionOrderMapper.xml:30) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectExecutionOrderMapper；历史1895条与原方式读数不构成同一范围证明。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateRow | update | 否 | [DirectExecutionOrderMapper.xml:15](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectExecutionOrderMapper.xml:15) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

项目前缀目录一次读取，执行单原表流式读取；99895入选、2625不入选；多来源索引是执行前置，预览不预测跨批实际写数。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateExecutionOrder | update | 否 | [P13TargetMapper.xml:9](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P13TargetMapper.xml:9) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### createParameters

[P13ExecutionOrderSyncTask.java:19](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P13ExecutionOrderSyncTask.java:19)

```java
protected Parameters createParameters(SyncContext<?> c){return new Parameters();}
```

### work

[P13ExecutionOrderSyncTask.java:33](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P13ExecutionOrderSyncTask.java:33)

```java
public void work(SyncContext<Parameters> context){syncData(context.forData("ExecutionOrder",false,true));if(context.parameters().excluded>0)context.runState().warning("P13_OUTSIDE_REVIEWED_PROJECT_SCOPE:"+context.parameters().excluded);if(context.preview())context.runState().warning("P13_PREVIEW_SOURCE_VALIDATED;WRITE_COUNTS_DEFERRED_UNTIL_EXECUTION");}
```

### syncDataBefore

[P13ExecutionOrderSyncTask.java:34](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P13ExecutionOrderSyncTask.java:34)

```java
protected void syncDataBefore(SyncContext<Parameters> context){super.syncDataBefore(context);if(mapper.oneSourcePerTargetIndex()>0){if(!context.preview())fail("METADATA_MULTI_SOURCE_INDEX_REQUIRED:uk_int_base_sync_binding_target");context.runState().warning("P13_EXECUTION_SCHEMA_REQUIRED:uk_int_base_sync_binding_target");}}
```

### syncDataInsertBefore

[P13ExecutionOrderSyncTask.java:35](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P13ExecutionOrderSyncTask.java:35)

```java
protected void syncDataInsertBefore(SyncBatch<Source,Parameters> batch){
  var c=batch.context();var keys=batch.rows().stream().map(Source::businessKey).distinct().toList();
  var bindings=c.identities().findByKeys(new SyncIdentityStore.Query("EXECUTION_HEADER",keys));
  var originals=c.identities().findByKeys(new SyncIdentityStore.Query("EXECUTION_ORIGINAL",batch.rows().stream().map(s->s.sourceId.toString()).toList()));
  var oldByKey=new HashMap<String,Row>();
  // Small bounded OR groups also keep the production parser's serialized tree within its stack budget.
  for(int i=0;i<batch.rows().size();i+=80){
   var chosen=batch.rows().subList(i,Math.min(i+80,batch.rows().size())).stream().map(s->new Key(s.sourceSystem,s.executionNo)).distinct().toList();
   for(var old:mapper.selectExisting(new ExistingQuery(c.tenantId(),chosen,c.preview()))){
    if(old.getTenantId()!=c.tenantId() || !chosen.contains(new Key(old.getSourceSystem(),old.getExecutionNo())))fail("BUSINESS_IDENTITY_DIFFER");
    String key=SyncFingerprint.of(Arrays.asList(old.getSourceSystem(),old.getExecutionNo()));var prior=oldByKey.putIfAbsent(key,old);
    if(prior!=null && !prior.equals(old))fail("BUSINESS_IDENTITY_AMBIGUOUS");
   }
  }
  var staged=new HashMap<String,Row>();
  for(var s:batch.rows()){
   s.action=SyncWriteAction.UNCHANGED;
   if(s.sourceId==null || s.sourceId<=0 || s.canonicalSourceId==null || s.canonicalSourceId<s.sourceId)fail("REVISION_INVALID");
   var incoming=convert(s);var key=s.businessKey();var binding=bindings.get(key);var old=oldByKey.get(key);var original=originals.get(s.sourceId.toString());
   if(old!=null && (Boolean.TRUE.equals(old.getDeleted()) || binding==null || binding.targetId()!=old.getId() || !binding.fingerprint().equals(fingerprint(old))))fail("EXISTING_HEADER_PROTECTED");
   if(old==null && binding!=null || original!=null && (old==null || original.targetId()!=old.getId() || !original.fingerprint().equals(identityFingerprint(old))))fail("SOURCE_BINDING_CHANGED");
   s.previous=binding==null?null:binding.fingerprint();s.originalPrevious=original==null?null:original.fingerprint();
   Row next=staged.get(key);
   if(next==null){
    next=incoming;
    if(old!=null){
     next=new Row();org.springframework.beans.BeanUtils.copyProperties(old,next);
     var from=new org.springframework.beans.BeanWrapperImpl(incoming);var into=new org.springframework.beans.BeanWrapperImpl(next);
     for(var field:SOURCE_FIELDS){var value=from.getPropertyValue(field);if(value!=null)into.setPropertyValue(field,value);}
     if(incoming.engineeringFeeRaw!=null)next.engineeringFee=incoming.engineeringFee;
     if(!business(old).equals(business(next))){next.version=Math.addExact(old.version,1);next.updater=Long.toString(c.actorId());next.updateTime=c.startedAt();next.sourceSyncTime=c.startedAt();s.expectedVersion=old.version;s.action=SyncWriteAction.UPDATE;}
    }
    if(old==null){next.setId(c.preview()?null:IdWorker.getId());next.setTenantId(c.tenantId());next.setVersion(0L);next.setDeleted(false);
     next.setStatus("ACTIVE");next.setAfEvidenceStatus("UNKNOWN");next.setCreator(Long.toString(c.actorId()));next.setUpdater(next.getCreator());
     next.setCreateTime(c.startedAt());next.setUpdateTime(c.startedAt());next.setSourceSyncTime(c.startedAt());s.action=SyncWriteAction.INSERT;}
    staged.put(key,next);
   }
   s.target=next;s.newOriginal=original==null;
  }
 }
```

### syncDataInsertAfter

[P13ExecutionOrderSyncTask.java:77](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P13ExecutionOrderSyncTask.java:77)

```java
protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){
  var saves=new ArrayList<SyncIdentityStore.Save>();
  for(var s:b.rows()){
   if(s.action!=SyncWriteAction.UNCHANGED)saves.add(new SyncIdentityStore.Save("EXECUTION_HEADER",s.businessKey(),s.target.id,fingerprint(s.target),s.previous));
   if(s.newOriginal)saves.add(new SyncIdentityStore.Save("EXECUTION_ORIGINAL",s.sourceId.toString(),s.target.id,identityFingerprint(s.target),s.originalPrevious));
  }
  b.context().identities().saveAll(saves);
 }
```

