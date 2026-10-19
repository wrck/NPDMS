# P12 前后置处理

[返回本任务](README.md)

前置：补充产品目录按编码；同码CRM名称权威，来源原值和空值语义保持。

后置：按原规则补充目录，不为DPPMS名字另造标签/扩展；不接管其他来源产品。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

原ProductCatalog Owner路径；无已取消的标签/扩展依赖。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectProductMapper及独立任务；复用编码和CRM同码权威。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

单表读取34976条；原itemname→model，describe_→name/desc；已有CRM同码名称保留。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### work

[P12SupplementProductSyncTask.java:20](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P12SupplementProductSyncTask.java:20)

```java
public void work(SyncContext<Void> context) { syncData(context.forData("SupplementProduct",false,false)); }
```

### syncDataValidate

[P12SupplementProductSyncTask.java:22](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P12SupplementProductSyncTask.java:22)

```java
protected void syncDataValidate(SyncBatch<ProductSyncRow,Void> snapshot) {
        var grouped=new LinkedHashMap<String,ProductSyncRow>();
        for(var row:snapshot.rows()) {
            String code=row.getProductCode();
            if(code==null || code.isBlank()) throw new IllegalArgumentException("DPPMS产品编码缺失");
            row.setProductCode(code.trim());
            var previous=grouped.putIfAbsent(row.getProductCode(),row);
            if(previous!=null && (!Objects.equals(previous.getProductName(),row.getProductName())
                    || !Objects.equals(previous.getProductModel(),row.getProductModel())))
                previous.setLabelConflict(true);
        }
        snapshot.rows().clear();
        snapshot.rows().addAll(grouped.values());
    }
```

### syncDataInsertBefore

[P12SupplementProductSyncTask.java:36](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P12SupplementProductSyncTask.java:36)

```java
protected void syncDataInsertBefore(SyncBatch<ProductSyncRow,Void> batch) {
        var context=batch.context();
        var productCodes=batch.rows().stream().map(ProductSyncRow::getProductCode).toList();
        var existing=new java.util.ArrayList<ProductSyncRow>();
        for(int offset=0;offset<productCodes.size();offset+=context.batchSize())
            existing.addAll(products.selectSupplementTargets(new TargetMapper.ProductCodesQuery(context.tenantId(),
                    productCodes.subList(offset,Math.min(offset+context.batchSize(),productCodes.size())),context.preview())));
        var codes=new HashSet<String>();
        for(var old:existing) if(!codes.add(old.getProductCode()) || Boolean.TRUE.equals(old.getDeleted())
                || old.getTenantId()!=context.tenantId()) throw new IllegalStateException("已有产品身份冲突");
        for(int index=0;index<batch.rows().size();index++) {
            var source=batch.rows().get(index); source.setSyncAction(SyncWriteAction.UNCHANGED);
            if(codes.contains(source.getProductCode())) continue;
            if(source.isLabelConflict()) throw new IllegalArgumentException("同一DPPMS产品编码有冲突标签");
            checkTextLength(source.getProductCode(),100);
            checkTextLength(source.getProductName(),500);
            checkTextLength(source.getProductModel(),128);
            var row=source.toBuilder().id(context.preview()?null:IdWorker.getId()).tenantId(context.tenantId())
                    .status("ACTIVE").sourceSystem("DPPMS").sourceKey(source.getProductCode()).version(0L).deleted(false)
                    .creator(Long.toString(context.actorId())).updater(Long.toString(context.actorId()))
                    .createTime(context.startedAt()).updateTime(context.startedAt()).syncedAt(context.startedAt()).build();
            row.setSyncAction(SyncWriteAction.INSERT); batch.rows().set(index,row);
        }
    }
```

