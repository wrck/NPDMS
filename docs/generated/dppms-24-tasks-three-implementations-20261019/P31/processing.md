# P31 前后置处理

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p31)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

前置：完整已批准项目范围；成员角色10/20/30与联系人60分支；联系人须正式最终客户相关方。

后置：成员授权标志保持false；联系人不接管当前主联系人；保留原人员编码，可空user_id/employee_no不猜填。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

原历史Owner与role60联系人同次采集；当前授权仍是另一批准路径。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| closeApprovedInterval | update | 否 | [ProjectMemberHistoryMigrationMapper.xml:23](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/legacydetail/ProjectMemberHistoryMigrationMapper.xml:23) |
| closeEffectiveServiceManagerAssignments | update | 否 | [ProjectMemberAssignmentMapper.xml:146](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:146) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectMemberHistory/ContactHistory Mapper；缺V482及旧完整范围发布，真实业务未到达。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

仅新P16完整COMPLETED范围，来源指纹/成员数验证；不依赖旧发布表；当前缺该正式输入。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### createParameters

[P31MemberContactSyncTask.java:18](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P31MemberContactSyncTask.java:18)

```java
@Override protected Parameters createParameters(SyncContext<?> c){return new Parameters();}
```

### work

[P31MemberContactSyncTask.java:21](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P31MemberContactSyncTask.java:21)

```java
@Override public void work(SyncContext<Parameters> c){if(c.preview())c.runState().warning("SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED");c.parameters().publication=publications.load(new BaseSyncPublishedProjects.Query(c.tenantId(),c.connectionId(),c.maxRows(),c.source(),c.preview()));syncData(c.forData("MemberContact",false,true));}
```

### syncDataInsertBefore

[P31MemberContactSyncTask.java:27](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P31MemberContactSyncTask.java:27)

