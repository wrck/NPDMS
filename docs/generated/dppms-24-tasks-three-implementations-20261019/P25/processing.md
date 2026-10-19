# P25 前后置处理

[返回本任务](README.md)

前置：逐次续保历史独立于当前设备保修；保留10原字段、NULL/0、原起止日期与最新标志；年转参考月。

后置：保存ast_device_warranty_renewal_record及来源指纹；不按日期推断当前在保；无必要不查设备ID。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

当前原线单表读fb_service，再由AST/COM从本地发货RMA/父合同类型判资格；未真实全量/性能验收这份新路径。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateRecord | update | 否 | [DeviceWarrantyRenewalRecordMapper.xml:16](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyRenewalRecordMapper.xml:16) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

独立直写仍按批准来源范围和id+SN；旧预览453443，用时347.893秒。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateRecord | update | 否 | [DirectWarrantyMapper.xml:16](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectWarrantyMapper.xml:16) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

整表462213行，无设备/项目/发货资格依赖，无排序，源重复身份预查一次；预览约15秒；不回填设备ID。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateWarranty | update | 否 | [P25TargetMapper.xml:13](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P25TargetMapper.xml:13) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### work

[P25WarrantySyncTask.java:23](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P25WarrantySyncTask.java:23)

```java
public void work(SyncContext<Void> context) { syncData(context.forData("Warranty",false,true)); }
```

### syncDataInsertBefore

[P25WarrantySyncTask.java:38](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P25WarrantySyncTask.java:38)

```java
protected void syncDataInsertBefore(SyncBatch<WarrantySyncSource,Void> batch) {
        var context=batch.context();
        var byKey=context.identities().findByKeys(new SyncIdentityStore.Query("WARRANTY",batch.rows().stream().map(WarrantySyncSource::getSourceKey).toList()));
        var identities=byKey.values();
        var targets=identities.isEmpty()?List.<WarrantySyncRow>of():records.selectExisting(new TargetMapper.ExistingQuery(context.tenantId(),identities.stream().map(SyncIdentityStore.Identity::targetId).toList(),context.preview()));
        var byId=new HashMap<Long,WarrantySyncRow>();
        for(var target:targets) if(byId.put(target.getId(),target)!=null) throw new IllegalStateException("续保目标身份重复");
        for(var source:batch.rows()) {
            source.setSyncAction(SyncWriteAction.UNCHANGED);
            var row=convert(source);
            var identity=byKey.get(source.getSourceKey());
            var old=identity==null?null:byId.get(identity.targetId());
            if(identity!=null && (old==null || Boolean.TRUE.equals(old.getDeleted()) || old.getTenantId()!=context.tenantId()
                    || old.getVersion()==null || !Objects.equals(identity.fingerprint(),fingerprint(old))))
                throw new IllegalStateException("续保目标或来源基线已变化，禁止覆盖");
            if(old!=null && business(old).equals(business(row))) { continue; }
            row.setId(old==null?(context.preview()?null:IdWorker.getId()):old.getId()); row.setTenantId(context.tenantId());
            row.setCreator(old==null?Long.toString(context.actorId()):old.getCreator()); row.setCreateTime(old==null?context.startedAt():old.getCreateTime());
            row.setUpdater(Long.toString(context.actorId())); row.setUpdateTime(context.startedAt()); row.setDeleted(false);
            row.setVersion(old==null?0:Math.addExact(old.getVersion(),1));
            source.setTarget(row); source.setExpectedVersion(old==null?null:old.getVersion());
            source.setPreviousFingerprint(identity==null?null:identity.fingerprint());
            source.setSyncAction(old==null?SyncWriteAction.INSERT:SyncWriteAction.UPDATE);
        }
    }
```

### syncDataInsertAfter

[P25WarrantySyncTask.java:70](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P25WarrantySyncTask.java:70)

```java
protected void syncDataInsertAfter(SyncBatch<WarrantySyncSource,Void> batch) {
        batch.context().identities().saveAll(batch.rows().stream()
                .filter(row->row.getSyncAction()!=SyncWriteAction.UNCHANGED).map(this::identity).toList());
    }
```

