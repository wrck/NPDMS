# P35 三种实现的正式写入字段

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。八个软件历史扩展已暂缓，不读写其属性或定义；基础批次/版本历史预览现停在原项目当前身份冲突。正式父软件批次仍只用本任务独立来源身份和本次规划，不接管旧任务记录。

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| ast_device_software_change_batch.id | #{row.values.id} | insertHistorical |
| ast_device_software_change_batch.tenant_id | #{row.values.tenantId} | insertHistorical |
| ast_device_software_change_batch.project_id | #{row.values.projectId} | insertHistorical |
| ast_device_software_change_batch.change_version | #{row.values.changeVersion} | insertHistorical |
| ast_device_software_change_batch.change_remark | #{row.values.changeRemark} | insertHistorical |
| ast_device_software_change_batch.latest | #{row.values.latest} | insertHistorical |
| ast_device_software_change_batch.creator | #{row.values.creator} | insertHistorical |
| ast_device_software_change_batch.create_time | #{row.values.createTime} | insertHistorical |
| ast_device_software_change_batch.updater | #{row.values.updater} | insertHistorical |
| ast_device_software_change_batch.update_time | #{row.values.updateTime} | insertHistorical |
| ast_device_software_change_batch.deleted | #{row.values.deleted} | insertHistorical |
| ast_device_software_version_record.id | #{row.values.id} | insertHistorical |
| ast_device_software_version_record.tenant_id | #{row.values.tenantId} | insertHistorical |
| ast_device_software_version_record.device_id | #{row.values.deviceId} | insertHistorical |
| ast_device_software_version_record.device_sn | #{row.values.deviceSn} | insertHistorical |
| ast_device_software_version_record.project_id | #{row.values.projectId} | insertHistorical |
| ast_device_software_version_record.previous_record_id | #{row.values.previousRecordId} | insertHistorical |
| ast_device_software_version_record.conp_version | #{row.values.conpVersion} | insertHistorical |
| ast_device_software_version_record.conp_type | #{row.values.conpType} | insertHistorical |
| ast_device_software_version_record.conp_series | #{row.values.conpSeries} | insertHistorical |
| ast_device_software_version_record.conp_mark | #{row.values.conpMark} | insertHistorical |
| ast_device_software_version_record.boot_version | #{row.values.bootVersion} | insertHistorical |
| ast_device_software_version_record.cpld_version | #{row.values.cpldVersion} | insertHistorical |
| ast_device_software_version_record.pcb_version | #{row.values.pcbVersion} | insertHistorical |
| ast_device_software_version_record.customized | #{row.values.customized} | insertHistorical |
| ast_device_software_version_record.release_date | #{row.values.releaseDate} | insertHistorical |
| ast_device_software_version_record.version_desc | #{row.values.versionDesc} | insertHistorical |
| ast_device_software_version_record.recorded_at | #{row.values.recordedAt} | insertHistorical |
| ast_device_software_version_record.data_source | #{row.values.dataSource} | insertHistorical |
| ast_device_software_version_record.parsed_fact_id | #{row.values.parsedFactId} | insertHistorical |
| ast_device_software_version_record.request_key | #{row.values.requestKey} | insertHistorical |
| ast_device_software_version_record.change_batch_id | #{row.values.changeBatchId} | insertHistorical |
| ast_device_software_version_record.contract_no | #{row.values.contractNo} | insertHistorical |
| ast_device_software_version_record.product_code | #{row.values.productCode} | insertHistorical |
| ast_device_software_version_record.execute_time | #{row.values.executeTime} | insertHistorical |
| ast_device_software_version_record.data_state | #{row.values.dataState} | insertHistorical |
| ast_device_software_version_record.custom_info | #{row.values.customInfo} | insertHistorical |
| ast_device_software_version_record.creator | #{row.values.creator} | insertHistorical |
| ast_device_software_version_record.create_time | #{row.values.createTime} | insertHistorical |
| ast_device_software_version_record.updater | #{row.values.updater} | insertHistorical |
| ast_device_software_version_record.update_time | #{row.values.updateTime} | insertHistorical |
| ast_device_software_version_record.deleted | #{row.values.deleted} | insertHistorical |

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
| ast_device_software_change_batch.id | #{row.values.id} | insertHistorical |
| ast_device_software_change_batch.tenant_id | #{row.values.tenantId} | insertHistorical |
| ast_device_software_change_batch.project_id | #{row.values.projectId} | insertHistorical |
| ast_device_software_change_batch.change_version | #{row.values.changeVersion} | insertHistorical |
| ast_device_software_change_batch.change_remark | #{row.values.changeRemark} | insertHistorical |
| ast_device_software_change_batch.latest | #{row.values.latest} | insertHistorical |
| ast_device_software_change_batch.creator | #{row.values.creator} | insertHistorical |
| ast_device_software_change_batch.create_time | #{row.values.createTime} | insertHistorical |
| ast_device_software_change_batch.updater | #{row.values.updater} | insertHistorical |
| ast_device_software_change_batch.update_time | #{row.values.updateTime} | insertHistorical |
| ast_device_software_change_batch.deleted | #{row.values.deleted} | insertHistorical |
| ast_device_software_version_record.id | #{row.values.id} | insertHistorical |
| ast_device_software_version_record.tenant_id | #{row.values.tenantId} | insertHistorical |
| ast_device_software_version_record.device_id | #{row.values.deviceId} | insertHistorical |
| ast_device_software_version_record.device_sn | #{row.values.deviceSn} | insertHistorical |
| ast_device_software_version_record.project_id | #{row.values.projectId} | insertHistorical |
| ast_device_software_version_record.previous_record_id | #{row.values.previousRecordId} | insertHistorical |
| ast_device_software_version_record.conp_version | #{row.values.conpVersion} | insertHistorical |
| ast_device_software_version_record.conp_type | #{row.values.conpType} | insertHistorical |
| ast_device_software_version_record.conp_series | #{row.values.conpSeries} | insertHistorical |
| ast_device_software_version_record.conp_mark | #{row.values.conpMark} | insertHistorical |
| ast_device_software_version_record.boot_version | #{row.values.bootVersion} | insertHistorical |
| ast_device_software_version_record.cpld_version | #{row.values.cpldVersion} | insertHistorical |
| ast_device_software_version_record.pcb_version | #{row.values.pcbVersion} | insertHistorical |
| ast_device_software_version_record.customized | #{row.values.customized} | insertHistorical |
| ast_device_software_version_record.release_date | #{row.values.releaseDate} | insertHistorical |
| ast_device_software_version_record.version_desc | #{row.values.versionDesc} | insertHistorical |
| ast_device_software_version_record.recorded_at | #{row.values.recordedAt} | insertHistorical |
| ast_device_software_version_record.data_source | #{row.values.dataSource} | insertHistorical |
| ast_device_software_version_record.parsed_fact_id | #{row.values.parsedFactId} | insertHistorical |
| ast_device_software_version_record.request_key | #{row.values.requestKey} | insertHistorical |
| ast_device_software_version_record.change_batch_id | #{row.values.changeBatchId} | insertHistorical |
| ast_device_software_version_record.contract_no | #{row.values.contractNo} | insertHistorical |
| ast_device_software_version_record.product_code | #{row.values.productCode} | insertHistorical |
| ast_device_software_version_record.execute_time | #{row.values.executeTime} | insertHistorical |
| ast_device_software_version_record.data_state | #{row.values.dataState} | insertHistorical |
| ast_device_software_version_record.custom_info | #{row.values.customInfo} | insertHistorical |
| ast_device_software_version_record.creator | #{row.values.creator} | insertHistorical |
| ast_device_software_version_record.create_time | #{row.values.createTime} | insertHistorical |
| ast_device_software_version_record.updater | #{row.values.updater} | insertHistorical |
| ast_device_software_version_record.update_time | #{row.values.updateTime} | insertHistorical |
| ast_device_software_version_record.deleted | #{row.values.deleted} | insertHistorical |
## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| ast_device_software_change_batch.id | #{row.batch.id} | insertSoftwareBatch |
| ast_device_software_change_batch.tenant_id | #{row.batch.tenantId} | insertSoftwareBatch |
| ast_device_software_change_batch.project_id | #{row.batch.projectId} | insertSoftwareBatch |
| ast_device_software_change_batch.change_version | #{row.batch.changeVersion} | insertSoftwareBatch |
| ast_device_software_change_batch.change_remark | #{row.batch.changeRemark} | insertSoftwareBatch |
| ast_device_software_change_batch.latest | #{row.batch.latest} | insertSoftwareBatch |
| ast_device_software_change_batch.creator | #{row.batch.creator} | insertSoftwareBatch |
| ast_device_software_change_batch.create_time | #{row.batch.createTime} | insertSoftwareBatch |
| ast_device_software_change_batch.updater | #{row.batch.updater} | insertSoftwareBatch |
| ast_device_software_change_batch.update_time | #{row.batch.updateTime} | insertSoftwareBatch |
| ast_device_software_change_batch.deleted | #{row.batch.deleted} | insertSoftwareBatch |
| ast_device_software_version_record.id | #{row.observation.id} | insertSoftwareObservation |
| ast_device_software_version_record.tenant_id | #{row.observation.tenantId} | insertSoftwareObservation |
| ast_device_software_version_record.device_id | #{row.observation.deviceId} | insertSoftwareObservation |
| ast_device_software_version_record.device_sn | #{row.observation.deviceSn} | insertSoftwareObservation |
| ast_device_software_version_record.project_id | #{row.observation.projectId} | insertSoftwareObservation |
| ast_device_software_version_record.previous_record_id | #{row.observation.previousRecordId} | insertSoftwareObservation |
| ast_device_software_version_record.conp_version | #{row.observation.conpVersion} | insertSoftwareObservation |
| ast_device_software_version_record.conp_type | #{row.observation.conpType} | insertSoftwareObservation |
| ast_device_software_version_record.conp_series | #{row.observation.conpSeries} | insertSoftwareObservation |
| ast_device_software_version_record.conp_mark | #{row.observation.conpMark} | insertSoftwareObservation |
| ast_device_software_version_record.boot_version | #{row.observation.bootVersion} | insertSoftwareObservation |
| ast_device_software_version_record.cpld_version | #{row.observation.cpldVersion} | insertSoftwareObservation |
| ast_device_software_version_record.pcb_version | #{row.observation.pcbVersion} | insertSoftwareObservation |
| ast_device_software_version_record.customized | #{row.observation.customized} | insertSoftwareObservation |
| ast_device_software_version_record.release_date | #{row.observation.releaseDate} | insertSoftwareObservation |
| ast_device_software_version_record.version_desc | #{row.observation.versionDesc} | insertSoftwareObservation |
| ast_device_software_version_record.recorded_at | #{row.observation.recordedAt} | insertSoftwareObservation |
| ast_device_software_version_record.data_source | #{row.observation.dataSource} | insertSoftwareObservation |
| ast_device_software_version_record.parsed_fact_id | #{row.observation.parsedFactId} | insertSoftwareObservation |
| ast_device_software_version_record.request_key | #{row.observation.requestKey} | insertSoftwareObservation |
| ast_device_software_version_record.change_batch_id | #{row.observation.changeBatchId} | insertSoftwareObservation |
| ast_device_software_version_record.contract_no | #{row.observation.contractNo} | insertSoftwareObservation |
| ast_device_software_version_record.product_code | #{row.observation.productCode} | insertSoftwareObservation |
| ast_device_software_version_record.execute_time | #{row.observation.executeTime} | insertSoftwareObservation |
| ast_device_software_version_record.data_state | #{row.observation.dataState} | insertSoftwareObservation |
| ast_device_software_version_record.custom_info | #{row.observation.customInfo} | insertSoftwareObservation |
| ast_device_software_version_record.creator | #{row.observation.creator} | insertSoftwareObservation |
| ast_device_software_version_record.create_time | #{row.observation.createTime} | insertSoftwareObservation |
| ast_device_software_version_record.updater | #{row.observation.updater} | insertSoftwareObservation |
| ast_device_software_version_record.update_time | #{row.observation.updateTime} | insertSoftwareObservation |
| ast_device_software_version_record.deleted | #{row.observation.deleted} | insertSoftwareObservation |
