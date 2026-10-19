# P10 前后置处理

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。ERP列已按单列授权补齐；源59601行中22组客户编码对应44个不同账号且名称不同，另35行缺名称、4行名称超长。需CRM来源Owner纠正或给出已审核的无损承载方案，不能擅自合并、改码或跳过。

[返回本任务](README.md)

前置：CRM字段及ERP客户码映射、租户、原版本和外部身份核验；SQL Server查询与目标MySQL分开。

后置：客户主档和外部映射同事务写入；旧来源、软删除、多义身份和人工维护保护。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

CRM客户Owner入口；按已批准CRM权威定义处理。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updatePlatformFieldsByVersion | update | 否 | [CustomerMasterMapper.xml:9](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-customer/src/main/resources/mapper/customer/CustomerMasterMapper.xml:9) |
| updateLifecycleByVersion | update | 否 | [CustomerMasterMapper.xml:53](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-customer/src/main/resources/mapper/customer/CustomerMasterMapper.xml:53) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

CrmDirectCustomerTask复用解释器并替换DirectCustomerPersistence。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateCustomer | update | 否 | [DirectCustomerMapper.xml:22](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectCustomerMapper.xml:22) |
| updateMapping | update | 否 | [DirectCustomerMapper.xml:48](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectCustomerMapper.xml:48) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

先校验cus_customer_master.erp_customer_code；当前结构缺失，在读源前FAILED。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateCustomer | update | 否 | [P10TargetMapper.xml:9](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P10TargetMapper.xml:9) |
| updateMapping | update | 否 | [P10TargetMapper.xml:10](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P10TargetMapper.xml:10) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### work

[P10CrmCustomerSyncTask.java:23](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P10CrmCustomerSyncTask.java:23)

```java
public void work(SyncContext<Void> context){syncData(context.forData("Customer",false,true));}
```

### syncDataBefore

[P10CrmCustomerSyncTask.java:24](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P10CrmCustomerSyncTask.java:24)

```java
protected void syncDataBefore(SyncContext<Void> context){
        super.syncDataBefore(context);
        if(customers.erpColumnReady()!=1)throw new IllegalStateException("P10目标缺少已审核列cus_customer_master.erp_customer_code；不执行迁移");
    }
```

### syncDataInsertBefore

[P10CrmCustomerSyncTask.java:28](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P10CrmCustomerSyncTask.java:28)

