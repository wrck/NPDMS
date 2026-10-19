# P14 前后置处理

[返回本任务](README.md)

前置：来源/公司/订单类型映射、同业务键载荷冲突、源版本和父行身份校验；不任选冲突原行。

后置：订单→订单行，父order_id必须真实；可空客户/产品ID留空；禁止自动恢复软删除或写数量完成事实。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

当前候选TABLE_MAPPING；引用历史回执与当前新候选需分开。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| generic_update_ORDER | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |
| generic_update_LINE | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

同一GenericSyncEngine，独立P14_DIRECT身份；预览465804且ISSUE1102不是零问题通过。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| generic_update_ORDER | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |
| generic_update_LINE | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

独立表头/明细批写；169表头键和10明细键冲突即整体FAILED；保留同版本保护。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateOrder | update | 否 | [P14TargetMapper.xml:7](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P14TargetMapper.xml:7) |
| updateLine | update | 否 | [P14TargetMapper.xml:11](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P14TargetMapper.xml:11) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### createParameters

[P14SalesOrderSyncTask.java:19](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:19)

```java
protected Parameters createParameters(SyncContext<?> c){return new Parameters();}
```

### work

[P14SalesOrderSyncTask.java:22](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:22)

```java
public void work(SyncContext<Parameters> c){
  // Explicit full header snapshot: planned parent identities must remain available to line preview.
  syncData(c.forData("Order",false,false));syncData(c.forData("Line",false,true));
 }
```

### syncDataInsertBefore

[P14SalesOrderSyncTask.java:26](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:26)

```java
protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  if("Order".equals(b.context().dataName()))prepareOrders(b);else prepareLines(b);
 }
```

### syncDataInsertAfter

[P14SalesOrderSyncTask.java:95](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:95)

```java
protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){var order="Order".equals(b.context().dataName());
  b.context().identities().saveAll(b.rows().stream().filter(s->s.action!=SyncWriteAction.UNCHANGED).map(s->new SyncIdentityStore.Save(order?"SALES_ORDER":"SALES_LINE",s.key,order?s.order.id:s.line.id,order?fingerprint(s.order):fingerprint(s.line),s.previous)).toList());
 }
```

