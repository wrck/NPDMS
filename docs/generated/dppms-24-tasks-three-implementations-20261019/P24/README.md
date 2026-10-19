# P24 合同实际回款

最新原用户名/原ID规则及回执：[公共审计变更后81547条重验通过](../base-sync-original-identifiers-preview-20261019.md)。此前阶段判断和运行原件保留。

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p24)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P24。原组件不再增设独立任务。

## 业务主键与原身份

原回款主键id；正式tenant_id + source_system + source_record_key；contract_id只在合同号全租户精确唯一时补入。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P24:PREVIEW_READY/79182 | 当前候选没有真实同步证据；不继承其他实现结果 |
| 独立直写任务 | PREVIEW_READY / 79182 | 未找到本实现真实同步完成证据；预览不计同步 |
| 新虚基类套件 | PREVIEW_READY / 81547 | 未找到本实现真实同步完成证据；预览不计同步 |

验证仅引用既有证据，本轮没有重跑测试/预览/同步。A的当前候选与早期回执不是相同配置版本；B/C也不相互替代。类级共同测试不能写成每任务全部用例都已通过。

## 浏览器验证范围

| 方式 | 已有浏览器结果与边界 | 原证据 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 未将历史页面或其他路线页面作为当前A候选的浏览器验收；当前候选专属业务UI验收未证明。 | 未定位到当前候选/最新Run专属证明 |
| 独立直写任务 | 未定位到可明确关联最新Run的专属详情浏览器验收回执；真实API/数据库终态单列，不以任务列表代替业务闭环。 | 未定位到当前候选/最新Run专属证明 |
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

前置：原39字段全部承接；7条空合同号沿已决排除；原时间/金额精度；审计引用和现存正式字段/前值保护。

后置：每批合同号一次集合匹配，空/多义保持NULL且不清旧引用；不猜公司、不改合同金额事实。

### 原通用引擎 / Owner适配器

原Owner已批量合同查询；真实回填和当前候选全量未验收。

当前入口：`DPPMS_CONTRACT_RECEIVABLE`。

定义来源：[P24.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P24.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P24 | 2110439265737150465 | 2110453111663112194 | PREVIEW_READY | 79182 | {"CREATED": 79182}  |

回执来源：[preview-repair-20261015.json](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| ContractReceivableMigrationServiceTest | 8 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-030845.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-030845.log) |
| DppmsContractReceivableSyncAdapterTest | 2 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-202229.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-202229.log) |
| ContractReceivableMigrationMySqlTest | 3 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-022928.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-022928.log) |

真实前后置方法及实现位置：

- [ContractReceivableMigrationService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/ContractReceivableMigrationService.java:37)
- [ContractReceivableMigrationService.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/ContractReceivableMigrationService.java:39)
- [ContractReceivableMigrationService.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/ContractReceivableMigrationService.java:40)
- [DppmsContractReceivableSyncAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsContractReceivableSyncAdapter.java:30)
- [DppmsContractReceivableSyncAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsContractReceivableSyncAdapter.java:32)
- [DppmsContractReceivableSyncAdapter.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsContractReceivableSyncAdapter.java:33)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 独立直写任务

DirectReceivableMapper；已有79182条预览。

当前入口：`DPPMS_CONTRACT_RECEIVABLE_DIRECT`。

定义来源：[tasks-before.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P24 | 2111096381534031875 | 2111116168024715266 | PREVIEW_READY | 79182 | {"CREATED": 79182}  |

回执来源：[owned-only-status.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| DirectReceivableTransactionTest | 4 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-035529.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-035529.log) |

真实前后置方法及实现位置：

- [DirectReceivablePersistence.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectReceivablePersistence.java:38)
- [DirectReceivablePersistence.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectReceivablePersistence.java:40)
- [DirectReceivablePersistence.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectReceivablePersistence.java:41)
- [DppmsDirectReceivableTask.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectReceivableTask.java:37)
- [DppmsDirectReceivableTask.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectReceivableTask.java:39)
- [DppmsDirectReceivableTask.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectReceivableTask.java:40)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 新虚基类套件

当前采用原用户名及原项目ID规则，最新回执：[P24-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-20261019/P24-preview.json)。

81547原行读取，7条排除；目录每运行一次，合同查询分段避免SQL解析栈溢出；执行结果才供新P15正式输入。

当前入口：`BASE_P24`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P24 | 2111463422476607489 | 2112057587128287234 | PREVIEW_READY | 81547 | None |
| P24 | — | 2111480412981334017 | PREVIEW_READY | 81547 | {"INSERTED": 81540, "UPDATED": 0, "UNCHANGED": 7}  |

回执来源：[P24-wave5-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-remaining-20261017/P24-wave5-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| ConfiguredSyncSourceNativeTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |
| ReceivableBaseSyncTest | 14 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |

真实前后置方法及实现位置：

- [P24ReceivableSyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P24ReceivableSyncTask.java:26)
- [P24ReceivableSyncTask.java::syncDataQueryByRowHandler](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P24ReceivableSyncTask.java:27)
- [P24ReceivableSyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P24ReceivableSyncTask.java:37)
- [P24ReceivableSyncTask.java::syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P24ReceivableSyncTask.java:84)
- [P24ReceivableSyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P24ReceivableSyncTask.java:87)
- [P24ReceivableSyncTask.java::syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P24ReceivableSyncTask.java:91)
- [P24ReceivableSyncTask.java::canonicalDivision](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P24ReceivableSyncTask.java:260)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| sms_ofst_contract_head_sap | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| com_contract_receivable | id | PRIMARY(id); uk_contract_receivable_source(tenant_id,source_system,source_record_key); uk_contract_receivable_tenant_row(tenant_id,id) |
