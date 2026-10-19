# P20 项目真实产品

最新原用户名/原ID规则及回执：[本项结果](../base-sync-original-identifiers-preview-20261019.md#p20)。此前阶段判断和运行原件保留。

2026-10-19当前更新：[本次真实预览仍受外部前置阻断，依据及Run](../base-sync-13-failures-followup-20261019.md#p20)。原历史回执保留。

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p20)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P20。原组件不再增设独立任务。

## 业务主键与原身份

原真实产品行id；项目清单按正式project_id及逐原行来源绑定，完整清单不可用产品编码去重。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P20:PREVIEW_READY/7818 | 当前候选没有真实同步证据；不继承其他实现结果 |
| 独立直写任务 | PREVIEW_READY / 7818 / ISSUE=7818 | 未找到本实现真实同步完成证据；预览不计同步 |
| 新虚基类套件 | PREVIEW_READY / 7818 | 未找到本实现真实同步完成证据；预览不计同步 |

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

前置：有效规范项目和唯一上游SMS原行精确对应；数量、审计、现存完整清单保护。

后置：保存正式真实产品清单及上游来源事实；保留原业务码，可空产品ID留空，不产生设备分配。

### 原通用引擎 / Owner适配器

ProjectRealProduct Owner及原线范围。

当前入口：`DPPMS_PROJECT_REAL_PRODUCT`。

定义来源：[P20.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P20.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P20 | 2110439264646631425 | 2110447956364398593 | PREVIEW_READY | 7818 | {"ISSUE": 7818}  |

回执来源：[preview-repair-20261015.json](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| ProjectMemberHistoryImportServiceTest | 14 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-022944.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-022944.log) |
| ProjectMemberSourceHistoryServiceTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-023457.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-023457.log) |
| ProjectAssociatedDataServiceTest | 5 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-192821.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-192821.log) |
| DppmsProjectRealProductSyncAdapterTest | 4 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-201855.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-201855.log) |
| ProjectDeviceSelectionContextApiImplTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-205005.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-205005.log) |
| ProjectMasterMigrationServiceTest | 28 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-015733.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-015733.log) |
| ProjectMasterMigrationMySqlTest | 6 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-015733.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-015733.log) |
| ProjectLeaseProductOwnerMySqlTest | 6 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-024833.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-024833.log) |
| ProjectProductConfigurationOwnerMySqlTest | 7 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-024833.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-024833.log) |
| ProjectRealProductOwnerMySqlTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-024833.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-024833.log) |

真实前后置方法及实现位置：

- [DppmsProjectRealProductSyncAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectRealProductSyncAdapter.java:31)
- [DppmsProjectRealProductSyncAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectRealProductSyncAdapter.java:33)
- [DppmsProjectRealProductSyncAdapter.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectRealProductSyncAdapter.java:34)
- [MigrationPreviewService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/migration/MigrationPreviewService.java:54)
- [PlatformMigrationEvidenceApiImpl.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/migration/PlatformMigrationEvidenceApiImpl.java:25)
- [PlatformMigrationEvidenceApiImpl.java::completeReconciliation](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/migration/PlatformMigrationEvidenceApiImpl.java:80)
- [ProjectRealProductMigrationService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/migration/ProjectRealProductMigrationService.java:16)
- [ProjectRealProductMigrationService.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/migration/ProjectRealProductMigrationService.java:18)
- [ProjectRealProductMigrationService.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/migration/ProjectRealProductMigrationService.java:19)
- [ProjectMasterMigrationService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectMasterMigrationService.java:57)
- [ProjectMasterMigrationService.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectMasterMigrationService.java:60)
- [ProjectMasterMigrationService.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectMasterMigrationService.java:112)
- [ProjectRealProductImportService.java::prepareSources](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectRealProductImportService.java:41)
- [ProjectRealProductImportService.java::prepare](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectRealProductImportService.java:86)
- [ProjectRealProductImportService.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectRealProductImportService.java:140)
- [ProjectRealProductImportService.java::validateCurrent](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectRealProductImportService.java:161)
- [ProjectRealProductImportService.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectRealProductImportService.java:208)
- [ProjectTreeScopeService.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java:271)
- [ProjectTreeScopeService.java::validateActorAction](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java:283)
- [ProjectTreeScopeService.java::validateCurrent](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java:293)
- [ProjectTreeScopeService.java::validateRevalidation](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java:303)
- [ProjectTreeProjectionService.java::validateMove](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttree/ProjectTreeProjectionService.java:207)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 独立直写任务

