# 既有日志回填增量的接收边界

状态：AUTHORIZED_SELECTIVE_REUSE / INTEGRATION_VALIDATION。用户已授权按清单直接复用，覆盖本次交接顺序。接收仅修改本任务工作树；未修改来源工作区、提交或推送。

来源：E:/AICoding/Projects/NPDMS，codex/domain-migration。接收：用户指定project-detail-followup工作树。现有日志上传、解析、回填和解析历史已经运行；本轮复用它们，新增的软件具名历史与手工维护属于已批准范围。

## 必须接收的日志解析基础增量

下列为实际存在、已逐入口核对的直接依赖清单。它不是完整分支接收授权。既有提交27e04716d的日志归档基础及其正式API/文件服务依赖需要按提交核对接收；不接收目录、位置、订单、同步引擎等无关增量。

| 文件 | 当前来源 |
|---|---|
| `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/DeviceLogParsedFactApi.java` | 未提交新增文件 |
| `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/DeviceLogSemanticFacts.java` | 未提交新增文件 |
| `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/device/DeviceParsedLogController.java` | 未提交新增文件 |
| `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/configurationlog/DeviceLogParseJobDO.java` | 未提交新增文件 |
| `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/configurationlog/DeviceLogParsedFactDO.java` | 未提交新增文件 |
| `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/configurationlog/DeviceLogParseJobMapper.java` | 未提交新增文件 |
| `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/configurationlog/DeviceLogParsedFactMapper.java` | 未提交新增文件 |
| `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/configurationlog/DeviceLogVersionProjectionMapper.java` | 未提交新增文件 |
| `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/configurationlog/query/DeviceLogDeviceLockQuery.java` | 未提交新增文件 |
| `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/configurationlog/query/DeviceLogParseJobLockQuery.java` | 未提交新增文件 |
| `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/configurationlog/query/DeviceLogParsePendingQuery.java` | 未提交新增文件 |
| `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/configurationlog/query/DeviceParsedLogHistoryQuery.java` | 未提交新增文件 |
| `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/configurationlog/DeviceArchivedLogParsingJob.java` | 未提交新增文件 |
| `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/configurationlog/DeviceArchivedLogParsingService.java` | 未提交新增文件 |
| `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/configurationlog/DeviceLogParsedFactService.java` | 未提交新增文件 |
| `pms-module-asset/src/main/resources/mapper/configurationlog/DeviceLogDeviceLockMapper.xml` | 未提交新增文件 |
| `pms-module-asset/src/main/resources/mapper/configurationlog/DeviceLogParseJobMapper.xml` | 未提交新增文件 |
| `pms-module-asset/src/main/resources/mapper/configurationlog/DeviceLogVersionProjectionMapper.xml` | 未提交新增文件 |
| `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/configurationlog/DeviceArchivedLogParsingServiceTest.java` | 未提交新增文件 |
| `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/configurationlog/DeviceLogParsedFactServiceTest.java` | 未提交新增文件 |
| `pms-module-integration/pms-module-integration-api/src/main/java/cn/iocoder/yudao/module/pms/integration/api/deviceops/DeviceLogParsingApi.java` | 未提交新增文件 |
| `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/DacDeviceLogParsingGateway.java` | 未提交新增文件 |
| `pms-module-integration/src/test/java/cn/iocoder/yudao/module/pms/integration/deviceops/DacDeviceLogParsingGatewayTest.java` | 未提交新增文件 |
| `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/collection/DeviceLogParsingController.java` | 未提交新增文件 |
| `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/collection/DeviceLogParseDO.java` | 未提交新增文件 |
| `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/collection/DeviceLogParseMapper.java` | 未提交新增文件 |
| `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/collection/query/DeviceLogParsePageQuery.java` | 未提交新增文件 |
| `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/collection/DeviceLogParsingService.java` | 未提交新增文件 |
| `pms-module-platform/src/main/resources/mapper/collection/DeviceLogParseMapper.xml` | 未提交新增文件 |
| `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/collection/DeviceLogParsingServiceTest.java` | 未提交新增文件 |
| `sql/migrations/V410__device_log_parsed_facts.sql` | 未提交新增文件 |

## 共享文件只接收日志相关差异

- `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/configurationlog/DeviceLogArchiveService.java`
- `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/device/DeviceLogArchiveController.java`
- `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/configurationlog/DeviceLogFileDO.java`
- `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/configurationlog/DeviceLogImportDO.java`
- `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/device/DeviceMapper.java`

