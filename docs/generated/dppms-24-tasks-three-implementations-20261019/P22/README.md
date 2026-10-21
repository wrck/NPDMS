# P22 项目产品实施范围

2026-10-20最新：[P22绑定键修复、结构前置及真实复验](../base-sync-execution-prerequisites-p33-sn-20261020.md)。独立P14明细绑定哈希改用已审line_type，真实父头仍按order_type核对；同租户/连接/新任务/正式完成运行及本次绑定范围保护保持。28项相关原生检查通过。新Run2112437662510071809 PREVIEW_READY/read343858，候选插入340882仅为预览。空部门保留原值、两个ID先-1及零数量处置沿用审核；正式物理约束差异仍登记，本轮未执行正式回填。

2026-10-19阶段结果（历史）以[订单行延后及原部门编码报告](../base-sync-p22-order-line-preview-20261019.md)和[当时XML](../base-sync-p22-order-line-preview-20261019.sql)为据：p.column001原查询直接带入；department_id/order_line_id均先-1，后处理仅限当前独立绑定；正式订单缺失不阻断读源。当时真实预览缺原部门编码，正数量14293条；2976条零数量按已有裁决待处理。后续原空部门保留的已决处理及当前回执见上文。

本轮最新：[base-sync-department-code-preview-20261019](../base-sync-department-code-preview-20261019.md)，本项真实回执[P22-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-deferred-extensions-20261019/P22-preview.json)。此前阶段判断及回执保留。

最新原用户名/原ID规则及回执：[本项结果](../base-sync-original-identifiers-preview-20261019.md#p22)。此前阶段判断和运行原件保留。

2026-10-19当前更新：[本次真实预览仍受外部前置阻断，依据及Run](../base-sync-13-failures-followup-20261019.md#p22)。原历史回执保留。

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p22)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P22。原组件不再增设独立任务。

## 业务主键与原身份

原实施范围id/数量明细id；正式project_id + 合同/订单行/办事处来源业务键及必要范围父关联。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P22:PREVIEW_READY/343858 | 当前候选没有真实同步证据；不继承其他实现结果 |
| 独立直写任务 | PREVIEW_READY / 343858 / ISSUE=343858 | 未找到本实现真实同步完成证据；预览不计同步 |
| 新虚基类套件 | FAILED / 0 | 未找到本实现真实同步完成证据；预览不计同步 |

初次汇总仅引用既有证据；2026-10-19后续已重跑本项真实API预览，新P21另有实现修复和定向测试，详见页首跟进。未做真实同步。A的当前候选与早期回执不是相同配置版本；B/C也不相互替代。类级共同测试不能写成每任务全部用例都已通过。

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

C当前前置：原项目身份保护、原部门编码/租户目录、原数量精度及授权约束；正式订单行先-1，后置按业务键及独立绑定处理。A/B历史前置见各方式段落。

后置：范围及数量明细同事务，必要父ID真实生成；不确认分配、消耗或提升当前范围版本。

### 原通用引擎 / Owner适配器

原ProjectProductScope Owner及完整已发布范围。

当前入口：`DPPMS_PROJECT_PRODUCT_SCOPE`。

定义来源：[P22.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P22.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P22 | 2110439265175113729 | 2110448100925280258 | PREVIEW_READY | 343858 | {"ISSUE": 343858}  |

回执来源：[preview-repair-20261015.json](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| ProjectMemberHistoryImportServiceTest | 14 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-022944.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-022944.log) |
| ProjectMemberSourceHistoryServiceTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-023457.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-023457.log) |
| ProjectAssociatedDataServiceTest | 5 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-192821.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-192821.log) |
| DppmsProjectProductScopeSyncAdapterTest | 7 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-200134.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-200134.log) |
| ProjectDeviceSelectionContextApiImplTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-205005.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-205005.log) |
| DppmsProductScopeMapperMySqlTest | 6 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-115410.log](D:/CodexTaskArtifacts/project-detail-followup/contract-relation-restore-20261014/test-115410.log) |
| ProjectMasterMigrationServiceTest | 28 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-015733.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-015733.log) |
| ProjectMasterMigrationMySqlTest | 6 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-015733.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-015733.log) |
| ProjectLeaseProductOwnerMySqlTest | 6 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-024833.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-024833.log) |
| ProjectProductConfigurationOwnerMySqlTest | 7 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-024833.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-024833.log) |
| ProjectRealProductOwnerMySqlTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-024833.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-024833.log) |
| DppmsProjectProductScopeServiceTest | 19 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-033853.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-033853.log) |
| CommerceDeliveryScopeQueryServiceTest | 4 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-220527.log](D:/CodexTaskArtifacts/project-detail-followup/typecheck-repair-20261014/targeted-220527.log) |

真实前后置方法及实现位置：