```java
protected void syncDataInsertBefore(SyncBatch<CustomerSource,Void> batch){
        var context=batch.context();
        var keys=batch.rows().stream().map(CustomerSource::getSourceKey).toList();
        var codes=batch.rows().stream().map(CustomerSource::getCode).toList();
        var bindings=context.identities().findByKeys(new SyncIdentityStore.Query("CUSTOMER",keys));
        var mappings=new HashMap<String,CustomerMapping>();
        for(var row:customers.selectMappings(new TargetMapper.SourceKeysQuery(context.tenantId(),keys,context.preview())))
            if(!keys.contains(row.getSourceKey()) || mappings.putIfAbsent(row.getSourceKey(),row)!=null)
                throw new IllegalStateException("CRM客户来源映射不唯一或原身份不同");
        var ids=mappings.values().stream().map(CustomerMapping::getCustomerId).toList();
        var targets=customers.selectCustomers(new TargetMapper.CustomersQuery(context.tenantId(),ids,codes,context.preview()));
        var byId=new HashMap<Long,CustomerRow>();var byCode=new HashMap<String,List<CustomerRow>>();
        for(var old:targets){
            if(old.getTenantId()!=context.tenantId() || byId.putIfAbsent(old.getId(),old)!=null)throw new IllegalStateException("CRM客户目标身份重复或跨租户");
            byCode.computeIfAbsent(old.getCode().toLowerCase(Locale.ROOT),ignored->new ArrayList<>()).add(old);
        }
        for(var source:batch.rows()){
            validate(source);source.setSyncAction(SyncWriteAction.UNCHANGED);
            var mapping=mappings.get(source.getSourceKey());var binding=bindings.get(source.getSourceKey());
            var old=mapping==null?null:byId.get(mapping.getCustomerId());
            if(mapping!=null && (old==null || binding==null || binding.targetId()!=old.getId() || Boolean.TRUE.equals(old.getDeleted())
                    || !"CRM_SYNC".equals(old.getSourceType()) || !Objects.equals(old.getSourceKey(),source.getSourceKey())
                    || !Objects.equals(binding.fingerprint(),fingerprint(old,mapping))))
                throw new IllegalStateException("CRM客户正式映射或独立来源基线缺失、变化；禁止接管");
            if(mapping==null && binding!=null)throw new IllegalStateException("CRM客户独立绑定的正式映射缺失");
            var occupied=byCode.getOrDefault(source.getCode().toLowerCase(Locale.ROOT),List.of());
            if(occupied.stream().anyMatch(row->old==null || !Objects.equals(row.getId(),old.getId())))
                throw new IllegalStateException("客户编码已占用；禁止接管既有客户");
            int version=old==null?1:source.getDataAsOf().compareTo(LocalDateTime.parse(mapping.getSourceVersion().replace(' ','T')));
            if(version<0)continue;
            var row=new CustomerRow();if(old!=null)org.springframework.beans.BeanUtils.copyProperties(old,row);
            copySource(source,row);
            if(old!=null && version==0){
                if(!business(old).equals(business(row)))throw new IllegalStateException("CRM客户同版本载荷冲突");
                continue;
            }
            row.setId(old==null?(context.preview()?null:IdWorker.getId()):old.getId());row.setTenantId(context.tenantId());
            row.setSourceType("CRM_SYNC");row.setSyncStatus("SYNCED");row.setReconciliationPending(false);row.setDeleted(false);
            row.setVersion(old==null?0L:Math.addExact(old.getVersion(),1));
            row.setCreator(old==null?Long.toString(context.actorId()):old.getCreator());row.setCreateTime(old==null?context.startedAt().withNano(0):old.getCreateTime());
            row.setUpdater(Long.toString(context.actorId()));row.setUpdateTime(context.startedAt().withNano(0));
            var next=new CustomerMapping();if(mapping!=null)org.springframework.beans.BeanUtils.copyProperties(mapping,next);
            next.setId(mapping==null?(context.preview()?null:IdWorker.getId()):mapping.getId());next.setTenantId(context.tenantId());next.setCustomerId(row.getId());
            next.setSourceSystem("CRM");next.setSourceKey(source.getSourceKey());next.setSourceVersion(source.getSourceVersion());
            if(mapping==null){next.setEffectiveFrom(source.getDataAsOf());next.setCreator(row.getCreator());next.setCreateTime(row.getCreateTime());}
            next.setUpdater(row.getUpdater());next.setUpdateTime(row.getUpdateTime());next.setDeleted(false);
            source.setTarget(row);source.setMapping(next);source.setExpectedVersion(old==null?null:old.getVersion());
            source.setExpectedSourceVersion(mapping==null?null:mapping.getSourceVersion());source.setPreviousFingerprint(binding==null?null:binding.fingerprint());
            source.setSyncAction(old==null?SyncWriteAction.INSERT:SyncWriteAction.UPDATE);
        }
    }
```

### syncDataInsertAfter

[P10CrmCustomerSyncTask.java:87](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P10CrmCustomerSyncTask.java:87)

```java
protected void syncDataInsertAfter(SyncBatch<CustomerSource,Void> batch){
        batch.context().identities().saveAll(batch.rows().stream().filter(row->row.getSyncAction()!=SyncWriteAction.UNCHANGED)
                .map(row->new SyncIdentityStore.Save("CUSTOMER",row.getSourceKey(),row.getTarget().getId(),fingerprint(row.getTarget(),row.getMapping()),row.getPreviousFingerprint())).toList());
    }
```

