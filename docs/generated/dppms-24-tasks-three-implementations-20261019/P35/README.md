# P35 软件变更批次与版本历史

2026-10-19最新：仅新BASE_P35容量获准提升到500万后，Run `2112116707562823682`全量1586578条PREVIEW_READY（16821批次＋1569757观察）；拓展属性仍暂缓，业务写入0。[本轮证明](../base-sync-seven-blockers-followup-20261019.md)。下方原失败和A/B回执保留历史。

本轮最新：[base-sync-department-code-preview-20261019](../base-sync-department-code-preview-20261019.md)，本项真实回执[P35-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-deferred-extensions-20261019/P35-preview.json)。此前阶段判断及回执保留。

最新原用户名/原ID规则及回执：[本项结果](../base-sync-original-identifiers-preview-20261019.md#p35)。此前阶段判断和运行原件保留。

2026-10-19当前更新：[本次真实预览仍受外部前置阻断，依据及Run](../base-sync-13-failures-followup-20261019.md#p35)。原历史回执保留。

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p35)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P35, P36。原组件不再增设独立任务。

## 业务主键与原身份

原批次id和原版本观察id分别追溯；logId是版本历史到批次的必要父关联，同一正式项目内解析。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P35:NOT_PREVIEWED | 当前候选没有真实同步证据；不继承其他实现结果 |
| 独立直写任务 | FAILED / 0 | 未找到本实现真实同步完成证据；预览不计同步 |
| 新虚基类套件 | PREVIEW_READY / 1,586,578 | 未找到本实现真实同步完成证据；预览不计同步 |

初次汇总仅引用既有证据；2026-10-19后续已重跑本项真实API预览，新P21另有实现修复和定向测试，详见页首跟进。未做真实同步。A的当前候选与早期回执不是相同配置版本；B/C也不相互替代。类级共同测试不能写成每任务全部用例都已通过。

## 浏览器验证范围

| 方式 | 已有浏览器结果与边界 | 原证据 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 未将历史页面或其他路线页面作为当前A候选的浏览器验收；当前候选专属业务UI验收未证明。 | 未定位到当前候选/最新Run专属证明 |
| 独立直写任务 | 环境恢复后曾逐页查看既有失败运行详情，确认失败与预览未写入；不是全部任务重新预览或业务闭环完成。 | [browser-nine-result.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/environment-restored-20261017/browser-nine-result.json) |
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

前置：完整项目范围、原时间/标志精度及AST/SoftwareVersionRecord八属性正式定义；原_bak/_change值不可丢弃。

后置：先批次、后版本观察；同次父ID真实；八属性通过正式扩展初始化，失败回滚两层；可空设备ID留空。

### 原通用引擎 / Owner适配器

原组合Owner；没有正式八属性定义及当前候选真实运行。

当前入口：`DPPMS_SOFTWARE_HISTORY`。

定义来源：[P35.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P35.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P35 | — | — | NOT_PREVIEWED | — | {}  |

回执来源：[preview-repair-20261015.json](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| ProjectMemberHistoryImportServiceTest | 14 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-022944.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-022944.log) |
| ProjectMemberSourceHistoryServiceTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-023457.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-023457.log) |
| DppmsAssetSourceHistoryServiceTest | 16 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-024530.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-024530.log) |
| SoftwareVersionLegacyExtensionPreviewTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-024530.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-024530.log) |
| DppmsSoftwareHistoryCorrectionServiceTest | 8 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-024530.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-024530.log) |
| DppmsLicenseRecordServiceTest | 7 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-025614.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-025614.log) |
| ProjectAssociatedDataServiceTest | 5 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-192821.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-192821.log) |
| ProjectDeviceSelectionContextApiImplTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-205005.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-205005.log) |
| DppmsDeviceBootstrapServiceTest | 6 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-205447.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-205447.log) |
| DppmsFactoryHistoryMigrationServiceTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-205005.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-205005.log) |
| ProjectMasterMigrationServiceTest | 28 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-015733.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-015733.log) |
| ProjectMasterMigrationMySqlTest | 6 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-015733.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-015733.log) |
| ProjectLeaseProductOwnerMySqlTest | 6 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-024833.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-024833.log) |
| ProjectProductConfigurationOwnerMySqlTest | 7 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-024833.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-024833.log) |
| ProjectRealProductOwnerMySqlTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-024833.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-024833.log) |
| DeviceWarrantyRenewalQueryServiceTest | 4 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-220454.log](D:/CodexTaskArtifacts/project-detail-followup/renewal-20261013/test-220454.log) |
| PlatformMigrationBulkWriterRoutingTest | 3 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-212903.log](D:/CodexTaskArtifacts/project-detail-followup/sync-optimization-merge-20261014/targeted-212903.log) |
| PlatformMigrationEvidenceMapperContractTest | 6 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-212903.log](D:/CodexTaskArtifacts/project-detail-followup/sync-optimization-merge-20261014/targeted-212903.log) |
| PlatformMigrationEvidenceTransactionExecutorTest | 5 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-212903.log](D:/CodexTaskArtifacts/project-detail-followup/sync-optimization-merge-20261014/targeted-212903.log) |
| PlatformMigrationStagingCountTest | 6 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-212903.log](D:/CodexTaskArtifacts/project-detail-followup/sync-optimization-merge-20261014/targeted-212903.log) |

