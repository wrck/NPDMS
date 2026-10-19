# P35 新虚基类实际读写字段衔接

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。八个软件历史扩展已暂缓，不读写其属性或定义；基础批次/版本历史预览现停在原项目当前身份冲突。正式父软件批次仍只用本任务独立来源身份和本次规划，不接管旧任务记录。

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p35)；本页早期流程描述按该修订解释。

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectSoftwareBatch | s.id | sourceId |
| selectSoftwareBatch | s.projectId | originalProjectId |
| selectSoftwareBatch | s.`changeVersion` | changeVersion |
| selectSoftwareBatch | s.`changeRemark` | changeRemark |
| selectSoftwareBatch | s.`latest` | latest |
| selectSoftwareBatch | s.createTime | createTime |
| selectSoftwareBatch | s.createBy | source_creator |
| selectSoftwareBatch | s.updateTime | updateTime |
| selectSoftwareBatch | s.updateBy | source_updater |
| selectSoftwareObservation | s.id | sourceId |
| selectSoftwareObservation | s.projectId | originalProjectId |
| selectSoftwareObservation | s.`logId` | logId |
| selectSoftwareObservation | s.`contractNo` | contractNo |
| selectSoftwareObservation | s.`itemCode` | itemCode |
| selectSoftwareObservation | s.`barCode` | barCode |
| selectSoftwareObservation | s.`conp` | conp |
| selectSoftwareObservation | s.`conpType` | conpType |
| selectSoftwareObservation | s.`conpSeries` | conpSeries |
| selectSoftwareObservation | s.`conpMark` | conpMark |
| selectSoftwareObservation | s.`cpld` | cpld |
| selectSoftwareObservation | s.`boot` | boot |
| selectSoftwareObservation | s.`pcb` | pcb |
| selectSoftwareObservation | s.`executeTime` | executeTime |
| selectSoftwareObservation | s.`datastate` | datastate |
| selectSoftwareObservation | s.`customInfo` | customInfo |
| selectSoftwareObservation | s.createTime | createTime |
| selectSoftwareObservation | s.createBy | source_creator |
| selectSoftwareObservation | s.updateTime | updateTime |
| selectSoftwareObservation | s.updateBy | source_updater |

| 正式表.列 | 实际写入参数 | INSERT语句 |
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
