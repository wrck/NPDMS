# P16 前后置处理

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p16)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

前置：规范主档与所有有效原ID同一完整范围；正式公司/部门、账号审计及创建/管理权限核验；不把旧数字ID当正式ID。

后置：主档→根树/路径→基础最终客户→全部原ID；不得产生当前经理授权或改写现存项目；A/B的旧完整发布契约独立保留。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

原Master/Identity Owner和不可变范围发布；正式账号及范围结构仍是前置。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| restoreVerifiedBinding | update | 否 | [SyncBindingMapper.xml:14](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:14) |
| updateLastRun | update | 否 | [SyncBindingMapper.xml:95](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:95) |
| deleteTaskBindings | delete | 否 | [SyncBindingMapper.xml:105](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:105) |
| deleteAdapterBindings | delete | 否 | [SyncBindingMapper.xml:108](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:108) |
| deleteObsoleteOrderLines | delete | 否 | [SyncBindingMapper.xml:121](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:121) |
| initializeFormalIfMatch | update | 否 | [ProjectMasterMigrationMapper.xml:14](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:14) |
| restoreHistoricalIfMatch | update | 否 | [ProjectMasterMigrationMapper.xml:197](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:197) |
| correctCustomerIfMatch | update | 否 | [ProjectMasterMapper.xml:5](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:5) |
| associateCustomerIfMissing | update | 否 | [ProjectMasterMapper.xml:11](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:11) |
| updateEndDateIfMatch | update | 否 | [ProjectMasterMapper.xml:21](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:21) |
| incrementVersionIfMatch | update | 否 | [ProjectMasterMapper.xml:41](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:41) |
| updateAssignmentStatusIfVersion | update | 否 | [ProjectMasterMapper.xml:49](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:49) |
| updateBusinessAttributesIfMatch | update | 否 | [ProjectMasterMapper.xml:57](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:57) |
| updateGovernanceStateIfMatch | update | 否 | [ProjectMasterMapper.xml:70](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:70) |
| advanceStageIfMatch | update | 否 | [ProjectMasterMapper.xml:85](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:85) |
| updateManagerMembersIfMatch | update | 否 | [ProjectMasterMapper.xml:94](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:94) |
| updateMigrationParty | update | 否 | [ProjectPartyMapper.xml:20](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/projectmanual/ProjectPartyMapper.xml:20) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectProject*独立持久化，原/新项目来源身份与正式树；历史未建立正式账号输入。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| initializeFormalIfMatch | update | 否 | [DirectProjectMapper.xml:14](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectProjectMapper.xml:14) |
| restoreHistoricalIfMatch | update | 否 | [DirectProjectMapper.xml:197](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectProjectMapper.xml:197) |
| updateMigrationParty | update | 否 | [DirectProjectPartyMapper.xml:12](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectProjectPartyMapper.xml:12) |
| activate | update | 否 | [DirectProjectRootMapper.xml:29](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectProjectRootMapper.xml:29) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

限定type10，不扩其他类型；主档/别名包括UNCHANGED均刷新本次独立run归属，COMPLETED与业务同事务提交，供C下游固定完整范围。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| activateTrees | update | 否 | [P16TargetMapper.xml:12](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P16TargetMapper.xml:12) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### createParameters

[P16ProjectSyncTask.java:29](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:29)

```java
@Override protected Parameters createParameters(SyncContext<?> c){return new Parameters();}
```

### work

[P16ProjectSyncTask.java:33](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:33)

```java
@Override public void work(SyncContext<Parameters> c){
  if(mapper.oneSourcePerTargetIndex()>0){if(!c.preview())fail("METADATA_MULTI_SOURCE_INDEX_REQUIRED:uk_int_base_sync_binding_target");c.runState().warning("P16_EXECUTION_SCHEMA_REQUIRED:uk_int_base_sync_binding_target");}
  var p=c.parameters();
  for(var d:mapper.selectDirectory(new TenantQuery(c.tenantId(),c.preview())))if(d.code!=null&&!d.code.isBlank()&&p.directory.putIfAbsent(d.kind+":"+d.code,d)!=null)fail("FORMAL_DIRECTORY_AMBIGUOUS");
  try(var session=c.source().open(c.connectionId())){
   p.source=session;var q=new SourceQuery<Parameters>(10001,c.tenantId(),c.preview(),p);var source=session.mapper(SourceMapper.class);
   var companies=source.selectCompanies(q);
   if(companies.size()>10000)fail("REFERENCE_CATALOGUE_CAPACITY");
   for(var company:companies)if(p.companies.putIfAbsent(company.id,company.code)!=null)fail("RAW_COMPANY_AMBIGUOUS");
   syncData(c.forData("ProjectMaster",false,true));p.masterPreviewVerified=true;syncData(c.forData("ProjectAlias",false,true));
   if(p.pendingAliases>0)c.runState().warning("P16_ALIASES_REQUIRE_NEW_MASTERS:"+p.pendingAliases);
  }finally{p.source=null;}
 }
```

### syncDataInsertBefore

[P16ProjectSyncTask.java:49](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:49)

