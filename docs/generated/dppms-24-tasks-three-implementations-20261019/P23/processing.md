# P23 前后置处理

[返回本任务](README.md)

前置：所有原字段、重复行、原NULL时间保持；不强制关联正式合同ID。

后置：按租户完整替换缓存，保留各原行；预览不DELETE/INSERT；失败整次回滚。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

当前候选已纠正sourceKey=id，仍未保存/重跑；不能沿旧cacheRowOrdinal失败算新候选失败。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`FULL_CACHE_REPLACE`，missingPolicy=`RETAIN`，clearBeforeLoad=`True`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

独立TABLE_MAPPING contract-plan-cache，35501条预览通过。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`FULL_CACHE_REPLACE`，missingPolicy=`RETAIN`，clearBeforeLoad=`True`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

默认清理/流式批写，当前实际源时间非空，35501条预览通过；未真正刷新缓存。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| deleteCollectionPlan | delete | 否 | [P23TargetMapper.xml:5](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P23TargetMapper.xml:5) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### work

[P23CollectionPlanSyncTask.java:20](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P23CollectionPlanSyncTask.java:20)

```java
public void work(SyncContext<Parameters> context) { syncData(context.forData("CollectionPlan",true,false)); }
```

### createParameters

[P23CollectionPlanSyncTask.java:22](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P23CollectionPlanSyncTask.java:22)

```java
protected Parameters createParameters(SyncContext<?> runtime) {
        return new Parameters(Set.copyOf(mapper.nullableColumns()));
    }
```

### syncDataValidate

[P23CollectionPlanSyncTask.java:25](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P23CollectionPlanSyncTask.java:25)

```java
protected void syncDataValidate(SyncBatch<CollectionPlanCacheSource,Parameters> batch) {
        for(var source:batch.rows()) {
            { var value=source;
                var row=new CollectionPlanCacheRow();
                row.setContractNo(value.getContractNo());
                checkTextLength(row.getContractNo(),64);
                row.setBatchCode(value.getBatchCode());
                checkTextLength(row.getBatchCode(),32);
                row.setCollectionItemName(value.getCollectionItemName());
                checkTextLength(row.getCollectionItemName(),64);
                row.setReferenceEventName(value.getReferenceEventName());
                checkTextLength(row.getReferenceEventName(),64);
                row.setEventPlannedAt(dateTime(value.getEventPlannedAt()));
                row.setAfterDays(integer(value.getAfterDays()));
                row.setEventActualFinishedAt(dateTime(value.getEventActualFinishedAt()));
                row.setMarketingFeedback(value.getMarketingFeedback());
                checkTextLength(row.getMarketingFeedback(),2000);
                row.setCreator(value.getCreator());
                checkTextLength(row.getCreator(),64);
                row.setCreateTime(dateTime(value.getCreateTime()));
                row.setUpdater(value.getUpdater());
                checkTextLength(row.getUpdater(),64);
                row.setUpdateTime(dateTime(value.getUpdateTime()));
                row.setEffectiveFrom(dateTime(value.getEffectiveFrom()));
                row.setEffectiveTo(dateTime(value.getEffectiveTo()));
                if((row.getCreateTime()==null && !batch.context().parameters().nullableColumns().contains("create_time"))
                        || (row.getUpdateTime()==null && !batch.context().parameters().nullableColumns().contains("update_time")))
                    throw new IllegalStateException("P23：原时间缺失且目标列不允许空值；禁止补造时间");
                row.setDataSource(value.getDataSource());
                checkTextLength(row.getDataSource(),25);
                value.setTarget(row);
            }
        }
    }
```

### syncDataInsertBefore

[P23CollectionPlanSyncTask.java:59](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P23CollectionPlanSyncTask.java:59)

```java
protected void syncDataInsertBefore(SyncBatch<CollectionPlanCacheSource,Parameters> batch) {
        var context=batch.context();
        for(var source:batch.rows()) {
            { var value=source;
                var row=value.getTarget();
                row.setId(context.preview()?null:IdWorker.getId()); row.setTenantId(context.tenantId()); row.setDeleted(false);
            }
        }
    }
```

