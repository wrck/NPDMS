# P15 合同、发货引用、包裹、发货事件和设备关系

本轮正式输入依赖、追加裁决与真实终态：[P15](../base-sync-five-input-dependencies-20261019.md#p15)；[当前24项JSON](../base-sync-five-input-dependencies-20261019.json)。下方旧阶段回执保留其历史范围，当前C以本轮记录为准。

较早阶段更新（历史）：[本次真实预览仍受外部前置阻断，依据及Run](../base-sync-13-failures-followup-20261019.md#p15)。原历史回执保留。

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p15)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P15, P26, P27, P28, P29。原组件不再增设独立任务。

## 业务主键与原身份

合同：tenant_id + company_code + contract_no；发货合同：原contract_id；包件：packlist_id；条码事件：原id；设备关系：原id。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P15:PREVIEW_READY/3848169 | 当前候选没有真实同步证据；不继承其他实现结果 |
| 独立直写任务 | PREVIEW_READY / 3848169 / ISSUE=814 | 未找到本实现真实同步完成证据；预览不计同步 |
| 新虚基类套件 | FAILED / 0 | 未找到本实现真实同步完成证据；预览不计同步 |

初次汇总仅引用既有证据；2026-10-19后续已重跑本项真实API预览，新P21另有实现修复和定向测试，详见页首跟进。未做真实同步。A的当前候选与早期回执不是相同配置版本；B/C也不相互替代。类级共同测试不能写成每任务全部用例都已通过。

## 浏览器验证范围

| 方式 | 已有浏览器结果与边界 | 原证据 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 未将历史页面或其他路线页面作为当前A候选的浏览器验收；当前候选专属业务UI验收未证明。 | 未定位到当前候选/最新Run专属证明 |
| 独立直写任务 | 已有完整预览终态详情检查：read3848169、ISSUE814、拟新增3847355，页面提示完整预览未写入。 | [browser-owned-final-only.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/browser-owned-final-only.json) |
| 新虚基类套件 | 已有19084登录首页恢复检查；新套件无专属任务UI。该任务结论来自真实API与数据库终态，不是任务专属UI业务验收。 | [standalone-base-sync-20261017.md](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/standalone-base-sync-20261017.md) |

## 单表字段映射和SQL入口

- [三种实现的业务键及来源键](business-keys.md)
- [三种实现的正式INSERT字段/参数](write-field-bindings.md)
- [前后置流程、实际关联更新清单及新基类钩子原文](processing.md)

- [原表逐字段映射/处置](field-mapping.md)
- [单表审阅投影SQL](single-table-projections.sql)：原值层投影，保留待转换字段，不冒充已实施的INSERT SELECT。
- [当前新基类实际查询别名与INSERT字段](current-base-field-bindings.md)

- [原通用引擎 / Owner适配器：源查询、插入、更新、前置查询和临时表SQL](A-original.sql)；[完整动态XML节点、映射及绑定参数](A-original-mappings.json)
- [独立直写任务：源查询、插入、更新、前置查询和临时表SQL](B-direct.sql)；[完整动态XML节点、映射及绑定参数](B-direct-mappings.json)
- [新虚基类套件：源查询、插入、更新、前置查询和临时表SQL](C-base-sync.sql)；[完整动态XML节点、映射及绑定参数](C-base-sync-mappings.json)

## 前后置总流程

前置：保持合同客户消歧、原父来源键与精确业务码；运输字典每轮一次；正式P24完整输入不能由预览冒充。

后置：原发货事实完整承接后才做本轮合同/发货引用/包件归集；A有COM临时目录与UPDATE JOIN收尾；预览不运行后置业务UPDATE。

### 原通用引擎 / Owner适配器

当前P15-local-owner：本地合同Owner输入及完整P24前置，COM完成实际公司/部门/父合同回填和逐字段汇总；尚无这份新候选真实全量验收。

当前入口：`TABLE_MAPPING`。

定义来源：[P15.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P15.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P15 | 2110439255301722114 | 2110493133556776962 | PREVIEW_READY | 3848169 | {"ISSUE": 814, "CREATED": 3847355}  |

回执来源：[preview-repair-20261015.json](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| GenericCacheRefreshTest | 8 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031357.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031357.log) |
| GenericSyncEngineTest | 57 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-022627.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-022627.log) |
| SyncRunTransactionTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031802.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031802.log) |
| DppmsConsolidatedTemplateTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031117.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031117.log) |
| DppmsContractCompletionServiceTest | 9 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-020327.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-020327.log) |
| SyncRunMaintenanceTest | 9 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-193533.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-193533.log) |
| DppmsLocalContractSourceServiceTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-205447.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-205447.log) |
| SyncBindingRestorationTest | 1 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-213658.log](D:/CodexTaskArtifacts/project-detail-followup/sync-optimization-merge-20261014/targeted-213658.log) |
| DppmsRuntimeConfigurationsTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-runtime-config-recheck.log](D:/CodexTaskArtifacts/project-detail-followup/preview-repair-20261015/final-runtime-config-recheck.log) |

