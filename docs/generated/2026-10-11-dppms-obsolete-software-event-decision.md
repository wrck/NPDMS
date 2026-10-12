# 旧软件事件表整表处理裁决

状态：APPLIED_AND_VERIFIED（本任务59282/19082实例）。用户回复“1,保留custom_info”：批准限定撤回635条，保留custom_info，仅清理source_log_key、source_data_state。此次限定撤回已独立执行，不冒用此前659＋6纠正授权。

635条全部由本任务此前DPPMS迁移生成，没有平台原生登记事件；1905份既有来源绑定及不可变迁入材料均核实。635条全部对应本次完整承接的659条正式历史记录。其中590条的版本字段与其自身不可变迁入材料不一致，并非根据当前原库变化推断；584条CONP、590条BOOT/CPLD/PCB、456条CONP类型/系列/标记存在差异。已有正式31列历史及8项扩展已逐字段与原表核验，ID保留。

## 建议整表处理

建议仅撤回这635条错误派生事件，不改425条当前主档及正常登记/日志历史；原来源、不可变材料、旧运行回执及绑定保留，并通过迁移功能及AST Owner产生独立撤回记录。执行前再次核对全32列、原成功回执、来源绑定和正式659条，任何后续修改阻断。不把旧表作为新维护或历史查询承接点。

现有表保留V110原29列及用户明确保留的custom_info，最终30列；撤回V4加入的source_log_key、source_data_state两列：对应内容已由正式custom_info、change_batch_id、data_state完整承接，不另加列。原有其他业务的事件路径保持；DPPMS导入只使用已批准的软件登记Owner，不再调用旧事件写入。

数据库未发现指向此表的外键。新手工登记、日志回填及页面查询使用正式软件版本记录；旧DPPMS导入的错误软件事件分支已停止，收到软件清单时明确阻断，正常调用方沿正式历史Owner承接。业务撤回及两列清理按本次635条完整预览保护执行。

## 执行前全部32列及逐项处理

|当前列|类型|可空|建议处理|
|---|---|---|---|
|id|bigint|NO|保留V110原正式结构|
|device_sn|varchar(100)|YES|保留V110原正式结构|
|source_device_key|varchar(128)|NO|保留V110原正式结构|
|source_event_key|varchar(128)|NO|保留V110原正式结构|
|conp_version|varchar(255)|YES|保留V110原正式结构|
|conp_type|varchar(100)|YES|保留V110原正式结构|
|conp_series|varchar(100)|YES|保留V110原正式结构|
|conp_mark|varchar(255)|YES|保留V110原正式结构|
|boot_version|varchar(255)|YES|保留V110原正式结构|
|cpld_version|varchar(255)|YES|保留V110原正式结构|
|pcb_version|varchar(255)|YES|保留V110原正式结构|
|customized|bit(1)|YES|保留V110原正式结构|
|release_date|date|YES|保留V110原正式结构|
|version_desc|varchar(1000)|YES|保留V110原正式结构|
|event_time|datetime(3)|NO|保留V110原正式结构|
|received_time|datetime(3)|NO|保留V110原正式结构|
|change_reason|varchar(500)|YES|保留V110原正式结构|
|changed_by|varchar(128)|YES|保留V110原正式结构|
|revoked|bit(1)|NO|保留V110原正式结构|
|mapping_status|varchar(32)|NO|保留V110原正式结构|
|source_system|varchar(32)|NO|保留V110原正式结构|
|source_version|varchar(64)|YES|保留V110原正式结构|
|sync_status|varchar(32)|NO|保留V110原正式结构|
|creator|varchar(64)|NO|保留V110原正式结构|
|create_time|datetime(3)|NO|保留V110原正式结构|
|updater|varchar(64)|NO|保留V110原正式结构|
|update_time|datetime(3)|NO|保留V110原正式结构|
|deleted|bit(1)|NO|保留V110原正式结构|
|tenant_id|bigint|NO|保留V110原正式结构|
|custom_info|json|YES|用户明确保留|
|source_log_key|varchar(128)|YES|撤回V4新增镜像，正式字段已完整承接|
|source_data_state|int|YES|撤回V4新增镜像，正式字段已完整承接|

只读证据：任务私有software-obsolete-event-canonical-correspondence-20261011.json（含635条完整现值、逐条原键与正式记录关系、1905份不可变输入核对）；software-carry-full-field-verification-20261011.json；software-event-receipt-origin-readonly-20261010.json。

## 实际执行与验证

正式迁移入口先核对635条完整32列、原V3成功回执、不可变旧快照与当前来源身份、逐条正式承接ID。五个项目批次通过PROJ迁移编排和AST Owner撤回448、115、36、12、24条，共635条；没有使用业务表DELETE脚本。独立命令回执保存全字段撤回基线，旧来源材料、旧回执与旧绑定保持。任何来源、正式承接、事件字段或客户端身份变化均阻断，批内失败整批回滚。

落库后仅清理source_log_key、source_data_state，保留custom_info及V110原29列，最终30列。原索引、外键、既有Flyway执行回执和关联迁入证据逐值保持；仓库V412定义也只保留本次批准的custom_info，不保留两个撤回列的先加后删链。

44项定向Owner/迁移测试通过；带真实登录上下文的13项MySQL测试通过，包含第二条撤回失败时第一条回滚、全字段变化保护、列清理后的重复核验。完整后端包通过。本次运行恢复时发现前一任务JVM已因原生内存分配失败退出，核实端口无占用后，仅恢复59282并调整任务JVM堆基址和编译/GC并行度；其他指定服务及业务数据保持。

两列清理后，五个撤回命令重放均REPLAY_COMPLETED；此前五个659＋6承接命令也均REPLAY_COMPLETED。独立重新核对659×31正式列、659×8扩展属性和6×11批次列，全部原业务值、原人员/历史时间及ID一致；425当前主档与51解析事实保持历史承接后的正常功能基线。真实浏览器重新查询迁入历史、原备份/标志及批次，并复核正常手工和日志产生的四条历史与当前BOOT。

任务私有证据：software-approved-event-withdrawal-state-20261011.json、software-approved-event-retirement-guard-20261011.json、software-approved-event-retirement-result-20261011.json、software-final-full-field-verification-20261011.json，以及software-final-migrated-history-browser-20261011.txt／software-final-native-history-browser-20261011.txt。该验证只覆盖本任务限定批次和实例，生产Flyway历史基线对齐及其他领域尚未完成，不宣称生产上线完成。
