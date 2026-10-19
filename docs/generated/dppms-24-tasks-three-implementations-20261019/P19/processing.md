# P19 前后置处理

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。四个项目状态扩展已暂缓，不读状态原表或正式扩展定义；相关方预览现停在原项目ID冲突。需解决同一40项项目身份冲突，不能接管已有相关方或项目。

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p19)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

前置：有效项目范围和当前正式来源身份、审计；PRJ/project四属性正式扩展定义核验。

后置：相关方批量写具名列；shipment/execution状态及时间仅通过正式历史扩展契约维护，不推进当前状态机。

上文保留历史实现，下方C实现已按最新决策刷新。实际预览与未执行写入的边界见本轮报告。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

原ProjectFacts Owner；采用originalLine限定及完整发布范围。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateMigrationParty | update | 否 | [ProjectPartyMapper.xml:20](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/projectmanual/ProjectPartyMapper.xml:20) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectProjectPartyMapper和正式公共扩展；265547条全为ISSUE。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateMigrationParty | update | 否 | [DirectProjectPartyMapper.xml:12](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectProjectPartyMapper.xml:12) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

当前修订优先：[base-sync-department-code-preview-20261019](../base-sync-department-code-preview-20261019.md)；源/目标SQL及关联更新：[实际XML](../base-sync-department-code-preview-20261019.sql)。A/B上文仍是历史实现，不在本轮运行。

只处理ProjectParty及PROJECT_PARTY独立绑定；四个项目状态属性暂不读取、写入或校验。

### work

[P19ProjectFactsSyncTask.java:19](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P19ProjectFactsSyncTask.java:19)

```java
@Override public void work(SyncContext<Void> c){if(c.preview())c.runState().warning("SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED");c.runState().warning("P19_EXTENSION_ATTRIBUTES_DEFERRED:shipment_status,shipment_status_updated_at,execution_status,execution_status_updated_at");syncData(c.forData("ProjectParty",false,true));}
```

### syncDataInsertBefore

[P19ProjectFactsSyncTask.java:20](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P19ProjectFactsSyncTask.java:20)

```java
@Override protected void syncDataInsertBefore(SyncBatch<Source,Void> b){
  var c=b.context();var keys=b.rows().stream().map(s->s.sourceId.toString()).toList();var bindings=c.identities().findByKeys(new SyncIdentityStore.Query("PROJECT_PARTY",keys));
  var refs=projects.resolve(new BaseSyncProjectReferences.Query(c.tenantId(),c.actorId(),c.connectionId(),b.rows().stream().map(s->s.canonicalProjectId.toString()).distinct().toList(),c.preview()));
  var current=new HashMap<String,PartyRow>();
  for(var old:mapper.selectParties(new PartiesQuery(c.tenantId(),keys,c.preview())))if(!keys.contains(old.sourceRecordKey)||current.putIfAbsent(old.sourceRecordKey,old)!=null)fail("PARTY_SOURCE_AMBIGUOUS");
  for(var s:b.rows()){
   if(s.sourceId==null || s.sourceId<=0 || !"10".equals(s.projectType))fail("SOURCE_IDENTITY_INVALID");var p=refs.get(s.canonicalProjectId.toString());if(!s.projectType.equals(p.projectType()))fail("PROJECT_TYPE_CHANGED");
   if(p.version()!=null&&!Objects.equals(p.projectCode(),s.originalProjectCode))fail("PROJECT_CODE_DIFFERS_FROM_SOURCE");
   var binding=bindings.get(s.sourceId.toString());s.previous=binding==null?null:binding.fingerprint();s.projectId=p.id();s.projectVersion=p.version();s.action=SyncWriteAction.UNCHANGED;
   if(!Set.of("10","20","30").contains(Objects.toString(s.partyRole,"")))fail("PARTY_ROLE_UNMAPPED");
   if(s.createTime==null || s.effectiveFrom!=null&&s.effectiveTo!=null&&s.effectiveTo.isBefore(s.effectiveFrom))fail("ORIGINAL_AUDIT_OR_INTERVAL_MISSING");
   checkTextLength(s.partyCode,128);checkTextLength(s.partyName,1024);
   var old=current.get(s.sourceId.toString());if(old!=null&&(binding==null||binding.targetId()!=old.id||Boolean.TRUE.equals(old.deleted)||!binding.fingerprint().equals(fingerprint(old))))fail("EXISTING_PARTY_PROTECTED");if(old==null&&binding!=null)fail("BOUND_PARTY_MISSING");
   var row=new PartyRow();if(old!=null)org.springframework.beans.BeanUtils.copyProperties(old,row);
   row.tenantId=c.tenantId();row.projectId=p.id();row.partyRole=s.partyRole;row.partyCode=s.partyCode;row.partyName=s.partyName;row.effectiveFrom=s.effectiveFrom;row.effectiveTo=s.effectiveTo;
   row.creator=s.resolvedCreator;row.updater=s.resolvedUpdater;row.createTime=s.createTime;row.updateTime=s.updateTime==null?s.createTime:s.updateTime;row.sourceSystem="DPPMS";row.sourceTable="pm_project_related_party";row.sourceRecordKey=s.sourceId.toString();
   if(old==null){row.id=c.preview()?null:IdWorker.getId();row.status="ACTIVE";row.version=0;row.deleted=false;s.action=SyncWriteAction.INSERT;}
   else if(!fingerprint(old).equals(fingerprint(row))){row.version=Math.addExact(old.version,1);s.expectedVersion=old.version;s.action=SyncWriteAction.UPDATE;}
   s.target=row;
  }
 }
```

### syncDataInsert

[P19ProjectFactsSyncTask.java:44](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P19ProjectFactsSyncTask.java:44)

```java
@Override protected void syncDataInsert(SyncBatch<Source,Void> b){super.syncDataInsert(b.matching(s->s.action==SyncWriteAction.INSERT));super.syncDataUpdate(b.matching(s->s.action==SyncWriteAction.UPDATE));}
```

### syncDataInsertAfter

[P19ProjectFactsSyncTask.java:45](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P19ProjectFactsSyncTask.java:45)

```java
@Override protected void syncDataInsertAfter(SyncBatch<Source,Void> b){b.context().identities().saveAll(b.rows().stream().filter(s->s.action!=SyncWriteAction.UNCHANGED).map(s->new SyncIdentityStore.Save("PROJECT_PARTY",s.sourceId.toString(),s.target.id,fingerprint(s.target),s.previous)).toList());}
```