DeviceMapper 的项目安装设备锁查询不属于接收范围；只接收 selectLogDeviceForUpdate。日志归档Service包括解析注册、租户/项目范围版本复核和现有读取的事务修正；对应测试一并保留。界面只接收既有日志解析入口及解析历史消费者，先按实际import依赖确认，不整体覆盖设备页面或API文件。

装配图锁及槽位投影属于后续硬件功能，未证明是软件解析的必需依赖，不自动纳入；若实际编译依赖必须引入，应列具体调用及最小范围，不能复制整套硬件改动。

## 接收后接入点与保留边界

DeviceArchivedLogParsingService → DeviceLogParsedFactService 是实际回填路径；后者在冻结解析事实前计算是否采用当前值。接收后该路径复用已批准的软件版本Owner写入具名历史，依据日志接收时间保护较新手工值，仅更新实际解析出的四项版本。正常日志解析事实保持不可变；不修改已有50条事实，不将635条DPPMS旧事件当原生功能。

来源文档 docs/generated/2026-10-06-device-log-parsing.md 记录59191/19191/48182真实上传、解析、导出、重开及失败路径验收，证据可以按前提是否变化复用；本任务59282/19082的最终联调和软件历史查询仍需单独完成。

## 交接依据与待确认动作

npdms-change-delivery 技能引用工程链6.2/6.3：来源未提交代码仅供了解进展，不直接复制成另一套实现。跨Worktree接收需明确来源Owner、范围及已提交交接；提交/合并须用户授权。当前master无该日志增量的有效交接记录，因此仅此接入部分等待正式交接或用户明确覆盖该流程。已批准批次、历史实体、原字段映射及独立验证继续，不等待整个来源Feature完成。

## 最新授权与实际接收

用户授权按清单直接复用，覆盖此次交接顺序。不修改来源工作区，不提交或推送。实际解析归档Service显式调用槽位Service，故其现有直接依赖随增量接收；不是新建硬件模块。已只读核实指定库中 ast_device_assembly_graph_lock、ast_device_log_parse_job、ast_device_log_parsed_fact 均已存在，相关DDL复用来源定义，不执行新建。接收只写本任务缺失文件，所有既存差异逐文件合并；接收明细保存在任务私有 software-log-reception-20261010.json。

已核实日志页面直接依赖LogParseResult、decodeLogText及其semantic展示组件，追加选择性复用以下现有文件，不重建日志解析界面：

- `yudao-ui/yudao-ui-admin-vue3/src/components/DeviceCollection/LogParseResult.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/components/DeviceCollection/decodeLogText.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/components/DeviceCollection/semantic/evidence.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/components/DeviceCollection/semantic/SemanticEntityView.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/components/DeviceCollection/semantic/SemanticEvidenceTable.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/components/DeviceCollection/semantic/SemanticGenericConfig.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/components/DeviceCollection/semantic/SemanticGenericKeyValueTree.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/components/DeviceCollection/semantic/SemanticGenericRecordList.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/components/DeviceCollection/semantic/SemanticGenericSection.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/components/DeviceCollection/semantic/SemanticGenericStructureView.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/components/DeviceCollection/semantic/SemanticGenericTable.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/components/DeviceCollection/semantic/SemanticValueTree.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/components/DeviceCollection/semantic/types.ts`


## 已授权接入的日志档案对话框依赖

正常软件维护页复用来源工作区既有 ProjectDeviceLogArchiveDialog.vue，作为第14个前端依赖接入项目日志档案入口。源工作区保持未修改。任务19082真实窗口已显示原生档案、文件历史、解析结果及下载入口；手工保存后保持当前在网版本页签并立即刷新历史已实际核验。日志下载仍受指定库缺少既有V398材料version列阻断；已完成旧解析的状态查询还发现重复解释BOOT语义触发历史保护，现按不可变事实查询修正，未改写旧解析事实。

## 2026-10-11任务实例实际接入结果

V398已按用户批准应用，236条材料原35列保持；日志Owner别名和下载票据请求来源缺口已修复并验证。正式API上传后，19082真实浏览器完成原生档案、解析1.3.2、当前BOOT=Basic 17.05.16、软件历史及前后比较。已有手工维护验收继续有效。DeviceSoftwareSourceHistoryPanel已用于正常登记、日志和迁入历史查询；源E工作区未修改。原50条解析事实及当前主档其他424条未被正常验收写入改动，历史承接也未改425条当前投影。详细范围与证据见材料整表裁决和软件现存记录限定纠正文档。