```java
@Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  var c=b.context();var publication=c.parameters().publication;var selected=new LinkedHashMap<String,BaseSyncPublishedProjects.Member>();
  for(var s:b.rows()){if(s.sourceId==null||s.sourceId<=0||s.originalProjectId==null)fail("SOURCE_IDENTITY_INVALID");var member=publication.members().get(s.originalProjectId.toString());if(member==null||!Objects.equals(member.projectType(),s.projectType))fail("SOURCE_OUTSIDE_PUBLISHED_SCOPE");selected.put(member.sourceKey(),member);}
  var refs=projects.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(c.tenantId(),c.actorId(),List.copyOf(selected.values()),c.preview()));var ids=refs.values().stream().map(BaseSyncProjectReferences.Identity::id).distinct().toList();
  var keys=b.rows().stream().map(s->s.sourceId.toString()).toList();var members=c.identities().findByKeys(new SyncIdentityStore.Query("MEMBER_HISTORY",keys));var contacts=c.identities().findByKeys(new SyncIdentityStore.Query("CONTACT_HISTORY",keys));
  var q=new TargetQuery(c.tenantId(),c.connectionId(),ids,c.maxRows()+1,c.preview());var currentMembers=mapper.selectMembers(q);var currentContacts=mapper.selectContacts(q);
  if(currentMembers.size()>c.maxRows()||currentContacts.size()>c.maxRows())fail("FORMAL_HISTORY_CAPACITY");
  var parties=new HashMap<Long,FinalCustomer>();for(var p:mapper.selectFinalCustomers(q))if(parties.putIfAbsent(p.projectId,p)!=null)fail("FINAL_CUSTOMER_AMBIGUOUS");
  for(var s:b.rows()){
   if(!Set.of("10","20","30","60").contains(Objects.toString(s.memberRole,""))||!"10".equals(s.projectType))fail("SOURCE_ROLE_OR_PROJECT_TYPE_INVALID");
   if(s.effectiveFrom!=null&&s.effectiveTo!=null&&s.effectiveTo.isBefore(s.effectiveFrom)||s.createTime!=null&&s.createTime.getNano()!=0||s.updateTime!=null&&s.updateTime.getNano()!=0)fail("SOURCE_INTERVAL_OR_AUDIT_PRECISION_INVALID");
   var p=refs.get(s.originalProjectId.toString());s.action=SyncWriteAction.INSERT;
   if("60".equals(s.memberRole)){
    if(s.memberName==null||s.memberName.isBlank()||s.createTime==null)fail("CONTACT_P31MemberContactSyncTask_OR_ORIGINAL_AUDIT_REQUIRED");
    var party=parties.get(p.id());var canonical=publication.canonical().get(p.id());
    if(party==null||!Objects.equals(party.sourceRecordKey,canonical.sourceKey()))fail("CANONICAL_FINAL_CUSTOMER_REQUIRED:"+p.id());
    var row=new ContactRow();row.tenantId=c.tenantId();row.projectId=p.id();row.projectPartyId=party.id;row.contactCode=s.memberCode;row.name=s.memberName;row.phone=s.phoneNum;row.email=s.email;row.registrationSource=s.fromFlag;row.effectiveFrom=s.effectiveFrom;row.effectiveTo=s.effectiveTo;row.creator=s.resolvedCreator;row.updater=s.resolvedUpdater;row.createTime=s.createTime;row.updateTime=s.updateTime==null?s.createTime:s.updateTime;row.primaryFlag=false;row.status=0;row.version=0;row.deleted=false;
    validateContact(row);var binding=contacts.get(s.sourceId.toString());var old=binding==null?null:currentContacts.stream().filter(r->Objects.equals(r.id,binding.targetId())).findFirst().orElse(null);
    if(binding!=null){if(old==null||Boolean.TRUE.equals(old.deleted)||!binding.fingerprint().equals(contactFingerprint(old)))fail("EXISTING_CONTACT_PROTECTED");row.id=old.id;row.version=old.version;if(!contactFingerprint(row).equals(contactFingerprint(old)))fail("CONTACT_HISTORY_IMMUTABLE");s.action=SyncWriteAction.UNCHANGED;s.contact=old;}
    else{if(currentContacts.stream().anyMatch(r->!Boolean.TRUE.equals(r.owned)&&sameContact(r,row)))fail("ORIGINAL_CONTACT_CANNOT_ADOPT");row.id=c.preview()?null:IdWorker.getId();s.contact=row;}
   }else{
    var row=new MemberRow();row.tenantId=c.tenantId();row.projectId=p.id();row.memberCode=s.memberCode;row.memberName=s.memberName;row.phoneNumber=s.phoneNum;row.email=s.email;row.registrationSource=s.fromFlag;row.memberRole=switch(s.memberRole){case "10"->"SALES_REPRESENTATIVE";case "20"->"SERVICE_MANAGER";case "30"->"PROJECT_MANAGER";default->throw new IllegalArgumentException();};row.companyId=p.companyId();row.companyCode=p.companyCode();row.companyName=p.companyName();row.effectiveFrom=s.effectiveFrom;row.effectiveTo=s.effectiveTo;row.creator=s.resolvedCreator;row.updater=s.resolvedUpdater;row.createTime=s.createTime;row.updateTime=s.updateTime==null?s.createTime:s.updateTime;row.authorizationConfirmed=false;row.status="ACTIVE";row.version=0;row.deleted=false;
    validateMember(row);var binding=members.get(s.sourceId.toString());var old=binding==null?null:currentMembers.stream().filter(r->Objects.equals(r.id,binding.targetId())).findFirst().orElse(null);
    if(binding!=null){if(old==null||Boolean.TRUE.equals(old.deleted)||!binding.fingerprint().equals(memberFingerprint(old)))fail("EXISTING_MEMBER_PROTECTED");row.id=old.id;if(!memberFingerprint(row).equals(memberFingerprint(old)))fail("MEMBER_HISTORY_IMMUTABLE");s.action=SyncWriteAction.UNCHANGED;s.member=old;}
    else{if(currentMembers.stream().anyMatch(r->!Boolean.TRUE.equals(r.owned)&&sameMember(r,row)))fail("ORIGINAL_MEMBER_CANNOT_ADOPT");row.id=c.preview()?null:IdWorker.getId();s.member=row;}
   }
  }
 }
```

### syncDataInsertAfter

[P31MemberContactSyncTask.java:65](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P31MemberContactSyncTask.java:65)

```java
@Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){b.context().identities().saveAll(b.rows().stream().filter(s->s.action==SyncWriteAction.INSERT).map(s->new SyncIdentityStore.Save(s.member!=null?"MEMBER_HISTORY":"CONTACT_HISTORY",s.sourceId.toString(),s.member!=null?s.member.id:s.contact.id,s.member!=null?memberFingerprint(s.member):contactFingerprint(s.contact),null)).toList());}
```