真实前后置方法及实现位置：

- [DeviceProjectAssignmentService.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/assignment/DeviceProjectAssignmentService.java:157)
- [DppmsAssetSourceHistoryService.java::executeSourceHistory](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/migration/DppmsAssetSourceHistoryService.java:45)
- [DppmsSoftwareHistoryCorrectionService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/migration/DppmsSoftwareHistoryCorrectionService.java:29)
- [DppmsSoftwareHistoryCorrectionService.java::authorize](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/migration/DppmsSoftwareHistoryCorrectionService.java:84)
- [DppmsSoftwareHistoryCorrectionService.java::validateRecordSource](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/migration/DppmsSoftwareHistoryCorrectionService.java:93)
- [DppmsSoftwareHistoryCorrectionService.java::validateBatchSource](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/migration/DppmsSoftwareHistoryCorrectionService.java:106)
- [SoftwareVersionLegacyExtensionService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/version/SoftwareVersionLegacyExtensionService.java:70)
- [SoftwareVersionLegacyExtensionService.java::validateValues](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/version/SoftwareVersionLegacyExtensionService.java:82)
- [MigrationPreviewService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/migration/MigrationPreviewService.java:54)
- [PlatformMigrationEvidenceApiImpl.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/migration/PlatformMigrationEvidenceApiImpl.java:25)
- [PlatformMigrationEvidenceApiImpl.java::completeReconciliation](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/migration/PlatformMigrationEvidenceApiImpl.java:80)
- [ProjectAssociatedDataService.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectAssociatedDataService.java:179)
- [ProjectHeaderFinalCustomerService.java::validateActor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectHeaderFinalCustomerService.java:100)
- [ProjectLeaseProductImportService.java::prepareSources](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectLeaseProductImportService.java:36)
- [ProjectLeaseProductImportService.java::prepare](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectLeaseProductImportService.java:73)
- [ProjectLeaseProductImportService.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectLeaseProductImportService.java:111)
- [ProjectLeaseProductImportService.java::validateCurrent](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectLeaseProductImportService.java:133)
- [ProjectLeaseProductImportService.java::validateCurrent](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectLeaseProductImportService.java:136)
- [ProjectLeaseProductImportService.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectLeaseProductImportService.java:195)
- [ProjectLegacyEntityMigrationService.java::validateActor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectLegacyEntityMigrationService.java:688)
- [ProjectProductConfigurationImportService.java::prepareSources](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectProductConfigurationImportService.java:36)
- [ProjectProductConfigurationImportService.java::prepare](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectProductConfigurationImportService.java:73)
- [ProjectProductConfigurationImportService.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectProductConfigurationImportService.java:111)
- [ProjectProductConfigurationImportService.java::validateCurrent](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectProductConfigurationImportService.java:133)
- [ProjectProductConfigurationImportService.java::validateCurrent](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectProductConfigurationImportService.java:136)
- [ProjectProductConfigurationImportService.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectProductConfigurationImportService.java:191)
- [ProjectRealProductImportService.java::prepareSources](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectRealProductImportService.java:41)
- [ProjectRealProductImportService.java::prepare](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectRealProductImportService.java:86)
- [ProjectRealProductImportService.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectRealProductImportService.java:140)
- [ProjectRealProductImportService.java::validateCurrent](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectRealProductImportService.java:161)
- [ProjectRealProductImportService.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectRealProductImportService.java:208)
- [ProjectSoftwareHistoryCorrectionService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectSoftwareHistoryCorrectionService.java:31)
- [ProjectSoftwareHistoryCorrectionService.java::prepare](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectSoftwareHistoryCorrectionService.java:61)
- [ProjectSourceAttributesService.java::validateActor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectSourceAttributesService.java:74)
- [ProjectSourceAttributesService.java::validateProject](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectSourceAttributesService.java:79)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 独立直写任务

