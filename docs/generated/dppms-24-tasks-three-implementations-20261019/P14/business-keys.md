# P14 业务主键、原身份与配置键

[返回本任务](README.md)

订单：tenant_id + source_system + company_code + order_type + order_no；A当前/C的行再加line_no，B已保存定义的行用order_id + line_no；line_type是载荷字段，不混入业务唯一键。原业务键不得被原id替代。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| ORDER | pm_order_data_from_erp | migrationSourceKey |  | migrationVersion |
| LINE | pm_order_line_from_erp | migrationSourceKey |  | migrationVersion |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| ORDER/order | com_sales_order | source_system,company_code,order_type,order_no | UPSERT |
| LINE/line | com_sales_order_line | source_system,company_code,order_type,order_no,line_no | UPSERT |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| ORDER | pm_order_data_from_erp | migrationSourceKey |  | migrationVersion |
| LINE | pm_order_line_from_erp | migrationSourceKey |  | migrationVersion |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| ORDER/order | com_sales_order | source_system,company_code,order_type,order_no | UPSERT |
| LINE/line | com_sales_order_line | order_id,line_no | UPSERT |

## C 新虚基类

对象键和来源键的实际保存表达式如下；正式行业务定位/幂等冲突继续受目标查询和指纹保护，不能只凭一个原ID覆盖现存行。

| 实现位置 | 身份/键原表达式 |
|---|---|
| [P14SalesOrderSyncTask.java:31](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:31) | `var bindings=new HashMap<String,SyncIdentityStore.Identity>();for(int i=0;i<keys.size();i+=500)bindings.putAll(c.identities().findByKeys(new SyncIdentityStore.Query("SALES_ORDER",keys.subList(i,Math.min(i+500,keys.size())))));` |
| [P14SalesOrderSyncTask.java:51](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:51) | `var c=b.context();var keys=b.rows().stream().map(s->s.key).toList();var bindings=c.identities().findByKeys(new SyncIdentityStore.Query("SALES_LINE",keys));` |
| [P14SalesOrderSyncTask.java:96](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:96) | `b.context().identities().saveAll(b.rows().stream().filter(s->s.action!=SyncWriteAction.UNCHANGED).map(s->new SyncIdentityStore.Save(order?"SALES_ORDER":"SALES_LINE",s.key,order?s.order.id:s.line.id,order?fingerprint(s.order):fingerprint(s.line),s.previous)).toList());` |
| [P14SalesOrderSyncTask.java:98](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:98) | `static OrderKey key(OrderRow r){return new OrderKey(r.sourceSystem,r.companyCode,r.orderType,r.orderNo);}` |
| [P14SalesOrderSyncTask.java:99](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:99) | `static LineKey key(LineRow r){return new LineKey(r.sourceSystem,r.companyCode,r.orderType,r.orderNo,r.lineNo);}` |
| [P14SalesOrderSyncTask.java:104](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:104) | `public record OrderKey(String sourceSystem,String companyCode,String orderType,String orderNo){}` |
| [P14SalesOrderSyncTask.java:105](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:105) | `public record LineKey(String sourceSystem,String companyCode,String orderType,String orderNo,String lineNo){}` |
