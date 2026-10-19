# P15 前后置处理

[返回本任务](README.md)

前置：保持合同客户消歧、原父来源键与精确业务码；运输字典每轮一次；正式P24完整输入不能由预览冒充。

后置：原发货事实完整承接后才做本轮合同/发货引用/包件归集；A有COM临时目录与UPDATE JOIN收尾；预览不运行后置业务UPDATE。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

当前P15-local-owner：本地合同Owner输入及完整P24前置，COM完成实际公司/部门/父合同回填和逐字段汇总；尚无这份新候选真实全量验收。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| createScope | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:4](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:4) |
| createCompanies | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:9](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:9) |
| createDepartments | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:10](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:10) |
| createUniqueContracts | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:17](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:17) |
| updateContractCompanies | update | 是 | [DppmsContractCompletionMapper.xml:91](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:91) |
| updateShipmentReferences | update | 是 | [DppmsContractCompletionMapper.xml:97](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:97) |
| updatePackageReferences | update | 是 | [DppmsContractCompletionMapper.xml:114](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:114) |
| createShipmentFields | runtime_temp_ddl | 是 | [DppmsContractCompletionMapper.xml:120](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:120) |
| createReceivableFields | runtime_temp_ddl | 是 | [DppmsContractCompletionMapper.xml:136](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:136) |
| updateContractFields | update | 是 | [DppmsContractCompletionMapper.xml:170](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:170) |
| dropReceivableFields | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:233](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:233) |
| dropShipmentFields | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:234](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:234) |
| dropUniqueContracts | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:235](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:235) |
| dropDepartments | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:236](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:236) |
| dropCompanies | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:237](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:237) |
| dropScope | runtime_temp_ddl | 否 | [DppmsContractCompletionMapper.xml:238](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:238) |
| generic_update_CONTRACT | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |
| generic_update_SHIPMENT_CONTRACT | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |
| generic_update_PACKAGE | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

GenericDirectTaskLine专用五阶段流式；完整旧预览3848169，814个合同客户冲突；不是A新本地Owner候选的验证。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| generic_update_CONTRACT | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |
| generic_update_SHIPMENT_CONTRACT | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |
| generic_update_PACKAGE | generic_update_template | 否 | [GenericSyncJdbcStore.java:132](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

五阶段原事实完整读取；只读预览的合同阶段从三份单表查询按既有字段优先级归并，真实执行继续读正式输入。运输字典每运行一次；发货事件去掉逐行关联，仅投影包裹3列/发货合同2列并在该步骤各加载一次，条码仍逐批释放，匹配为精确原键且不增加SQL嵌套；双空发货合同按新裁决在来源查询过滤，客户按公司＋合同内订单类型选择对应最大订单号，正向优先，仅退货时取最大退货订单，客户编码及名称来自同一选定订单；同类型最大号内仍有客户冲突则阻断。

[本轮选客裁决与真实预览](../base-sync-p15-order-type-customer-20261019.md)。不是所有分支都已在真实运行到达；缺引用分支另有原生检查。

| 前后置入口 | 逻辑 | 原件 |
|---|---|---|
| createParameters | 运行内固定少量目录/参数。 | [createParameters](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:22) |
| work | 按上述预览与实际写入边界编排。 | [work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:27) |
| syncDataQuery | 执行实际来源查询，保持既定容量及范围。 | [syncDataQuery](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:39) |
| syncDataQueryByRowHandler | 发货事件单表流式读取；两个最小来源投影各查一次，按精确原键补入原发货时间/合同号，保留原LEFT JOIN缺关联语义。 | [syncDataQueryByRowHandler](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:72) |
| relationshipTime | 按原VARCHAR日期精确解析LocalDateTime，拒绝超过目标毫秒精度的有效位，不修改原日期。 | [relationshipTime](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:123) |
| syncDataInsertBefore | 映射/合并并核验当前目标、授权、身份与不可变历史；预览缺引用登记见本轮报告。 | [syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:86) |
| syncDataCount | 待核验的缺引用条目计入read，不虚报插入/未变。 | [syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:142) |
| syncDataInsert | 仅实际执行进入批量DML/原Owner写入；预览不写业务。 | [syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:143) |
| syncDataInsertAfter | 仅实际执行保存独立来源绑定，保留指纹/原身份保护。 | [syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:144) |

实际SQL及完整动态节点见[C-base-sync.sql](C-base-sync.sql)、[C-base-sync-mappings.json](C-base-sync-mappings.json)。本次没有新增多表关联UPDATE；不能把Mapper声明当作已执行。