DirectSoftwareBatch/History Mapper；仍在旧发布范围前置失败。

当前入口：`DPPMS_SOFTWARE_HISTORY_DIRECT`。

定义来源：[tasks-before.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P35 | 2111097541141647362 | 2111097541389111297 | FAILED | 0 | {} 执行失败（BadSqlGrammarException），请检查连接、容量和服务状态；凭据及原始SQL不写入日志 |

回执来源：[owned-only-status.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| ReviewedDirectConfigurationTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-230420.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-230420.log) |
| DirectDeviceBaseTransactionTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-043123.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-043123.log) |
| DirectSoftwareHistoryTransactionTest | 4 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-043905.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-043905.log) |

真实前后置方法及实现位置：

- [DirectSoftwareHistoryPersistence.java::executeSourceHistory](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectSoftwareHistoryPersistence.java:39)
- [DppmsDirectSoftwareHistoryTask.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectSoftwareHistoryTask.java:37)
- [DppmsDirectSoftwareHistoryTask.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectSoftwareHistoryTask.java:38)
- [DppmsDirectSoftwareHistoryTask.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectSoftwareHistoryTask.java:39)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 新虚基类套件

当前采用原用户名及原项目ID规则，最新回执：[P35-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-20261019/P35-preview.json)。

仅新P16完整范围；尚无该输入及软件历史定义ID；不自动发布或借其他实体定义。

当前入口：`BASE_P35`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P35 | 2111504369105137665 | 2112071158990024705 | FAILED | 0 | PUBLISHED_PROJECT_CURRENT_IDENTITY_CHANGED |
| P35 | 2111504369105137665 | 2112057745329045506 | FAILED | 0 | P35_FORMAL_AST_SoftwareVersionRecord_EIGHT_ATTRIBUTE_DEFINITION_REQUIRED |
| P35 | 2111504369105137665 | 2112051644671893506 | FAILED | 0 | COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED |
| P35 | — | 2111517889595088898 | FAILED | 0 | {"INSERTED": 0, "UPDATED": 0, "UNCHANGED": 0} COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED |

最新回执：[P35-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P35-current-preview.json)；其余表格行保留初次汇总时的历史证据。

回执来源：[P35-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018-wave9/P35-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| NewHistoryTaskMySqlTest | 7 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |

真实前后置方法及实现位置：

- [P35SoftwareHistorySyncTask.java::createParameters](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P35SoftwareHistorySyncTask.java:21)
- [P35SoftwareHistorySyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P35SoftwareHistorySyncTask.java:24)
- [P35SoftwareHistorySyncTask.java::syncDataQueryByRowHandler](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P35SoftwareHistorySyncTask.java:30)
- [P35SoftwareHistorySyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P35SoftwareHistorySyncTask.java:31)
- [P35SoftwareHistorySyncTask.java::syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P35SoftwareHistorySyncTask.java:58)
- [P35SoftwareHistorySyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P35SoftwareHistorySyncTask.java:59)
- [P35SoftwareHistorySyncTask.java::syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P35SoftwareHistorySyncTask.java:60)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| pm_project_soft_change_logs | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| pm_project_soft_version | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| ast_device_software_change_batch | id | PRIMARY(id) |
| ast_device_software_version_record | id | PRIMARY(id); uk_software_parsed_fact(tenant_id,parsed_fact_id); uk_software_request(tenant_id,request_key) |
| plt_entity_extension_value | id | PRIMARY(id); uk_entity_extension_target(tenant_id,owner_module,entity_type,entity_id,revision_id) |
