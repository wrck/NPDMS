# P19 三种实现的正式写入字段

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。四个项目状态扩展已暂缓，不读状态原表或正式扩展定义；相关方预览现停在原项目ID冲突。需解决同一40项项目身份冲突，不能接管已有相关方或项目。

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| proj_project_party.id | #{command.row.id} | insertMigrationParty |
| proj_project_party.tenant_id | #{command.row.tenantId} | insertMigrationParty |
| proj_project_party.project_id | #{command.row.projectId} | insertMigrationParty |
| proj_project_party.party_role | #{command.row.partyRole} | insertMigrationParty |
| proj_project_party.party_code | #{command.row.partyCode} | insertMigrationParty |
| proj_project_party.party_name | #{command.row.partyName} | insertMigrationParty |
| proj_project_party.contact_name | #{command.row.contactName} | insertMigrationParty |
| proj_project_party.phone | #{command.row.phone} | insertMigrationParty |
| proj_project_party.source_system | #{command.row.sourceSystem} | insertMigrationParty |
| proj_project_party.source_table | #{command.row.sourceTable} | insertMigrationParty |
| proj_project_party.source_record_key | #{command.row.sourceRecordKey} | insertMigrationParty |
| proj_project_party.effective_from | #{command.row.effectiveFrom} | insertMigrationParty |
| proj_project_party.effective_to | #{command.row.effectiveTo} | insertMigrationParty |
| proj_project_party.status | #{command.row.status} | insertMigrationParty |
| proj_project_party.version | #{command.row.version} | insertMigrationParty |
| proj_project_party.creator | #{command.creator} | insertMigrationParty |
| proj_project_party.create_time | #{command.row.createTime} | insertMigrationParty |
| proj_project_party.updater | #{command.updater} | insertMigrationParty |
| proj_project_party.update_time | #{command.row.updateTime} | insertMigrationParty |
| proj_project_party.deleted | #{command.row.deleted} | insertMigrationParty |

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `plt_entity_extension_value` (`id`, `tenant_id`, `owner_module`, `entity_type`, `entity_id`, `revision_id`, `definition_revision_id`, `values_json`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (:orm_id, :orm_tenant_id, :orm_owner_module, :orm_entity_type, :orm_entity_id, :orm_revision_id, :orm_definition_revision_id, :orm_values_json, :orm_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted);
```

## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| proj_project_party.id | #{command.row.id} | insertMigrationParty |
| proj_project_party.tenant_id | #{command.row.tenantId} | insertMigrationParty |
| proj_project_party.project_id | #{command.row.projectId} | insertMigrationParty |
| proj_project_party.party_role | #{command.row.partyRole} | insertMigrationParty |
| proj_project_party.party_code | #{command.row.partyCode} | insertMigrationParty |
| proj_project_party.party_name | #{command.row.partyName} | insertMigrationParty |
| proj_project_party.contact_name | #{command.row.contactName} | insertMigrationParty |
| proj_project_party.phone | #{command.row.phone} | insertMigrationParty |
| proj_project_party.source_system | #{command.row.sourceSystem} | insertMigrationParty |
| proj_project_party.source_table | #{command.row.sourceTable} | insertMigrationParty |
| proj_project_party.source_record_key | #{command.row.sourceRecordKey} | insertMigrationParty |
| proj_project_party.effective_from | #{command.row.effectiveFrom} | insertMigrationParty |
| proj_project_party.effective_to | #{command.row.effectiveTo} | insertMigrationParty |
| proj_project_party.status | #{command.row.status} | insertMigrationParty |
| proj_project_party.version | #{command.row.version} | insertMigrationParty |
| proj_project_party.creator | #{command.creator} | insertMigrationParty |
| proj_project_party.create_time | #{command.row.createTime} | insertMigrationParty |
| proj_project_party.updater | #{command.updater} | insertMigrationParty |
| proj_project_party.update_time | #{command.row.updateTime} | insertMigrationParty |
| proj_project_party.deleted | #{command.row.deleted} | insertMigrationParty |
## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| proj_project_party.id | #{row.target.id} | insertProjectParty |
| proj_project_party.tenant_id | #{row.target.tenantId} | insertProjectParty |
| proj_project_party.project_id | #{row.target.projectId} | insertProjectParty |
| proj_project_party.party_role | #{row.target.partyRole} | insertProjectParty |
| proj_project_party.party_code | #{row.target.partyCode} | insertProjectParty |
| proj_project_party.party_name | #{row.target.partyName} | insertProjectParty |
| proj_project_party.contact_name | #{row.target.contactName} | insertProjectParty |
| proj_project_party.phone | #{row.target.phone} | insertProjectParty |
| proj_project_party.source_system | #{row.target.sourceSystem} | insertProjectParty |
| proj_project_party.source_table | #{row.target.sourceTable} | insertProjectParty |
| proj_project_party.source_record_key | #{row.target.sourceRecordKey} | insertProjectParty |
| proj_project_party.effective_from | #{row.target.effectiveFrom} | insertProjectParty |
| proj_project_party.effective_to | #{row.target.effectiveTo} | insertProjectParty |
| proj_project_party.status | #{row.target.status} | insertProjectParty |
| proj_project_party.version | #{row.target.version} | insertProjectParty |
| proj_project_party.creator | #{row.target.creator} | insertProjectParty |
| proj_project_party.create_time | #{row.target.createTime} | insertProjectParty |
| proj_project_party.updater | #{row.target.updater} | insertProjectParty |
| proj_project_party.update_time | #{row.target.updateTime} | insertProjectParty |
| proj_project_party.deleted | #{row.target.deleted} | insertProjectParty |
