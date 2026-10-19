# P18 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| com_project_contract_relation.id | #{row.id} | insertHistorical |
| com_project_contract_relation.tenant_id | #{row.tenantId} | insertHistorical |
| com_project_contract_relation.project_id | #{row.projectId} | insertHistorical |
| com_project_contract_relation.contract_id | #{row.contractId} | insertHistorical |
| com_project_contract_relation.relation_role | #{row.relationRole} | insertHistorical |
| com_project_contract_relation.source_system | #{row.sourceSystem} | insertHistorical |
| com_project_contract_relation.source_table | #{row.sourceTable} | insertHistorical |
| com_project_contract_relation.source_record_key | #{row.sourceRecordKey} | insertHistorical |
| com_project_contract_relation.effective_from | #{row.effectiveFrom} | insertHistorical |
| com_project_contract_relation.effective_to | #{row.effectiveTo} | insertHistorical |
| com_project_contract_relation.status | #{row.status} | insertHistorical |
| com_project_contract_relation.version | #{row.version} | insertHistorical |
| com_project_contract_relation.creator | #{row.creator} | insertHistorical |
| com_project_contract_relation.create_time | #{row.createTime} | insertHistorical |
| com_project_contract_relation.updater | #{row.updater} | insertHistorical |
| com_project_contract_relation.update_time | #{row.updateTime} | insertHistorical |
| com_project_contract_relation.deleted | #{row.deleted} | insertHistorical |
## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| com_project_contract_relation.id | #{row.id} | insertHistorical |
| com_project_contract_relation.tenant_id | #{row.tenantId} | insertHistorical |
| com_project_contract_relation.project_id | #{row.projectId} | insertHistorical |
| com_project_contract_relation.contract_id | #{row.contractId} | insertHistorical |
| com_project_contract_relation.relation_role | #{row.relationRole} | insertHistorical |
| com_project_contract_relation.source_system | #{row.sourceSystem} | insertHistorical |
| com_project_contract_relation.source_table | #{row.sourceTable} | insertHistorical |
| com_project_contract_relation.source_record_key | #{row.sourceRecordKey} | insertHistorical |
| com_project_contract_relation.effective_from | #{row.effectiveFrom} | insertHistorical |
| com_project_contract_relation.effective_to | #{row.effectiveTo} | insertHistorical |
| com_project_contract_relation.status | #{row.status} | insertHistorical |
| com_project_contract_relation.version | #{row.version} | insertHistorical |
| com_project_contract_relation.creator | #{row.creator} | insertHistorical |
| com_project_contract_relation.create_time | #{row.createTime} | insertHistorical |
| com_project_contract_relation.updater | #{row.updater} | insertHistorical |
| com_project_contract_relation.update_time | #{row.updateTime} | insertHistorical |
| com_project_contract_relation.deleted | #{row.deleted} | insertHistorical |
## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| com_project_contract_relation.id | #{row.target.id} | insertProjectContract |
| com_project_contract_relation.tenant_id | #{row.target.tenantId} | insertProjectContract |
| com_project_contract_relation.project_id | #{row.target.projectId} | insertProjectContract |
| com_project_contract_relation.contract_id | #{row.target.contractId} | insertProjectContract |
| com_project_contract_relation.relation_role | #{row.target.relationRole} | insertProjectContract |
| com_project_contract_relation.source_system | #{row.target.sourceSystem} | insertProjectContract |
| com_project_contract_relation.source_table | #{row.target.sourceTable} | insertProjectContract |
| com_project_contract_relation.source_record_key | #{row.target.sourceRecordKey} | insertProjectContract |
| com_project_contract_relation.effective_from | #{row.target.effectiveFrom} | insertProjectContract |
| com_project_contract_relation.effective_to | #{row.target.effectiveTo} | insertProjectContract |
| com_project_contract_relation.status | #{row.target.status} | insertProjectContract |
| com_project_contract_relation.version | #{row.target.version} | insertProjectContract |
| com_project_contract_relation.creator | #{row.target.creator} | insertProjectContract |
| com_project_contract_relation.create_time | #{row.target.createTime} | insertProjectContract |
| com_project_contract_relation.updater | #{row.target.updater} | insertProjectContract |
| com_project_contract_relation.update_time | #{row.target.updateTime} | insertProjectContract |
| com_project_contract_relation.deleted | #{row.target.deleted} | insertProjectContract |
