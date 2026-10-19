# P18 前后置处理

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p18)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

前置：项目组、成员桥、规范项目与合同精确匹配；活动来源优先，最早创建、最新更新及同刻人员冲突规则。

后置：写正式关系及各原来源追溯；真实项目、合同、公司授权不可省，不借预览父ID。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

当前原线完整组输入新工厂；ONLY_FULL_GROUP_BY修复只有常量派生表检查，未真实全量验收新候选。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| correctHistorical | update | 否 | [ProjectContractRelationMigrationMapper.xml:27](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/ProjectContractRelationMigrationMapper.xml:27) |
| restoreWithdrawn | update | 否 | [ProjectContractRelationMigrationMapper.xml:36](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/ProjectContractRelationMigrationMapper.xml:36) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectProjectRelationMapper；82676条预览均ISSUE(PROJECT_BINDING_MISSING)。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| correctHistorical | update | 否 | [DirectProjectRelationMapper.xml:27](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectProjectRelationMapper.xml:27) |
| restoreWithdrawn | update | 否 | [DirectProjectRelationMapper.xml:36](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectProjectRelationMapper.xml:36) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

四份原表查询各一次、运行内有界归并；源查询已不超时，但9个正式审计账号缺失，首个x00270。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### createParameters

[P18ProjectContractSyncTask.java:18](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:18)

```java
@Override protected Parameters createParameters(SyncContext<?> c){var scopes=organization.getActiveScopes(c.actorId());if(scopes==null || scopes.isEmpty())fail("AUTHORIZED_COMPANIES_EMPTY");return new Parameters(scopes.stream().filter(s->s.getId()!=null&&s.getVersion()!=null).map(s->s.getCompanyCode()).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet()));}
```

### work

[P18ProjectContractSyncTask.java:22](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:22)

```java
@Override public void work(SyncContext<Parameters> c){if(c.preview())c.runState().warning("SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED");syncData(c.forData("ProjectContract",false,false));}
```

### syncDataBefore

[P18ProjectContractSyncTask.java:23](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:23)

```java
@Override protected void syncDataBefore(SyncContext<Parameters> c){super.syncDataBefore(c);if(mapper.oneSourcePerTargetIndex()>0){if(!c.preview())fail("METADATA_MULTI_SOURCE_INDEX_REQUIRED:uk_int_base_sync_binding_target");c.runState().warning("P18_EXECUTION_SCHEMA_REQUIRED:uk_int_base_sync_binding_target");}}
```

### syncDataInsertBefore

[P18ProjectContractSyncTask.java:59](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:59)

