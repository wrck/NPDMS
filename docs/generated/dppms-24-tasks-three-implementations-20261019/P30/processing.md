# P30 前后置处理

[返回本任务](README.md)

前置：按SN核实唯一最新正式发货及产品来源；项目归属必须另具正式选择范围和批准输入。

后置：基础设备建档；A有显式SN指派入口须预检及P33/P16前置，不能把发货成功当指派成功。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

DeviceBootstrap/Assignment离线入口；没有本候选真实全量运行。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| closeCurrentProject | update | 是 | [DeviceAssignmentMapper.xml:52](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:52) |
| updateDeviceProjectIfMatch | update | 否 | [DeviceAssignmentMapper.xml:100](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:100) |
| closeCurrentCustomer | update | 是 | [DeviceAssignmentMapper.xml:141](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:141) |
| updateDeviceCustomerIfMatch | update | 否 | [DeviceAssignmentMapper.xml:189](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:189) |
| updateOrganizationBatch | update | 否 | [DeviceOrganizationMapper.xml:30](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/device/DeviceOrganizationMapper.xml:30) |
| updateOrganizationRows | update | 否 | [DeviceOrganizationMapper.xml:40](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/device/DeviceOrganizationMapper.xml:40) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectDeviceBaseMapper，PREVIEW_READY/read0；正式发货来源为0。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

forLocalData读取正式发货，每轮产品目录一次；当前0行并登记缺正式发货，不算业务完成。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### createParameters

[P30DeviceBaseSyncTask.java:17](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P30DeviceBaseSyncTask.java:17)

```java
protected Parameters createParameters(SyncContext<?> runtime){return new Parameters();}
```

### work

[P30DeviceBaseSyncTask.java:20](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P30DeviceBaseSyncTask.java:20)

```java
public void work(SyncContext<Parameters> context){
        syncData(context.forLocalData("DeviceBase",false,true));
        if(context.runState().counts().read()==0)context.runState().warning("P30_FORMAL_DPPMS_SHIPMENTS_MISSING:0");
    }
```

### syncDataInsertBefore

[P30DeviceBaseSyncTask.java:24](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P30DeviceBaseSyncTask.java:24)

```java
protected void syncDataInsertBefore(SyncBatch<DeviceSource,Parameters> batch){
        var context=batch.context();var serials=batch.rows().stream().map(DeviceSource::getDeviceSn).toList();
        var query=new TargetMapper.SerialsQuery(context.tenantId(),serials,context.preview());
        var shipments=devices.selectLatestShipments(query);var latest=new HashMap<String,Shipment>();
        for(var shipment:shipments){
            if(!serials.contains(shipment.getDeviceSn()) || shipment.getTenantId()!=context.tenantId() || !"DPPMS".equals(shipment.getSourceSystem())
                    || latest.putIfAbsent(shipment.getDeviceSn(),shipment)!=null)throw new IllegalStateException("P30正式最新发货来源、租户或SN身份冲突");
        }
        if(!latest.keySet().equals(new HashSet<>(serials)))throw new IllegalStateException("P30正式发货事实缺失");
        if(context.parameters().products==null){
            context.parameters().products=new HashMap<>();
            for(var product:devices.selectProducts(new TargetMapper.ProductCatalogueQuery(context.tenantId())))
                context.parameters().products.computeIfAbsent(product.productCode(),ignored->new ArrayList<>()).add(product);
        }
        var bindings=context.identities().findByKeys(new SyncIdentityStore.Query("DEVICE_BASE",serials));
        var existing=new HashMap<String,DeviceRow>();for(var old:devices.selectExisting(query))
            if(old.getTenantId()!=context.tenantId() || !serials.contains(old.getSn()) || existing.putIfAbsent(old.getSn(),old)!=null)
                throw new IllegalStateException("P30正式设备SN身份多义、跨租户或原文不同");
        for(var source:batch.rows()){
            if(source.getDeviceSn()==null || source.getDeviceSn().isBlank())throw new IllegalArgumentException("P30正式发货SN缺失");
            checkTextLength(source.getDeviceSn(),100);
            var shipment=latest.get(source.getDeviceSn());
            var matches=context.parameters().products.getOrDefault(shipment.getProductCode(),List.of());
            if(matches.size()>1)throw new IllegalStateException("P30产品正式编码身份多义");
            var product=matches.isEmpty()?null:matches.getFirst();
            var old=existing.get(source.getDeviceSn());var binding=bindings.get(source.getDeviceSn());
            source.setSyncAction(SyncWriteAction.UNCHANGED);
            if(old!=null){
                if(Boolean.TRUE.equals(old.getDeleted()) || !"DPPMS".equals(old.getSourceSystem())
                        || binding!=null && (binding.targetId()!=old.getId() || !Objects.equals(binding.fingerprint(),fingerprint(old))))
                    throw new IllegalStateException("P30设备正式来源或独立保护基线已变化");
                continue;
            }
            if(binding!=null)throw new IllegalStateException("P30独立设备绑定目标缺失");
            var row=new DeviceRow();row.setId(context.preview()?null:IdWorker.getId());row.setTenantId(context.tenantId());row.setSn(source.getDeviceSn());
            row.setProductCode(shipment.getProductCode());row.setProductName(product==null?null:product.productName());row.setName(row.getProductName());row.setProductModel(product==null?null:product.productModel());
            row.setShipmentRecordId(shipment.getId());row.setShipmentTime(shipment.getShipmentTime());row.setPackageNo(shipment.getPackageNo());row.setContractNo(shipment.getContractNo());
            row.setProjectAssignmentVersion(0L);row.setCustomerAssignmentVersion(0L);row.setStatus("IN_STOCK");row.setVersion(0L);
            row.setSourceSystem("DPPMS");row.setSourceKey("SN:"+row.getSn());row.setSourceVersion("BASE_P30:"+context.startedAt());row.setSyncedAt(context.startedAt());row.setSyncStatus("SUCCESS");row.setRemark("DPPMS设备基础承接");
            row.setCreator(Long.toString(context.actorId()));row.setUpdater(row.getCreator());row.setCreateTime(context.startedAt());row.setUpdateTime(context.startedAt());row.setDeleted(false);
            checkTextLength(row.getProductCode(),64);checkTextLength(row.getProductName(),128);checkTextLength(row.getProductModel(),128);checkTextLength(row.getPackageNo(),128);checkTextLength(row.getContractNo(),64);checkMillisecond(row.getShipmentTime());
            source.setTarget(row);source.setSyncAction(SyncWriteAction.INSERT);
        }
    }
```

### syncDataInsertAfter

[P30DeviceBaseSyncTask.java:70](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P30DeviceBaseSyncTask.java:70)

```java
protected void syncDataInsertAfter(SyncBatch<DeviceSource,Parameters> batch){
        batch.context().identities().saveAll(batch.rows().stream().filter(row->row.getSyncAction()==SyncWriteAction.INSERT)
                .map(row->new SyncIdentityStore.Save("DEVICE_BASE",row.getDeviceSn(),row.getTarget().getId(),fingerprint(row.getTarget()),null)).toList());
    }
```