- [DppmsProjectProductScopeService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/DppmsProjectProductScopeService.java:58)
- [DppmsProjectProductScopeService.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/DppmsProjectProductScopeService.java:60)
- [DppmsProjectProductScopeService.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/DppmsProjectProductScopeService.java:61)
- [DppmsProjectProductScopeService.java::fields](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/DppmsProjectProductScopeService.java:249)
- [DppmsProjectProductScopeSyncAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectProductScopeSyncAdapter.java:37)
- [DppmsProjectProductScopeSyncAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectProductScopeSyncAdapter.java:39)
- [DppmsProjectProductScopeSyncAdapter.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectProductScopeSyncAdapter.java:40)
- [ProjectMasterMigrationService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectMasterMigrationService.java:57)
- [ProjectMasterMigrationService.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectMasterMigrationService.java:60)
- [ProjectMasterMigrationService.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectMasterMigrationService.java:112)
- [ProjectTreeScopeService.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java:271)
- [ProjectTreeScopeService.java::validateActorAction](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java:283)
- [ProjectTreeScopeService.java::validateCurrent](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java:293)
- [ProjectTreeScopeService.java::validateRevalidation](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java:303)
- [ProjectTreeProjectionService.java::validateMove](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttree/ProjectTreeProjectionService.java:207)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 独立直写任务

DirectProductScopeMapper；343858条全为ISSUE。

当前入口：`DPPMS_PROJECT_PRODUCT_SCOPE_DIRECT`。

定义来源：[tasks-before.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P22 | 2111096327310069761 | 2111115031976505346 | PREVIEW_READY | 343858 | {"ISSUE": 343858}  |

回执来源：[owned-only-status.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| DirectProductScopePlanTest | 17 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-041842.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-041842.log) |
| DirectProductScopeTransactionTest | 4 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-042337.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-042337.log) |

真实前后置方法及实现位置：

- [DirectProductScopePersistence.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectProductScopePersistence.java:24)
- [DirectProductScopePersistence.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectProductScopePersistence.java:25)
- [DirectProductScopePersistence.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectProductScopePersistence.java:26)
- [DirectProductScopePersistence.java::fields](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectProductScopePersistence.java:218)
- [DppmsDirectProductScopeTask.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectProductScopeTask.java:43)
- [DppmsDirectProductScopeTask.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectProductScopeTask.java:45)
- [DppmsDirectProductScopeTask.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectProductScopeTask.java:46)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 新虚基类套件

当前采用原用户名及原项目ID规则，最新回执：[P22-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-p22-20261019/P22-preview.json)。

独立Scope及明细批写；正式项目来源身份缺失，后续合同/行/版本尚未到达验证。

当前入口：`BASE_P22`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P22 | 2111504342123180033 | 2112071134759530497 | FAILED | 0 | P22_ASSOCIATED_ORDER_LINE_NOT_UNIQUE:1 |
| P22 | 2111504342123180033 | 2112059747408756737 | FAILED | 0 | P22_PROJECT_OFFICE_UNRESOLVED |
| P22 | 2111504342123180033 | 2112051622005874690 | FAILED | 0 | FORMAL_PROJECT_SOURCE_IDENTITY_MISSING:37400,38882,38883,41315,41931,41953,41954,42167,42500,42878,42879,42880 |
| P22 | — | 2111504342265786369 | FAILED | 0 | {"INSERTED": 0, "UPDATED": 0, "UNCHANGED": 0} FORMAL_PROJECT_SOURCE_IDENTITY_MISSING:37400,38882,38883,41315,41931,41953,41954,42167,42500,42878,42879,42880 |

最新回执：[P22-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P22-current-preview.json)；其余表格行保留初次汇总时的历史证据。

回执来源：[P22-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-next13-20261017/P22-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

未定位到可明确关联本方式的单任务已执行测试类回执；共享框架/原生SQL检查单列于总报告，不当作该任务写入验收。

真实前后置方法及实现位置：

- [P22ProductScopeSyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P22ProductScopeSyncTask.java:21)
- [P22ProductScopeSyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P22ProductScopeSyncTask.java:22)
- [P22ProductScopeSyncTask.java::syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P22ProductScopeSyncTask.java:76)
- [P22ProductScopeSyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P22ProductScopeSyncTask.java:77)
- [P22ProductScopeSyncTask.java::syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P22ProductScopeSyncTask.java:78)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| pm_project_product_line | 盘点未给出稳定PK | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| com_delivery_scope | id | PRIMARY(id); uk_project_order_line_scope_tenant_row(tenant_id,id); uk_scope_current(tenant_id,project_id,current_order_line_id); uk_scope_version(tenant_id,<表达式索引；快照未记录表达式>,project_id,allocation_version) |
| com_delivery_scope_detail | id | PRIMARY(id); uk_delivery_scope_detail_sequence(tenant_id,delivery_scope_id,detail_sequence); uk_delivery_scope_detail_tenant_row(tenant_id,id) |
