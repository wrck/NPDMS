# P34 前后置处理

[返回本任务](README.md)

前置：原五字段完整，SN空值按正式目标结构核验；原重复行保留，不按最新值覆盖历史。

后置：追加正式出厂版本历史，来源指纹保护；不推导当前已安装/已升级事实。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

原FactoryHistory Owner；当时原SN列可空契约前置未落地，当前候选未跑。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectFactoryHistoryMapper；165801条真实预览，实际SN均非NULL。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

按原五值排序，以相邻重复出现序号保留165801条；仅在实际存在NULL时核验目标可空。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### work

[P34FactoryHistorySyncTask.java:22](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P34FactoryHistorySyncTask.java:22)

```java
public void work(SyncContext<Parameters> context) { syncData(context.forData("FactoryHistory",false,true)); }
```

### createParameters

[P34FactoryHistorySyncTask.java:24](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P34FactoryHistorySyncTask.java:24)

```java
protected Parameters createParameters(SyncContext<?> runtime) {
        return new Parameters(records.nullableSerialColumn()==1);
    }
```

### syncDataInsertBefore

[P34FactoryHistorySyncTask.java:41](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P34FactoryHistorySyncTask.java:41)

```java
protected void syncDataInsertBefore(SyncBatch<FactorySyncSource,Parameters> batch) {
        var context=batch.context();
        var byKey=context.identities().findByKeys(new SyncIdentityStore.Query("FACTORY_HISTORY",batch.rows().stream().map(FactorySyncSource::getSourceKey).toList()));
        var identities=byKey.values();
        var targets=identities.isEmpty()?List.<FactorySyncRow>of():records.selectExisting(new TargetMapper.ExistingQuery(context.tenantId(),identities.stream().map(SyncIdentityStore.Identity::targetId).toList(),context.preview()));
        var byId=new HashMap<Long,FactorySyncRow>();
        for(var row:targets) if(byId.put(row.getId(),row)!=null) throw new IllegalStateException("出厂目标身份重复");
        for(var source:batch.rows()) {
            if(source.getDeviceSn()==null && !context.parameters().nullableSerial())
                throw new IllegalStateException("P34原SN缺失且目标列不允许空值；禁止填占位或跳过");
            source.setSyncAction(SyncWriteAction.UNCHANGED);
            var identity=byKey.get(source.getSourceKey());
            if(identity!=null) {
                var old=byId.get(identity.targetId());
                if(old==null || Boolean.TRUE.equals(old.getDeleted()) || old.getTenantId()!=context.tenantId()
                        || old.getProjectId()!=null || !Objects.equals(identity.fingerprint(),fingerprint(old))
                        || !Arrays.asList(source.getDeviceSn(),source.getConpVersion(),source.getCpldVersion(),source.getBootVersion(),source.getPcbVersion())
                        .equals(Arrays.asList(old.getDeviceSn(),old.getConpVersion(),old.getCpldVersion(),old.getBootVersion(),old.getPcbVersion())))
                    throw new IllegalStateException("出厂不可变历史或来源绑定已变化");
                continue;
            }
            var row=FactorySyncRow.builder().id(context.preview()?null:IdWorker.getId()).tenantId(context.tenantId())
                    .deviceSn(source.getDeviceSn()).conpVersion(source.getConpVersion()).cpldVersion(source.getCpldVersion())
                    .bootVersion(source.getBootVersion()).pcbVersion(source.getPcbVersion()).projectId(null)
                    .creator(Long.toString(context.actorId())).updater(Long.toString(context.actorId()))
                    .createTime(context.startedAt()).updateTime(context.startedAt()).deleted(false).build();
            source.setTarget(row); source.setSyncAction(SyncWriteAction.INSERT);
        }
    }
```

### syncDataInsertAfter

[P34FactoryHistorySyncTask.java:76](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P34FactoryHistorySyncTask.java:76)

```java
protected void syncDataInsertAfter(SyncBatch<FactorySyncSource,Parameters> batch) {
        batch.context().identities().saveAll(batch.rows().stream()
                .filter(row->row.getSyncAction()!=SyncWriteAction.UNCHANGED).map(this::identity).toList());
    }
```

