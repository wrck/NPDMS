# 软件来源事件表来源与当前业务接入核查

状态：READ_ONLY / BUSINESS_BOUNDARY_PENDING。2026-10-10用户指出当前软件更新没有写入本表，要求核实来源。新增12列的裁决保留；本次未修改软件代码、SQL、实际结构或业务数据，实际切换暂停。

后续裁决：用户明确本表不能作为正式业务权威表，并要求“先补齐功能，再承接历史”。此前复用本表的建议已撤回；本报告只作为来源核查证据。正常版本更新业务模型及正式表重新规划，全部新表、新列仍需用户整表裁决，不扩大原12列批准的适用表。

## 已核实结论

`ast_device_network_version_event`不是本任务V4创建的表，但也不能据此称它为当前软件更新功能已经使用的变更历史表。它来自设备主档前向迁移设计，原始定义是“AST设备在网软件版本来源事件”。

当前工作树的`DeviceNetworkVersionService.apply`实现了写事件及更新当前主档/设备投影；全仓生产源码检索没有找到业务入口对它的调用，仅存在服务自身、测试和迁移服务的注入（注入不等于调用）。当前设备详情的“在网版本”是只读展示，读取`ast_device_network_version`。本任务新增的来源历史展示读取V414观察表，不能作为当前软件更新历史功能已完成的证据。

本任务指定目标库重新只读核查：本表635条全部`source_system=DPPMS`；全部来源键均为`pm_project_soft_version:<原ID>`，均能核实到本批原表记录和原设备SN；没有其他来源系统的事件。635条均有PLT迁移目标绑定，共1905条绑定（不能将多次绑定当成1905次版本变化）。记录创建时间为2026-10-08 16:00:02.231～16:00:32.163，整表与此前只读核查一致。以上证明这些数据的迁移来源，不能证明当前业务生成软件变更历史。

逐条回溯PLT来源记录及批次：1905条绑定分别来自`DPPMS_NATIVE_V1`、`DPPMS_NATIVE_V2`、`DPPMS_NATIVE_V3`各635条，15个已完成批次（5个有软件来源记录的项目×3次承接）。它们的原表全部为`pm_project_soft_version`、Owner为PROJ、目标角色为SOFTWARE_EVENT。635个事件ID全部有对应证据；数据是在此前迁入时产生，并非V4才建表或当前软件更新动作产生。新增证据`software-event-receipt-origin-readonly-20261010.json`，writes=0。

## 创建依据

| 证据 | 已核实事实 | 能证明的范围 |
|---|---|---|
| Git提交`3de265f01`，2026-08-28 | 新增V110文件，包含本表CREATE TABLE | 表的源码定义早于本任务V4 |
| Git提交`162d00b73`，2026-08-27 | 已有DeviceNetworkVersionService实现 | 事件写入能力的代码存在；不证明业务已接入 |
| 目标库Flyway V110 | 2026-09-17 20:37:35执行成功 | 本任务库原表实际建立时间 |
| 目标库Flyway V412 | 2026-10-08 19:07:33执行成功 | 本任务追加custom_info/source_log_key/source_data_state三列的版本是V412 |
| 目标库Flyway V414 | 2026-10-09 00:29:17执行成功 | 观察表和批次表由本任务建立；不能冒充之前已有功能 |

## 另两张“版本历史”的真实用途

`ast_device_version`现有13列：id、device_id、version_no、change_type、change_description、before_snapshot、after_snapshot、creator、create_time、updater、update_time、deleted、tenant_id。它由设备档案服务保存创建、修改、部署、故障、维修、报废等档案变化，`version_no`是设备档案的递增修订号。不能仅凭“version”命名认定其为CONP/BOOT/CPLD/PCB的软件更新历史。

`pms_equipment_version_retired`亦为设备档案修订记录，V310已退役并有只读保护。不得为了迁入软件原表字段而恢复旧写入路径，也不能把原备份版本、变化标志、登记日期和JSON塞入档案快照，代替正式软件属性承接。

## 更正与后续边界

此前“优先复用既有软件历史表”的表述只核实了存在的事件模型，未证明当前软件更新功能使用它，判断依据不足，现明确更正。635条记录不是本平台原生软件更新历史；V4批次表也不是现成功能的复用。

原建议曾将既有来源事件模型作为正式登记历史候选；用户后续已否决该承接目标，建议撤回。新的当前版本更新闭环按用户要求优先完成，正式业务表与Owner须基于正常业务规划。635条纠正、观察表撤回和批次表结构均不在此次只读核查中执行。

源码证据：`sql/migrations/V110__fast001_device_shipments_and_software_versions.sql`；`pms-module-asset/.../service/version/DeviceNetworkVersionService.java`；`pms-module-asset/.../service/migration/DppmsAssetEntityImportService.java`；`pms-module-asset/.../service/device/DeviceDetailService.java`及`DeviceArchiveServiceImpl.java`；前端`DeviceNetworkVersionPanel.vue`。实际证据保存于任务私有目录`software-event-provenance-readonly-20261010.json`，writes=0，不改变不可变来源材料、迁移回执或Flyway历史。

## 后续裁决与跨工作区补充核查

用户明确软件版本由项目人员手工维护，或上传日志后自动回填，与ITR无关；BOOT使用Basic Bootloader。随后指出日志回填功能已有，要求先明确缺失再实施。

此前当前业务未接入的结论仅适用于`DeviceNetworkVersionService.apply`与该来源事件表，不能扩大为整个平台没有日志回填。重新核对主工作区后，已发现正常上传归档→DAC解析→AST解析事实→当前版本/设备投影链路，正式解析记录表为`ast_device_log_parsed_fact`。指定库当前有50条解析记录、14条DEVICE_LOG当前版本。主工作区已有日志解析及历史重开的浏览器验收记录，本轮未重做业务上传、不宣称本轮端到端通过。

工作区差异已明确：本任务HEAD为`4d522dbc3`，主工作区HEAD为`22bf74f30`；V409归档增量已提交，后续解析回传服务、API及V410在主工作区仍未提交。不能把本任务旧工作树缺少文件当作平台业务缺失，也不能直接复制未交接的提供方实现。以上不改变635条DPPMS来源事件的来源结论，不使该事件表成为正式版本业务承接目标。

补充只读证据为`existing-log-version-function-readonly-20261010.json`。所有后续软件新增实现停止，待明确实际缺口及正式承接方案；已具备日志回填与解析历史优先复用。软件业务写入0，DDL0，服务重启0。
