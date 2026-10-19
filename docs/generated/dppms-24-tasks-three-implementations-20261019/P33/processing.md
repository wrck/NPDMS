# P33 前后置处理

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p33)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

前置：完整项目范围、正式SN设备、项目选择/管理授权；设备不能用原缓存ID猜配。

后置：写历史地址原文及正式不可变地址快照；仅获准首次当前记录建立，不接管现有当前安装；地址API失败整次回滚。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

原安装Owner和首次迁入/跨批归集；需要正式设备及范围。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| closeCurrentProject | update | 是 | [DeviceAssignmentMapper.xml:52](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:52) |
| updateDeviceProjectIfMatch | update | 否 | [DeviceAssignmentMapper.xml:100](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:100) |
| closeCurrentCustomer | update | 是 | [DeviceAssignmentMapper.xml:141](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:141) |
| updateDeviceCustomerIfMatch | update | 否 | [DeviceAssignmentMapper.xml:189](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:189) |
| closeCurrent | update | 否 | [DeviceLocationMapper.xml:7](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/location/DeviceLocationMapper.xml:7) |
| carryHistoricalRecord | update | 否 | [InstallationMapper.xml:11](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-engineering/src/main/resources/mapper/installation/InstallationMapper.xml:11) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectInstallationMapper，范围发布前置处失败。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

新P16范围；地址与设备选择公共API保留；当前缺新P16正式范围，不伪造预览地址ID。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### createParameters

[P33InstallationSyncTask.java:22](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:22)

```java
@Override protected Parameters createParameters(SyncContext<?> c){return new Parameters();}
```

### work

[P33InstallationSyncTask.java:25](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:25)

```java
@Override public void work(SyncContext<Parameters> c){if(c.preview())c.runState().warning("SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED");c.parameters().publication=publications.load(new BaseSyncPublishedProjects.Query(c.tenantId(),c.connectionId(),c.maxRows(),c.source(),c.preview()));syncData(c.forData("Installation",false,false));}
```

### syncDataInsertBefore

[P33InstallationSyncTask.java:28](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:28)

