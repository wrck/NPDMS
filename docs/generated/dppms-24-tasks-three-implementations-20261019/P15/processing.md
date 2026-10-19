# P15 前后置处理

[返回本任务](README.md)

前置：保持合同客户消歧、原父来源键与精确业务码；运输字典每轮一次；正式P24完整输入不能由预览冒充。

后置：原发货事实完整承接后才做本轮合同/发货引用/包件归集；A有COM临时目录与UPDATE JOIN收尾；预览不运行后置业务UPDATE。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

当前P15-local-owner：本地合同Owner输入及完整P24前置，COM完成实际公司/部门/父合同回填和逐字段汇总；尚无这份新候选真实全量验收。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| createScope | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:4](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:4) |
| createCompanies | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:9](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:9) |
| createDepartments | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:10](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:10) |
| createUniqueContracts | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:17](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:17) |
| updateContractCompanies | update | 是 | [DppmsContractCompletionMapper.xml:91](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:91) |
| updateShipmentReferences | update | 是 | [DppmsContractCompletionMapper.xml:97](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:97) |
| updatePackageReferences | update | 是 | [DppmsContractCompletionMapper.xml:114](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:114) |
| createShipmentFields | runtime_temp_ddl | 是 | [DppmsContractCompletionMapper.xml:120](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:120) |
| createReceivableFields | runtime_temp_ddl | 是 | [DppmsContractCompletionMapper.xml:136](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:136) |
| updateContractFields | update | 是 | [DppmsContractCompletionMapper.xml:170](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:170) |
| dropReceivableFields | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:233](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:233) |
| dropShipmentFields | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:234](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:234) |
| dropUniqueContracts | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:235](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:235) |
| dropDepartments | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:236](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:236) |
| dropCompanies | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:237](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:237) |
| dropScope | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:238](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:238) |
| generic_update_CONTRACT | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |
| generic_update_SHIPMENT_CONTRACT | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |
| generic_update_PACKAGE | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

GenericDirectTaskLine专用五阶段流式；完整旧预览3848169，814个合同客户冲突；不是A新本地Owner候选的验证。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| generic_update_CONTRACT | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |
| generic_update_SHIPMENT_CONTRACT | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |
| generic_update_PACKAGE | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

五个独立步骤，先原发货再本地合同；缺shipment_contract_source_key、500万配置/结构及新P24正式输入；实际条码4283326。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateContract | update | 否 | [P15TargetMapper.xml:82](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P15TargetMapper.xml:82) |
| updateShipmentContract | update | 否 | [P15TargetMapper.xml:85](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P15TargetMapper.xml:85) |
| updatePackage | update | 否 | [P15TargetMapper.xml:88](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P15TargetMapper.xml:88) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### createParameters

[P15DeliverySyncTask.java:22](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:22)

```java
protected Parameters createParameters(SyncContext<?> c){
  var names=new HashMap<String,String>();var ambiguous=new HashSet<String>();
  for(var item:mapper.selectCarriers(new TenantQuery(c.tenantId()))){if(item.code==null || item.label==null || item.label.isBlank())continue;var old=names.putIfAbsent(item.code,item.label);if(old!=null && !old.equals(item.label))ambiguous.add(item.code);}
  ambiguous.forEach(names::remove);return new Parameters(Map.copyOf(names),c.connectionId());
 }
```

### work

[P15DeliverySyncTask.java:27](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:27)

```java
public void work(SyncContext<Parameters> c){
  if(mapper.packageSourceColumnReady()!=1)fail("TARGET_COLUMN_MISSING:com_shipment_package.shipment_contract_source_key");
  try(var session=c.source().open(c.connectionId())){long count=session.mapper(SourceMapper.class).countShipmentEvents(new SourceQuery<>(1,c.tenantId(),true,c.parameters()));if(count>c.maxRows())fail("FULL_SHIPMENT_SOURCE_EXCEEDS_CONFIGURED_LIMIT:"+count+":"+c.maxRows());}
  var prerequisite=mapper.latestP24Execution(new PrerequisiteQuery(c.tenantId(),c.connectionId()));if(prerequisite==null || !"COMPLETED".equals(prerequisite))fail("COMPLETE_FORMAL_BASE_P24_INPUT_REQUIRED");
  syncData(c.forData("ShipmentContract",false,true));syncData(c.forData("Package",false,true));
  syncData(c.forData("ShipmentEvent",false,true));syncData(c.forData("DeviceRelationship",false,true));
  // Contracts consume the now-complete local shipment and P24 facts once, after the raw history phases.
  syncData(c.forLocalData("Contract",false,true));
 }
```

### syncDataInsertBefore

[P15DeliverySyncTask.java:36](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:36)

