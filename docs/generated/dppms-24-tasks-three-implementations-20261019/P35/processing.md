# P35 前后置处理

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。八个软件历史扩展已暂缓，不读写其属性或定义；基础批次/版本历史预览现停在原项目当前身份冲突。正式父软件批次仍只用本任务独立来源身份和本次规划，不接管旧任务记录。

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p35)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

前置：完整项目范围、原时间/标志精度及AST/SoftwareVersionRecord八属性正式定义；原_bak/_change值不可丢弃。

后置：先批次、后版本观察；同次父ID真实；八属性通过正式扩展初始化，失败回滚两层；可空设备ID留空。

上文保留历史实现，下方C实现已按最新决策刷新。实际预览与未执行写入的边界见本轮报告。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

原组合Owner；没有正式八属性定义及当前候选真实运行。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| correctHistoricalAudit | update | 否 | [DeviceSoftwareChangeBatchMapper.xml:7](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/version/DeviceSoftwareChangeBatchMapper.xml:7) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectSoftwareBatch/History Mapper；仍在旧发布范围前置失败。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

当前修订优先：[base-sync-department-code-preview-20261019](../base-sync-department-code-preview-20261019.md)；源/目标SQL及关联更新：[实际XML](../base-sync-department-code-preview-20261019.sql)。A/B上文仍是历史实现，不在本轮运行。

处理SoftwareBatch→SoftwareObservation；SOFTWARE_BATCH和SOFTWARE_OBSERVATION仅使用本任务独立绑定，原logId通过本次批次规划取得正式父ID。预览新父ID保持待定，八个扩展属性不读取、写入或校验。

### work

[P35SoftwareHistorySyncTask.java:22](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P35SoftwareHistorySyncTask.java:22)

```java
@Override public void work(SyncContext<Parameters> c){if(c.preview())c.runState().warning("SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED");
  c.parameters().publication=publications.load(new BaseSyncPublishedProjects.Query(c.tenantId(),c.connectionId(),c.maxRows(),c.source(),c.preview()));
  c.runState().warning("P35_EXTENSION_ATTRIBUTES_DEFERRED:conp_bak,conp_change,boot_bak,boot_change,cpld_bak,cpld_change,pcb_bak,pcb_change");
  try(var source=c.source().open(c.connectionId())){c.parameters().source=source;syncData(c.forData("SoftwareBatch",false,true));syncData(c.forData("SoftwareObservation",false,true));}finally{c.parameters().source=null;}
 }
```

### syncDataQueryByRowHandler

[P35SoftwareHistorySyncTask.java:28](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P35SoftwareHistorySyncTask.java:28)

```java
@Override protected void syncDataQueryByRowHandler(SyncContext<Parameters> c,ResultHandler<Source> handler){var keys=new ArrayList<>(c.parameters().publication.members().keySet());Collections.sort(keys);for(int i=0;i<keys.size();i+=500)syncDataQueryBySql(c.parameters().source.sqlSession(),new MappedCall<>(c,sourceMapperNamespace()+".select"+c.dataName(),new ReadQuery(c.maxRows()+1,keys.subList(i,Math.min(i+500,keys.size())))),handler);}
```

### syncDataInsertBefore

[P35SoftwareHistorySyncTask.java:29](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P35SoftwareHistorySyncTask.java:29)