```java
@Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  var c=b.context();var selected=new LinkedHashMap<String,BaseSyncPublishedProjects.Member>();var sourceIds=new HashSet<Long>();
  for(var s:b.rows()){validate(s);if(!sourceIds.add(s.sourceId))fail("SOURCE_ID_DUPLICATE");var member=c.parameters().publication.members().get(s.originalProjectId.toString());if(member==null)fail("SOURCE_OUTSIDE_PUBLISHED_SCOPE");selected.put(member.sourceKey(),member);}
  var refs=new HashMap<String,BaseSyncProjectReferences.Identity>();var members=new ArrayList<>(selected.values());for(int i=0;i<members.size();i+=1000)refs.putAll(projects.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(c.tenantId(),c.actorId(),members.subList(i,Math.min(i+1000,members.size())),c.preview())));
  var serials=b.rows().stream().map(s->s.barcode).distinct().toList();var equipment=new HashMap<String,Device>();for(int i=0;i<serials.size();i+=500)for(var d:mapper.selectDevices(new DeviceQuery(c.tenantId(),serials.subList(i,Math.min(i+500,serials.size())),c.preview())))if(equipment.putIfAbsent(d.sn,d)!=null)fail("DEVICE_SERIAL_AMBIGUOUS");
  var byProject=new HashMap<Long,Set<Long>>();for(var s:b.rows()){var d=equipment.get(s.barcode);if(d==null)fail("FORMAL_DEVICE_MISSING:"+s.barcode);s.projectId=refs.get(s.originalProjectId.toString()).id();s.equipmentId=d.id;byProject.computeIfAbsent(s.projectId,k->new TreeSet<>()).add(d.id);}
  for(var entry:byProject.entrySet()){var ids=new ArrayList<>(entry.getValue());for(int i=0;i<ids.size();i+=500){var requested=ids.subList(i,Math.min(i+500,ids.size()));var selectedDevices=devices.validateSelection(new ProjectDeviceSelectionApi.SelectionQuery(c.tenantId(),c.actorId(),entry.getKey(),requested));if(selectedDevices==null||selectedDevices.size()!=requested.size()||!selectedDevices.stream().map(cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice::equipmentId).collect(java.util.stream.Collectors.toSet()).equals(Set.copyOf(requested))||selectedDevices.stream().anyMatch(d->!Objects.equals(equipment.get(d.sn()).id,d.equipmentId())))fail("DEVICE_NOT_IN_CURRENT_PROJECT_SELECTION");}}
  var current=new HashMap<Long,Row>();var currentByDevice=new HashMap<Long,Row>();var codes=new HashMap<Long,List<String>>();var projectIds=new ArrayList<>(byProject.keySet());var deviceIds=equipment.values().stream().map(d->d.id).distinct().toList();
  for(int i=0;i<Math.max(projectIds.size(),deviceIds.size());i+=500){var ps=i<projectIds.size()?projectIds.subList(i,Math.min(i+500,projectIds.size())):List.<Long>of();var ds=i<deviceIds.size()?deviceIds.subList(i,Math.min(i+500,deviceIds.size())):List.<Long>of();for(var row:mapper.selectExisting(new TargetQuery(c.tenantId(),ps,ds,c.maxRows()+1,c.preview()))){if(current.putIfAbsent(row.id,row)!=null)continue;if(current.size()>c.maxRows())fail("FORMAL_HISTORY_CAPACITY");codes.computeIfAbsent(row.projectId,k->new ArrayList<>()).add(row.code);if(row.currentEquipmentId!=null&&currentByDevice.putIfAbsent(row.equipmentId,row)!=null)fail("CURRENT_INSTALLATION_AMBIGUOUS");}}
  var bindings=new HashMap<String,SyncIdentityStore.Identity>();var keys=b.rows().stream().map(s->s.sourceId.toString()).toList();for(int i=0;i<keys.size();i+=500)bindings.putAll(c.identities().findByKeys(new SyncIdentityStore.Query("INSTALLATION_HISTORY",keys.subList(i,Math.min(i+500,keys.size())))));
  var latest=new HashMap<Long,Source>();for(var s:b.rows())if(!bindings.containsKey(s.sourceId.toString()))latest.merge(s.equipmentId,s,(a,d)->a.createTime.isBefore(d.createTime)?d:a);
  var ordered=new ArrayList<Source>();latest.values().stream().sorted(Comparator.comparing(s->s.sourceId)).forEach(ordered::add);for(var s:b.rows())if(latest.get(s.equipmentId)!=s)ordered.add(s);
  for(var s:ordered){var binding=bindings.get(s.sourceId.toString());s.action=SyncWriteAction.UNCHANGED;
   if(binding!=null){var old=current.get(binding.targetId());if(old==null||Boolean.TRUE.equals(old.deleted)||!Objects.equals(old.projectId,s.projectId)||!Objects.equals(old.equipmentId,s.equipmentId)||!binding.fingerprint().equals(fingerprint(old,s)))fail("EXISTING_INSTALLATION_PROTECTED");s.target=old;continue;}
   var previous=currentByDevice.get(s.equipmentId);var newest=latest.get(s.equipmentId);var end=previous==null?newest.createTime:previous.effectiveFrom;boolean isCurrent=previous==null&&newest==s;
   if(!isCurrent&&(end==null||!s.createTime.isBefore(end)))fail("EQUAL_OR_NEWER_TIME_REQUIRES_ADJUDICATION");
   if(current.values().stream().anyMatch(r->Objects.equals(r.projectId,s.projectId)&&Objects.equals(r.equipmentId,s.equipmentId)&&Objects.equals(r.createTime,s.createTime)))fail("ORIGINAL_INSTALLATION_CANNOT_ADOPT");
   var row=new Row();row.tenantId=c.tenantId();row.projectId=s.projectId;row.equipmentId=s.equipmentId;row.code=ProjectScopedCodes.next(refs.get(s.originalProjectId.toString()).projectCode(),"AZ",codes.computeIfAbsent(s.projectId,k->new ArrayList<>()));codes.get(s.projectId).add(row.code);checkTextLength(row.code,64);row.id=c.preview()?null:IdWorker.getId();row.installLocation=s.installAddress;row.locationResolutionStatus="UNRESOLVED";row.status=2;row.version=0;row.deleted=false;row.installTime=s.createTime;row.effectiveFrom=s.createTime;row.effectiveTo=isCurrent?null:end;row.creator=s.resolvedCreator;row.updater=s.resolvedUpdater;row.createTime=s.createTime;row.updateTime=s.updateTime==null?s.createTime:s.updateTime;row.currentEquipmentId=isCurrent?row.equipmentId:null;s.target=row;s.newCurrent=isCurrent;s.action=SyncWriteAction.INSERT;
  }
  b.rows().clear();b.rows().addAll(ordered);
 }
```

### syncDataInsertAfter

[P33InstallationSyncTask.java:59](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:59)

```java
@Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){var saves=b.rows().stream().filter(s->s.action==SyncWriteAction.INSERT).map(s->new SyncIdentityStore.Save("INSTALLATION_HISTORY",s.sourceId.toString(),s.target.id,fingerprint(s.target,s),null)).toList();for(int i=0;i<saves.size();i+=500)b.context().identities().saveAll(saves.subList(i,Math.min(i+500,saves.size())));}
```