真实前后置方法及实现位置：

- [DppmsContractCompletionService.java::complete](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/DppmsContractCompletionService.java:25)
- [DppmsContractCompletionService.java::authorize](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/DppmsContractCompletionService.java:108)
- [GenericSyncJdbcStore.java::validateValues](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:33)
- [GenericSyncEngine.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:43)
- [GenericSyncEngine.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:173)
- [GenericSyncEngine.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:174)
- [GenericSyncEngine.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:209)
- [GenericSyncEngine.java::afterCommit](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:442)
- [GenericSyncEngine.java::canonical](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:543)

多表关联UPDATE JOIN：已定位 4 条声明，详该方式SQL第4节；声明存在不等于实际执行过。
### 独立直写任务

GenericDirectTaskLine专用五阶段流式；完整旧预览3848169，814个合同客户冲突；不是A新本地Owner候选的验证。

当前入口：`TABLE_MAPPING`。

定义来源：[task-after.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/P15-phase-stream/task-after.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P15 | 2111150344895655938 | 2111163163829530626 | PREVIEW_READY | 3848169 | {"ISSUE": 814, "CREATED": 3847355}  |

回执来源：[owned-only-status.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| GenericCacheRefreshTest | 8 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031357.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031357.log) |
| GenericSyncEngineTest | 57 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-022627.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-022627.log) |
| GenericDirectTaskLineTest | 2 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-030845.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-030845.log) |
| SyncRunTransactionTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031802.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031802.log) |
| DppmsConsolidatedTemplateTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031117.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031117.log) |
| SyncRunMaintenanceTest | 9 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-193533.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-193533.log) |
| SyncBindingRestorationTest | 1 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-213658.log](D:/CodexTaskArtifacts/project-detail-followup/sync-optimization-merge-20261014/targeted-213658.log) |
| DppmsRuntimeConfigurationsTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-runtime-config-recheck.log](D:/CodexTaskArtifacts/project-detail-followup/preview-repair-20261015/final-runtime-config-recheck.log) |

真实前后置方法及实现位置：

- [GenericSyncJdbcStore.java::validateValues](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:33)
- [GenericDirectTaskLine.java::businessKey](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/GenericDirectTaskLine.java:6)
- [GenericSyncEngine.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:43)
- [GenericSyncEngine.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:173)
- [GenericSyncEngine.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:174)
- [GenericSyncEngine.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:209)
- [GenericSyncEngine.java::afterCommit](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:442)
- [GenericSyncEngine.java::canonical](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:543)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 新虚基类套件

五个独立步骤，先原发货再本地合同；缺shipment_contract_source_key、500万配置/结构及新P24正式输入；实际条码4283326。

当前入口：`BASE_P15`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P15 | 2111504249399701506 | 2112051493786001409 | FAILED | 0 | P15_TARGET_COLUMN_MISSING:com_shipment_package.shipment_contract_source_key |
| P15 | — | 2111515580383285249 | FAILED | 0 | {"INSERTED": 0, "UPDATED": 0, "UNCHANGED": 0} P15_TARGET_COLUMN_MISSING:com_shipment_package.shipment_contract_source_key |

最新回执：[P15-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P15-current-preview.json)；其余表格行保留初次汇总时的历史证据。

回执来源：[P15-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/P15-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| NewTaskPreconditionsTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |
| PrerequisiteCompletionMySqlTest | 2 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |

真实前后置方法及实现位置：

- [P15DeliverySyncTask.java::createParameters](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:22)
- [P15DeliverySyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:27)
- [P15DeliverySyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:36)
- [P15DeliverySyncTask.java::syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:84)
- [P15DeliverySyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:85)
- [P15DeliverySyncTask.java::syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java:86)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| fb_contract | 盘点未给出稳定PK | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| fb_shipment | 盘点未给出稳定PK | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| fb_shipment_barcode | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| fb_shipment_barcode_relation | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| pm_order_data_from_erp | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| sms_ofst_contract_head_sap | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| view_shipment_ems_4_pm | 盘点未给出稳定PK | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| com_contract | id | PRIMARY(id); uk_contract_business(tenant_id,company_code,contract_no); uk_contract_master_source(tenant_id,master_source_system,master_source_record_key); uk_contract_tenant_row(tenant_id,id) |
| com_shipment_contract_reference | id | PRIMARY(id); uk_shipment_contract_ref_source(tenant_id,source_system,source_record_key); uk_shipment_contract_ref_tenant_row(tenant_id,id) |
| com_shipment_package | id | PRIMARY(id); uk_shipment_package_no(tenant_id,source_system,package_no); uk_shipment_package_source(tenant_id,source_system,source_record_key); uk_shipment_package_tenant_row(tenant_id,id) |
| ast_device_shipment | id | PRIMARY(id); uk_ast_device_shipment_source(tenant_id,source_system,source_key) |
| ast_device_relationship | id | PRIMARY(id); uk_ast_device_relationship_source(tenant_id,source_system,source_key) |