```java
@Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  var c=b.context();boolean batch=c.dataName().equals("SoftwareBatch");var selected=new LinkedHashMap<String,BaseSyncPublishedProjects.Member>();
  for(var s:b.rows()){if(s.sourceId==null||s.sourceId<=0||s.originalProjectId==null)fail("SOURCE_IDENTITY_INVALID");var member=c.parameters().publication.members().get(s.originalProjectId.toString());if(member==null)fail("SOURCE_OUTSIDE_PUBLISHED_SCOPE");selected.put(member.sourceKey(),member);}
  var refs=projects.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(c.tenantId(),c.actorId(),List.copyOf(selected.values()),c.preview()));var keys=b.rows().stream().map(s->s.sourceId.toString()).toList();var bindings=c.identities().findByKeys(new SyncIdentityStore.Query(batch?"SOFTWARE_BATCH":"SOFTWARE_OBSERVATION",keys));
  var ids=bindings.values().stream().map(SyncIdentityStore.Identity::targetId).toList();var q=new TargetQuery(c.tenantId(),ids,c.preview());var batches=new HashMap<Long,BatchRow>();var observations=new HashMap<Long,ObservationRow>();if(!ids.isEmpty()){if(batch)mapper.selectBatches(q).forEach(r->batches.put(r.id,r));else mapper.selectObservations(q).forEach(r->observations.put(r.id,r));}
  for(var s:b.rows()){
   if(s.createTime==null||s.createTime.getNano()%1000000!=0||s.updateTime!=null&&s.updateTime.getNano()%1000000!=0)fail("ORIGINAL_AUDIT_REQUIRED_OR_PRECISION_INVALID");var p=refs.get(s.originalProjectId.toString());var binding=bindings.get(s.sourceId.toString());s.action=SyncWriteAction.INSERT;
   if(batch){
    if(s.latest!=null&&!Set.of(0,1).contains(s.latest))fail("LATEST_FLAG_INVALID");var row=new BatchRow();row.tenantId=c.tenantId();row.projectId=p.id();row.changeVersion=s.changeVersion;row.changeRemark=s.changeRemark;row.latest=s.latest;row.creator=s.resolvedCreator;row.updater=s.resolvedUpdater;row.createTime=s.createTime;row.updateTime=s.updateTime==null?s.createTime:s.updateTime;row.deleted=false;validateBatch(row);
    var old=binding==null?null:batches.get(binding.targetId());if(binding!=null){if(old==null||Boolean.TRUE.equals(old.deleted)||!binding.fingerprint().equals(batchFingerprint(old)))fail("EXISTING_BATCH_PROTECTED");row.id=old.id;if(!batchFingerprint(row).equals(batchFingerprint(old)))fail("BATCH_HISTORY_IMMUTABLE");s.action=SyncWriteAction.UNCHANGED;}else row.id=c.preview()?null:IdWorker.getId();s.batch=row;if(c.preview()&&(c.parameters().batches.putIfAbsent(s.sourceId,new PlannedBatch(row.id,row.projectId))!=null||c.parameters().batches.size()>c.maxRows()))fail("SOURCE_BATCH_DUPLICATE_OR_CAPACITY");
   }else{
    var row=new ObservationRow();row.tenantId=c.tenantId();row.projectId=p.id();row.deviceSn=s.barCode;row.contractNo=s.contractNo;row.productCode=s.itemCode;row.conpVersion=s.conp;row.conpType=s.conpType;row.conpSeries=s.conpSeries;row.conpMark=s.conpMark;row.bootVersion=s.boot;row.cpldVersion=s.cpld;row.pcbVersion=s.pcb;row.executeTime=s.executeTime;row.dataState=s.datastate;row.customInfo=s.customInfo;row.recordedAt=s.createTime;row.dataSource="DPPMS";row.creator=s.resolvedCreator;row.updater=s.resolvedUpdater;row.createTime=s.createTime;row.updateTime=s.updateTime==null?s.createTime:s.updateTime;row.deleted=false;
    if(s.logId!=null){var parent=c.parameters().batches.get(s.logId);if(parent==null||!Objects.equals(parent.projectId,p.id()))fail("ORIGINAL_BATCH_NOT_IN_SAME_PUBLISHED_PROJECT");row.changeBatchId=parent.id;}
    validateObservation(row);if(row.customInfo!=null)BaseSyncJson.canonicalJson(row.customInfo);var old=binding==null?null:observations.get(binding.targetId());
    if(binding!=null){if(old==null||Boolean.TRUE.equals(old.deleted))fail("BOUND_OBSERVATION_MISSING");row.id=old.id;if(!binding.fingerprint().equals(observationFingerprint(old)))fail("EXISTING_OBSERVATION_PROTECTED");if(!observationFingerprint(row).equals(observationFingerprint(old)))fail("OBSERVATION_HISTORY_IMMUTABLE");s.action=SyncWriteAction.UNCHANGED;}
    else row.id=c.preview()?null:IdWorker.getId();
    s.observation=row;
   }
  }
 }
```

### syncDataInsert

[P35SoftwareHistorySyncTask.java:56](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P35SoftwareHistorySyncTask.java:56)

```java
@Override protected void syncDataInsert(SyncBatch<Source,Parameters> b){var fresh=b.matching(s->s.action==SyncWriteAction.INSERT);super.syncDataInsert(fresh);}
```

### syncDataInsertAfter

[P35SoftwareHistorySyncTask.java:57](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P35SoftwareHistorySyncTask.java:57)

```java
@Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){var saves=new ArrayList<SyncIdentityStore.Save>();for(var s:b.rows()){if(s.batch!=null){if(b.context().parameters().batches.putIfAbsent(s.sourceId,new PlannedBatch(s.batch.id,s.batch.projectId))!=null||b.context().parameters().batches.size()>b.context().maxRows())fail("SOURCE_BATCH_DUPLICATE_OR_CAPACITY");if(s.action==SyncWriteAction.INSERT)saves.add(new SyncIdentityStore.Save("SOFTWARE_BATCH",s.sourceId.toString(),s.batch.id,batchFingerprint(s.batch),null));}else if(s.action==SyncWriteAction.INSERT)saves.add(new SyncIdentityStore.Save("SOFTWARE_OBSERVATION",s.sourceId.toString(),s.observation.id,observationFingerprint(s.observation),null));}b.context().identities().saveAll(saves);}
```
