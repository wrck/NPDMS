# P21 前后置处理

[返回本任务](README.md)

前置：两张原租赁/配置缓存原字段；project_id按已批准可空契约，不得伪造正式项目。

后置：两份缓存按租户全量替换；预览不清空；同次失败回滚此前删除/插入；不转成正式项目产品关系。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

既定TABLE_MAPPING FULL_CACHE_REPLACE。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`FULL_CACHE_REPLACE`，missingPolicy=`RETAIN`，clearBeforeLoad=`True`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| clearCurrent | delete | 否 | [SyncDisplayCacheMapper.xml:4](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/SyncDisplayCacheMapper.xml:4) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

仍GenericSyncEngine独立缓存任务；历史1190后失败，结构project_id非空。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`FULL_CACHE_REPLACE`，missingPolicy=`RETAIN`，clearBeforeLoad=`True`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

两次单表查询、按批写。新P21预览登记执行结构缺项后继续完整校验；实际执行仍在任何清理前拒绝未就绪结构。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| deleteLease | delete | 否 | [P21TargetMapper.xml:5](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P21TargetMapper.xml:5) |
| deleteConfiguration | delete | 否 | [P21TargetMapper.xml:10](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P21TargetMapper.xml:10) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### work

[P21ProjectCacheSyncTask.java:20](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P21ProjectCacheSyncTask.java:20)

```java
public void work(SyncContext<Void> context) { syncData(context.forData("ProjectCache",true,false)); }
```

### syncDataBefore

[P21ProjectCacheSyncTask.java:21](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P21ProjectCacheSyncTask.java:21)

```java
protected void syncDataBefore(SyncContext<Void> context) {
        super.syncDataBefore(context);
        if(mapper.nullableColumns()!=2) {
            if(!context.preview()) throw new IllegalStateException("P21：已审核可空列尚未就绪；禁止补造关联ID或原时间");
            context.runState().warning("P21_EXECUTION_SCHEMA_REQUIRED:project_id_nullable");
        }
    }
```

### syncDataValidate

[P21ProjectCacheSyncTask.java:34](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P21ProjectCacheSyncTask.java:37)

```java
protected void syncDataValidate(SyncBatch<ProjectCacheSource,Void> batch) {
        for(var source:batch.rows()) {
            if(source instanceof LeaseCacheSource value) {
                var row=new LeaseCacheRow();
                row.setProjectCode(value.getProjectCode());
                checkTextCapacity(row.getProjectCode());
                row.setExecutionNo(value.getExecutionNo());
                checkTextCapacity(row.getExecutionNo());
                row.setProductCategory(value.getProductCategory());
                checkTextCapacity(row.getProductCategory());
                row.setProductType(value.getProductType());
                checkTextCapacity(row.getProductType());
                row.setProductCode(value.getProductCode());
                checkTextCapacity(row.getProductCode());
                row.setProductModel(value.getProductModel());
                checkTextCapacity(row.getProductModel());
                row.setProductName(value.getProductName());
                checkTextCapacity(row.getProductName());
                row.setQuantity(exactDecimal(value.getQuantity()==null?null:new java.math.BigDecimal(value.getQuantity()),24,6));
                row.setMemo(value.getMemo());
                checkTextCapacity(row.getMemo());
                row.setLeaseDuration(exactDecimal(value.getLeaseDuration()==null?null:new java.math.BigDecimal(value.getLeaseDuration()),16,2));
                row.setDataSource(value.getDataSource());
                checkTextLength(row.getDataSource(),25);
                value.setTarget(row);
            }
            if(source instanceof ConfigurationCacheSource value) {
                var row=new ConfigurationCacheRow();
                row.setProjectCode(value.getProjectCode());
                checkTextCapacity(row.getProjectCode());
                row.setExecutionNo(value.getExecutionNo());
                checkTextCapacity(row.getExecutionNo());
                row.setItemGroup(integer(value.getItemGroup()));
                row.setProductCode(value.getProductCode());
                checkTextCapacity(row.getProductCode());
                row.setParentCode(value.getParentCode());
                checkTextCapacity(row.getParentCode());
                row.setQuantity(exactDecimal(value.getQuantity()==null?null:new java.math.BigDecimal(value.getQuantity()),24,6));
                row.setBomPath(value.getBomPath());
                checkTextCapacity(row.getBomPath());
                row.setProductModel(value.getProductModel());
                checkTextCapacity(row.getProductModel());
                row.setProductDescription(value.getProductDescription());
                checkTextCapacity(row.getProductDescription());
                row.setLevel(integer(value.getLevel()));
                row.setDataSource(value.getDataSource());
                checkTextLength(row.getDataSource(),25);
                value.setTarget(row);
            }
        }
    }
```

### syncDataInsertBefore

[P21ProjectCacheSyncTask.java:85](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P21ProjectCacheSyncTask.java:88)

```java
protected void syncDataInsertBefore(SyncBatch<ProjectCacheSource,Void> batch) {
        var context=batch.context();
        for(var source:batch.rows()) {
            if(source instanceof LeaseCacheSource value) {
                var row=value.getTarget();
                row.setId(context.preview()?null:IdWorker.getId()); row.setTenantId(context.tenantId()); row.setDeleted(false);
                row.setCreator(Long.toString(context.actorId())); row.setUpdater(Long.toString(context.actorId()));
                row.setCreateTime(context.startedAt()); row.setUpdateTime(context.startedAt());
            }
            if(source instanceof ConfigurationCacheSource value) {
                var row=value.getTarget();
                row.setId(context.preview()?null:IdWorker.getId()); row.setTenantId(context.tenantId()); row.setDeleted(false);
                row.setCreator(Long.toString(context.actorId())); row.setUpdater(Long.toString(context.actorId()));
                row.setCreateTime(context.startedAt()); row.setUpdateTime(context.startedAt());
            }
        }
    }
```