```java
@Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  var c=b.context();var canonical=b.rows().stream().map(s->s.originalProjectId.toString()).distinct().toList();var refs=new HashMap<String,BaseSyncProjectReferences.Identity>();
  for(int i=0;i<canonical.size();i+=1000)refs.putAll(projects.resolve(new BaseSyncProjectReferences.Query(c.tenantId(),c.actorId(),c.connectionId(),canonical.subList(i,Math.min(i+1000,canonical.size())),c.preview())));
  var business=new LinkedHashMap<String,Source>();for(var s:b.rows())business.putIfAbsent(s.businessKey(),s);
  var sourceKeys=b.rows().stream().map(Source::sourceKey).toList();var originals=new HashMap<String,SyncIdentityStore.Identity>();
  for(int i=0;i<sourceKeys.size();i+=500)originals.putAll(c.identities().findByKeys(new SyncIdentityStore.Query("RELATION_ORIGINAL",sourceKeys.subList(i,Math.min(i+500,sourceKeys.size())))));
  var businessKeys=new ArrayList<>(business.keySet());var bindings=new HashMap<String,SyncIdentityStore.Identity>();
  for(int i=0;i<businessKeys.size();i+=500)bindings.putAll(c.identities().findByKeys(new SyncIdentityStore.Query("RELATION_BUSINESS",businessKeys.subList(i,Math.min(i+500,businessKeys.size())))));
  var contractKeys=new ArrayList<ContractKey>();
  for(var s:business.values()){var p=refs.get(s.originalProjectId.toString());s.companyCode=s.originalCompanyCode==null?p.companyCode():cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter.fromSource(s.originalCompanyCode);
   if(p.version()!=null&&!Objects.equals(p.projectCode(),s.originalProjectCode))fail("PROJECT_CODE_DIFFERS_FROM_SOURCE");
   if(p.companyCode()!=null&&!Objects.equals(p.companyCode(),s.companyCode))fail("PROJECT_COMPANY_DIFFERS_FROM_SOURCE");
   if(s.companyCode==null || !c.parameters().companies.contains(s.companyCode))fail("PROJECT_COMPANY_NOT_AUTHORIZED");contractKeys.add(new ContractKey(s.companyCode,s.contractNo));}
  var contracts=new HashMap<ContractKey,Contract>();
  for(int i=0;i<contractKeys.size();i+=60)for(var contract:mapper.selectContracts(new ContractsQuery(c.tenantId(),contractKeys.subList(i,Math.min(i+60,contractKeys.size())),c.preview()))){var key=new ContractKey(contract.companyCode,contract.contractNo);if(!contractKeys.contains(key) || contract.deleted!=0 || contracts.putIfAbsent(key,contract)!=null)fail("CONTRACT_NOT_EXACTLY_UNIQUE");}
  var targetKeys=new ArrayList<RelationKey>();
  for(var s:business.values()){var p=refs.get(s.originalProjectId.toString());var contract=contracts.get(new ContractKey(s.companyCode,s.contractNo));if(contract==null)fail("FORMAL_CONTRACT_MISSING:"+s.contractNo);s.formalProjectId=p.id();s.formalContractId=contract.id;targetKeys.add(new RelationKey(p.id(),contract.id));}
  var current=new HashMap<RelationKey,Row>();
  for(int i=0;i<targetKeys.size();i+=80)for(var row:mapper.selectRelations(new RelationsQuery(c.tenantId(),targetKeys.subList(i,Math.min(i+80,targetKeys.size())),c.preview()))){if(current.putIfAbsent(new RelationKey(row.projectId,row.contractId),row)!=null)fail("FORMAL_RELATION_AMBIGUOUS");}
  for(var s:business.values()){
   var old=current.get(new RelationKey(s.formalProjectId,s.formalContractId));var binding=bindings.get(s.businessKey());var next=convert(new Plan(s,c));s.action=SyncWriteAction.UNCHANGED;
   if(old!=null){if(binding==null || binding.targetId()!=old.id || !binding.fingerprint().equals(fingerprint(old)) || old.deleted!=0 || !sameFacts(old,next))fail("EXISTING_RELATION_PROTECTED");s.target=old;}
   else{if(binding!=null)fail("BOUND_RELATION_MISSING");next.id=c.preview()?null:IdWorker.getId();s.target=next;s.action=SyncWriteAction.INSERT;}
  }
  for(var s:b.rows()){
   var chosen=business.get(s.businessKey());if(s!=chosen)s.action=SyncWriteAction.UNCHANGED;s.target=chosen.target;
   var previous=originals.get(s.sourceKey());if(previous!=null && (previous.targetId()!=s.target.id || !previous.fingerprint().equals(originalFingerprint(s))))fail("ORIGINAL_RELATION_CHANGED");s.newOriginal=previous==null;
  }
 }
```

### syncDataInsertAfter

[P18ProjectContractSyncTask.java:96](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:96)

```java
@Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){var saves=new ArrayList<SyncIdentityStore.Save>();for(var s:b.rows()){if(s.action==SyncWriteAction.INSERT)saves.add(new SyncIdentityStore.Save("RELATION_BUSINESS",s.businessKey(),s.target.id,fingerprint(s.target),null));if(s.newOriginal)saves.add(new SyncIdentityStore.Save("RELATION_ORIGINAL",s.sourceKey(),s.target.id,originalFingerprint(s),null));}b.context().identities().saveAll(saves);}
```