DirectRealProductMapper，7818条预览全为缺正式项目ISSUE。

当前入口：`DPPMS_PROJECT_REAL_PRODUCT_DIRECT`。

定义来源：[tasks-before.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P20 | 2111096225740804097 | 2111096225971490818 | PREVIEW_READY | 7818 | {"ISSUE": 7818}  |

回执来源：[owned-only-status.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| ReviewedDirectConfigurationTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-230420.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-230420.log) |
| DirectProjectPlanTest | 28 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-040851.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-040851.log) |
| DirectProjectTransactionTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-041051.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-041051.log) |
| DirectRealProductTransactionTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-042817.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-042817.log) |
| DirectMemberHistoryTransactionTest | 4 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-043355.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-043355.log) |

真实前后置方法及实现位置：

- [DirectRealProductPersistence.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectRealProductPersistence.java:18)
- [DirectRealProductPersistence.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectRealProductPersistence.java:20)
- [DirectRealProductPersistence.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectRealProductPersistence.java:21)
- [DirectRealProductPreparation.java::prepareSources](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectRealProductPreparation.java:37)
- [DirectRealProductPreparation.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectRealProductPreparation.java:83)
- [DirectRealProductPreparation.java::validateCurrent](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectRealProductPreparation.java:104)
- [DirectRealProductPreparation.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectRealProductPreparation.java:144)
- [DppmsDirectRealProductTask.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectRealProductTask.java:37)
- [DppmsDirectRealProductTask.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectRealProductTask.java:39)
- [DppmsDirectRealProductTask.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectRealProductTask.java:40)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 新虚基类套件

当前采用原用户名及原项目ID规则，最新回执：[P20-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-p22-20261019/P20-preview.json)。

完整清单显式规划，必要project_id必须正式；当前在正式项目来源身份前置处FAILED。

当前入口：`BASE_P20`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P20 | 2111504298947014658 | 2112059695747514370 | PREVIEW_READY | 7818 | SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED |
| P20 | 2111504298947014658 | 2112051575306493953 | FAILED | 0 | FORMAL_PROJECT_SOURCE_IDENTITY_MISSING:22945,24284,28998,27490,26918,29967,36237,33718,33382,34863,34779,36979 |
| P20 | — | 2111504299144146946 | FAILED | 0 | {"INSERTED": 0, "UPDATED": 0, "UNCHANGED": 0} FORMAL_PROJECT_SOURCE_IDENTITY_MISSING:22945,24284,28998,27490,26918,29967,36237,33718,33382,34863,34779,36979 |

最新回执：[P20-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P20-current-preview.json)；其余表格行保留初次汇总时的历史证据。

回执来源：[P20-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-next13-20261017/P20-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| RemainingTaskMySqlTest | 7 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |

真实前后置方法及实现位置：

- [P20RealProductSyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P20RealProductSyncTask.java:19)
- [P20RealProductSyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P20RealProductSyncTask.java:20)
- [P20RealProductSyncTask.java::syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P20RealProductSyncTask.java:53)
- [P20RealProductSyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P20RealProductSyncTask.java:54)
- [P20RealProductSyncTask.java::syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P20RealProductSyncTask.java:55)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| pm_project_product_line_real | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| proj_project_real_product | id | PRIMARY(id) |
