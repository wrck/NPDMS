# P15 业务主键、原身份与配置键

[返回本任务](README.md)

合同：tenant_id + company_code + contract_no；发货合同：原contract_id；包件：packlist_id；条码事件：原id；设备关系：原id。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| CONTRACT | com_sales_order | migrationKey |  | — |
| SHIPMENT_CONTRACT | fb_contract | sync_evidence_key |  | — |
| PACKAGE | fb_shipment | packlist_id |  | — |
| DEVICE_SN | fb_shipment_barcode | id |  | — |
| DEVICE_RELATION | fb_shipment_barcode_relation | id |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| CONTRACT/contract | com_contract | company_code,contract_no | UPSERT |
| SHIPMENT_CONTRACT/shipmentContract | com_shipment_contract_reference | source_system,source_record_key | UPSERT |
| PACKAGE/package | com_shipment_package | source_system,source_record_key | UPSERT |
| DEVICE_SN/event | ast_device_shipment | source_system,source_key | UPSERT |
| DEVICE_RELATION/relation | ast_device_relationship | source_system,source_key | UPSERT |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| CONTRACT | pm_order_data_from_erp | id |  | — |
| SHIPMENT_CONTRACT | fb_contract | sync_evidence_key |  | — |
| PACKAGE | fb_shipment | packlist_id |  | — |
| DEVICE_SN | fb_shipment_barcode | id |  | — |
| DEVICE_RELATION | fb_shipment_barcode_relation | id |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| CONTRACT/contract | com_contract | company_code,contract_no | UPSERT |
| SHIPMENT_CONTRACT/shipmentContract | com_shipment_contract_reference | source_system,source_record_key | UPSERT |
| PACKAGE/package | com_shipment_package | source_system,source_record_key | UPSERT |
| DEVICE_SN/event | ast_device_shipment | source_system,source_key | UPSERT |
| DEVICE_RELATION/relation | ast_device_relationship | source_system,source_key | UPSERT |

## C 新虚基类

对象键和来源键的实际保存表达式如下；正式行业务定位/幂等冲突继续受目标查询和指纹保护，不能只凭一个原ID覆盖现存行。

| 实现位置 | 身份/键原表达式 |
|---|---|
| [P15DeliverySyncTask.java:38](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:38) | `var bindings=c.identities().findByKeys(new SyncIdentityStore.Query(objectKey(kind),keys));` |
| [P15DeliverySyncTask.java:86](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:86) | `@Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){String kind=b.context().dataName();b.context().identities().saveAll(b.rows().stream().filter(s->s.action!=SyncWriteAction.UNCHANGED).map(s->new SyncIdentityStore.Save(objectKey(kind),s.key,s.target.id,fingerprint(new Validation(kind,s.target)),s.previous)).toList());}` |
