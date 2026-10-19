# P11 前后置处理

[返回本任务](README.md)

前置：SQL Server正式视图；空键、字符串容量、原精度及既定来源保护。

后置：写正式产品；不修改人工document_id；不产生产品标签/扩展副本。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

TABLE_MAPPING原PRODUCT；较早SQL按同码窗口选一行，历史读取8441。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| generic_update_PRODUCT | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

仍是GenericSyncEngine，独立P11_DIRECT身份，不是第二套专用SQL写入器。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| generic_update_PRODUCT | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

LTRIM/RTRIM仅在源SQL，保留8443原行含2个重复编码；每批INSERT ON DUPLICATE KEY UPDATE，version递增，预览动作计数0。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### work

[P11CrmProductSyncTask.java:18](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P11CrmProductSyncTask.java:18)

```java
public void work(SyncContext<Void> context) { syncData(context.forData("CrmProduct",false,true)); }
```

### syncDataInsertBefore

[P11CrmProductSyncTask.java:20](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P11CrmProductSyncTask.java:20)

```java
protected void syncDataInsertBefore(SyncBatch<ProductSyncRow,Void> batch) {
        for(var row:batch.rows()) {
            if(row.getCrmStateCode()==null || !Set.of(0,1).contains(row.getCrmStateCode()))
                throw new IllegalArgumentException("CRM产品状态编码未定义");
            validate(row);
            row.setSourceVersion(row.getSourceUpdatedAt()==null?null:row.getSourceUpdatedAt().toString());
            if(!batch.context().preview()) row.setId(IdWorker.getId());
        }
    }
```