```java
@Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){if(b.context().dataName().equals("ProjectAlias")){prepareAliases(b);return;}
  var c=b.context();var keys=b.rows().stream().map(s->s.projectId.toString()).toList();var bindings=c.identities().findByKeys(new SyncIdentityStore.Query("PROJECT_MASTER",keys));var originals=c.identities().findByKeys(new SyncIdentityStore.Query("PROJECT_ORIGINAL",keys));
  var ids=new ArrayList<>(bindings.values().stream().map(SyncIdentityStore.Identity::targetId).toList());ids.addAll(b.rows().stream().map(Source::getProjectId).toList());var current=new HashMap<Long,Row>();var byCode=new HashMap<String,Row>();
  for(var old:mapper.selectProjects(new ProjectsQuery(c.tenantId(),b.rows().stream().map(s->s.projectCode).toList(),ids,c.preview()))){if(!ids.contains(old.id)&&b.rows().stream().noneMatch(s->Objects.equals(s.projectCode,old.projectCode)))fail("PROJECT_CODE_COLLATION_COLLISION");if(current.putIfAbsent(old.id,old)!=null||byCode.putIfAbsent(old.projectCode,old)!=null)fail("FORMAL_PROJECT_IDENTITY_AMBIGUOUS");}
  var sourceCodes=new HashSet<String>();for(var s:b.rows())if(!sourceCodes.add(s.projectCode.toLowerCase(Locale.ROOT)))fail("PROJECT_CODE_CASE_COLLISION");
  var partyBindings=c.identities().findByKeys(new SyncIdentityStore.Query("PROJECT_FINAL_CUSTOMER",keys));var currentParties=new HashMap<String,PartyRow>();
  for(var row:mapper.selectParties(new PartyQuery(c.tenantId(),keys,c.preview())))if(currentParties.putIfAbsent(row.sourceRecordKey,row)!=null)fail("FINAL_CUSTOMER_IDENTITY_AMBIGUOUS");
  for(var s:b.rows()){
   validate(s);var binding=bindings.get(s.projectId.toString());var original=originals.get(s.projectId.toString());s.masterPrevious=binding==null?null:binding.fingerprint();s.originalPrevious=original==null?null:original.fingerprint();var old=binding==null?null:current.get(binding.targetId());s.action=SyncWriteAction.UNCHANGED;
   if(binding!=null&&binding.targetId()!=s.projectId.longValue())fail("BOUND_PROJECT_ID_DIFFERS_FROM_SOURCE");
   if(old!=null){if(Boolean.TRUE.equals(old.deleted)||binding==null||!binding.fingerprint().equals(fingerprint(old))||original==null||original.targetId()!=old.id||!original.fingerprint().equals(originalFingerprint(s)))fail("EXISTING_PROJECT_OR_SOURCE_PROTECTED");s.target=old;}
   else{
    if(current.containsKey(s.projectId))fail("PROJECT_ID_ALREADY_OWNED");
    if(binding!=null||original!=null||byCode.containsKey(s.projectCode))fail("PROJECT_CODE_ALREADY_OWNED");
    var row=convert(new Conversion(s,c));row.id=s.projectId;row.codeRootId=row.id;row.rootId=row.id;s.target=row;s.action=SyncWriteAction.INSERT;
    s.treeVersionId=c.preview()?null:IdWorker.getId();s.pathId=c.preview()?null:IdWorker.getId();
   }
   var oldParty=currentParties.get(s.projectId.toString());var pb=partyBindings.get(s.projectId.toString());
   if(oldParty!=null){if(pb==null||pb.targetId()!=oldParty.id||!pb.fingerprint().equals(partyFingerprint(oldParty))||!Objects.equals(oldParty.projectId,s.target.id)||!Objects.equals(oldParty.partyName,s.column013))fail("EXISTING_FINAL_CUSTOMER_PROTECTED");s.party=oldParty;}
   else if(pb!=null)fail("BOUND_FINAL_CUSTOMER_MISSING");
   else if(s.column013!=null&&!s.column013.isBlank()){
    if(old!=null)fail("BOUND_FINAL_CUSTOMER_MISSING");
    var party=new PartyRow();party.id=c.preview()?null:IdWorker.getId();party.tenantId=c.tenantId();party.projectId=s.target.id;party.partyRole="FINAL_CUSTOMER";party.partyName=s.column013;party.sourceSystem="DPPMS";party.sourceTable="pm_project_header";party.sourceRecordKey=s.projectId.toString();party.effectiveFrom=s.effectiveFrom;party.effectiveTo=s.effectiveTo;party.status="ACTIVE";party.version=0L;party.deleted=0;party.creator=Long.toString(c.actorId());party.updater=party.creator;party.createTime=c.startedAt();party.updateTime=c.startedAt();s.party=party;s.newParty=true;
   }
  }
 }
```

### syncDataInsertAfter

[P16ProjectSyncTask.java:116](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:116)

```java
@Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){var saves=new ArrayList<SyncIdentityStore.Save>();boolean alias=b.context().dataName().equals("ProjectAlias");for(var s:b.rows()){
  if(alias){saves.add(new SyncIdentityStore.Save("PROJECT_ALIAS",s.projectId.toString(),s.target.id,aliasFingerprint(s),s.aliasPrevious));continue;}
  saves.add(new SyncIdentityStore.Save("PROJECT_MASTER",s.projectId.toString(),s.target.id,fingerprint(s.target),s.masterPrevious));saves.add(new SyncIdentityStore.Save("PROJECT_ORIGINAL",s.projectId.toString(),s.target.id,originalFingerprint(s),s.originalPrevious));
  if(s.newParty)saves.add(new SyncIdentityStore.Save("PROJECT_FINAL_CUSTOMER",s.projectId.toString(),s.party.id,partyFingerprint(s.party),null));
 }b.context().identities().saveAll(saves);}
```