```java
protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  var c=b.context();String kind=c.dataName();var keys=b.rows().stream().map(s->s.key).toList();
  var bindings=c.identities().findByKeys(new SyncIdentityStore.Query(objectKey(kind),keys));
  var current=new HashMap<String,Row>();
  // Typed scene query per table, with source identity and tenant fixed in XML.
  List<Row> selected=switch(kind){case "Contract"->mapper.selectContracts(new ContractsQuery(c.tenantId(),b.rows().stream().map(s->new ContractIdentity(s.target.companyCode,s.target.contractNo)).toList(),c.preview()));case "ShipmentContract"->mapper.selectShipmentContracts(new KeysQuery(c.tenantId(),keys,c.preview()));case "Package"->mapper.selectPackages(new KeysQuery(c.tenantId(),keys,c.preview()));case "ShipmentEvent"->mapper.selectEvents(new KeysQuery(c.tenantId(),keys,c.preview()));case "DeviceRelationship"->mapper.selectRelationships(new KeysQuery(c.tenantId(),keys,c.preview()));default->throw new IllegalArgumentException("P15_STEP_UNKNOWN");};
  var contractKeys=new HashMap<String,String>();if(kind.equals("Contract"))for(var s:b.rows())contractKeys.put(SyncFingerprint.of(Arrays.asList(s.target.companyCode,s.target.contractNo)),s.key);
  for(var row:selected){String key=kind.equals("Contract")?contractKeys.get(SyncFingerprint.of(Arrays.asList(row.companyCode,row.contractNo))):kind.equals("Package")?row.packageNo:kind.equals("ShipmentEvent")||kind.equals("DeviceRelationship")?row.sourceKey:row.sourceRecordKey;if(!keys.contains(key)||current.putIfAbsent(key,row)!=null)fail("FORMAL_IDENTITY_AMBIGUOUS");}
  for(var s:b.rows()){
   if(s.issue!=null)fail(s.issue);if(s.key==null || s.key.isBlank())fail("RAW_SOURCE_KEY_MISSING");
   var row=s.target;if(kind.equals("ShipmentContract")){var years=DppmsWarrantyYearsConverter.fromSource(s.rawWarranty);row.warrantyYears=years==null?null:years.longValue();}
   if(kind.equals("Package"))row.carrierName=row.carrierCode==null?null:c.parameters().carriers.getOrDefault(row.carrierCode,row.carrierCode);
   validate(new Validation(kind,row));var old=current.get(s.key);var binding=bindings.get(s.key);s.action=SyncWriteAction.UNCHANGED;s.previous=binding==null?null:binding.fingerprint();
   if(old!=null){
    if(kind.equals("Contract")&&(!"DPPMS_ERP".equals(old.masterSourceSystem)||!Objects.equals(old.masterSourceRecordKey,s.key)) || kind.equals("Package")&&!Objects.equals(old.sourceRecordKey,s.key))fail("BUSINESS_KEY_ALREADY_OWNED");
    if(Boolean.TRUE.equals(old.deleted) || binding==null || binding.targetId()!=old.id || !binding.fingerprint().equals(fingerprint(new Validation(kind,old))))fail("EXISTING_FACT_PROTECTED");
    var next=new Row();org.springframework.beans.BeanUtils.copyProperties(old,next);var from=new org.springframework.beans.BeanWrapperImpl(row);var into=new org.springframework.beans.BeanWrapperImpl(next);
    for(var field:sourceFields(kind))into.setPropertyValue(field,from.getPropertyValue(field));
    if(business(new Validation(kind,old)).equals(business(new Validation(kind,next)))){s.target=old;continue;}
    if(kind.equals("ShipmentEvent")||kind.equals("DeviceRelationship"))fail("IMMUTABLE_HISTORY_CHANGED");
    next.updater=Long.toString(c.actorId());next.updateTime=c.startedAt();next.sourceSyncTime=c.startedAt();s.expectedVersion=old.version;
    if(next.version!=null)next.version=Math.addExact(next.version,1);s.target=next;s.action=SyncWriteAction.UPDATE;
   }else{
    if(binding!=null)fail("BOUND_TARGET_MISSING");row.id=c.preview()?null:IdWorker.getId();row.tenantId=c.tenantId();row.deleted=false;
    row.creator=Long.toString(c.actorId());row.updater=row.creator;row.createTime=c.startedAt();row.updateTime=c.startedAt();
    if(kind.equals("Contract")||kind.equals("ShipmentEvent")||kind.equals("DeviceRelationship"))row.version=0L;
    if(kind.equals("Contract"))row.authorityStatus="CONFIRMED";
    if(kind.equals("ShipmentContract")||kind.equals("Package")){row.mappingStatus="PENDING_MAPPING";row.sourceSyncTime=c.startedAt();}
    if(kind.equals("ShipmentEvent")){row.syncStatus="SYNCED";row.syncedAt=c.startedAt();}
    s.action=SyncWriteAction.INSERT;
   }
  }
 }
```

### syncDataInsertAfter

[P15DeliverySyncTask.java:86](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:86)

```java
protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){String kind=b.context().dataName();b.context().identities().saveAll(b.rows().stream().filter(s->s.action!=SyncWriteAction.UNCHANGED).map(s->new SyncIdentityStore.Save(objectKey(kind),s.key,s.target.id,fingerprint(new Validation(kind,s.target)),s.previous)).toList());}
```

