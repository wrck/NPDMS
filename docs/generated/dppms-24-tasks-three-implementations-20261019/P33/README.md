# P33 普通安装历史及获准当前位置

2026-10-20最新：[SQL/Java匹配修复、正式前置与真实复验](../base-sync-execution-prerequisites-p33-sn-20261020.md)。SQL保留原请求requested_sn并返回正式设备id/sn，Java按原请求键定位、按正式ID/SN核对当前选择；不TRIM或修改原SN/指纹。7项专项原生检查及17项历史回归通过。新Run2112436849851088898 PREVIEW_READY/read1035329，190.174秒；正式设备缺项继续登记，两条串货暂缓。正式数据尚未具备匹配条件，27条尾空格来源的成功匹配分支由真实MySQL临时夹具验证。下方只读审计及较早回执保留各自范围。

2026-10-20后续SN核查：[完整来源SN、目标匹配SQL和缺失范围](../P33-formal-device-reference-audit-20261020.md)。1,035,329条安装涉及1,018,498个原SN、14,135个原项目，2037批正式查询均无设备命中；首要原因是新P15/P30正式DPPMS发货/设备尚未写入。另单列26个尾空格SN／27条来源的SQL与Java键比较边界、1个内部码对应7个主SN、5个在三个发货SN字段未见原文的身份。本轮只读，没有新任务运行或代码修改，两条串货继续暂缓。

2026-10-20当前结果：已按本轮明确授权，仅在npdms_reverify_20261014将install_location扩为VARCHAR(1024)。新BASE_P33 Run2112346937229496321 PREVIEW_READY/read1035329/写入计划0，容量告警已消除；正式设备引用待就绪和2条暂缓串货继续按既有结论登记，空地址过滤、原文双份承接及UNRESOLVED规则保持。执行收据、原有34条安装全字段保护及真实预览见[容量补丁及最终结果](../P10-P33-capacity-applied-preview-20261020.md)。

此前2026-10-20实现及回执见[三项续办历史结果](../base-sync-P10-P18-P33-followup-20261020.md)，以下2026-10-19内容按原时点保留。

此前逐项核查与真实预览：[base-sync-four-blockers-audit-20261019](../base-sync-four-blockers-audit-20261019.md#p33)。当时新P33为FAILED，原任务及历史失败回执保持。

本轮正式输入依赖、追加裁决与真实终态：[P33](../base-sync-five-input-dependencies-20261019.md#p33)；[当前24项JSON](../base-sync-five-input-dependencies-20261019.json)。下方旧阶段回执保留其历史范围，当前C以本轮记录为准。

最新原用户名/原ID规则及回执：[本项结果](../base-sync-original-identifiers-preview-20261019.md#p33)。此前阶段判断和运行原件保留。

较早阶段更新（历史）：[本次真实预览仍受外部前置阻断，依据及Run](../base-sync-13-failures-followup-20261019.md#p33)。原历史回执保留。

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p33)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P33。原组件不再增设独立任务。

## 业务主键与原身份

原安装记录id；正式安装历史新ID；设备当前记录唯一性/有效期按设备和既定Owner契约保护。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P33:NOT_PREVIEWED | 当前候选没有真实同步证据；不继承其他实现结果 |
| 独立直写任务 | FAILED / 0 | 未找到本实现真实同步完成证据；预览不计同步 |
| 新虚基类套件 | FAILED / 0 | 未找到本实现真实同步完成证据；预览不计同步 |

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

前置：完整项目范围、正式SN设备、项目选择/管理授权；设备不能用原缓存ID猜配。

后置：写历史地址原文及正式不可变地址快照；仅获准首次当前记录建立，不接管现有当前安装；地址API失败整次回滚。

### 原通用引擎 / Owner适配器

原安装Owner和首次迁入/跨批归集；需要正式设备及范围。

当前入口：`DPPMS_INSTALLATION_HISTORY`。

定义来源：[P33.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P33.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P33 | — | — | NOT_PREVIEWED | — | {}  |

回执来源：[preview-repair-20261015.json](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| ProjectMemberHistoryImportServiceTest | 14 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-022944.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-022944.log) |
| ProjectMemberSourceHistoryServiceTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-023457.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-023457.log) |
| DppmsAssetSourceHistoryServiceTest | 16 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-024530.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-024530.log) |
| DppmsSoftwareHistoryCorrectionServiceTest | 8 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-024530.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-024530.log) |
| DppmsLicenseRecordServiceTest | 7 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-025614.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-025614.log) |
| ProjectAssociatedDataServiceTest | 5 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-192821.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-192821.log) |
| ProjectDeviceSelectionContextApiImplTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-205005.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-205005.log) |
| DppmsDeviceBootstrapServiceTest | 6 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-205447.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-205447.log) |
| DppmsFactoryHistoryMigrationServiceTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-205005.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-205005.log) |
| InstallationSourceHistoryMapperTest | 7 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-220210.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-220210.log) |
| InstallationHistoryInsertServiceTest | 7 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-220735.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-220735.log) |
| DppmsInstallationRecordMigrationServiceTest | 8 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-220210.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-220210.log) |
| InstallationFirstImportFlowTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-221456.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-221456.log) |
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

- [AssetLocationApiImpl.java::validateSites](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/location/AssetLocationApiImpl.java:152)
- [AssetLocationApiImpl.java::validateSiteCodeUnique](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/location/AssetLocationApiImpl.java:246)
- [AssetLocationApiImpl.java::validateReplayReferences](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/location/AssetLocationApiImpl.java:265)
- [AssetLocationApiImpl.java::validateVersion](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/location/AssetLocationApiImpl.java:318)
- [AssetLocationApiImpl.java::validateVersion](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/location/AssetLocationApiImpl.java:324)
- [DeviceProjectAssignmentService.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/assignment/DeviceProjectAssignmentService.java:157)
- [DeviceLocationEffectiveService.java::validateCommand](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/location/DeviceLocationEffectiveService.java:110)
- [DeviceLocationEffectiveService.java::validateLocation](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/location/DeviceLocationEffectiveService.java:128)
- [DppmsAssetSourceHistoryService.java::executeSourceHistory](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/migration/DppmsAssetSourceHistoryService.java:45)
- [DppmsInstallationRecordMigrationService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/installation/DppmsInstallationRecordMigrationService.java:28)
- [DppmsInstallationRecordMigrationService.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/installation/DppmsInstallationRecordMigrationService.java:30)
- [DppmsInstallationRecordMigrationService.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/installation/DppmsInstallationRecordMigrationService.java:31)
- [InstallationServiceImpl.java::completeInstallation](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/installation/InstallationServiceImpl.java:123)
- [InstallationServiceImpl.java::validateInstallationExists](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/installation/InstallationServiceImpl.java:156)
- [InstallationServiceImpl.java::validateVersion](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/installation/InstallationServiceImpl.java:164)
- [InstallationServiceImpl.java::validateStatus](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/installation/InstallationServiceImpl.java:170)
- [InstallationServiceImpl.java::validateEffectiveLocation](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/installation/InstallationServiceImpl.java:215)
- [MigrationPreviewService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/migration/MigrationPreviewService.java:54)
- [PlatformMigrationEvidenceApiImpl.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/migration/PlatformMigrationEvidenceApiImpl.java:25)
- [PlatformMigrationEvidenceApiImpl.java::completeReconciliation](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/migration/PlatformMigrationEvidenceApiImpl.java:80)
- [ProjectAssociatedDataService.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectAssociatedDataService.java:179)
- [ProjectHeaderFinalCustomerService.java::validateActor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectHeaderFinalCustomerService.java:100)
- [ProjectInstallationRecordReconciliationService.java::prepare](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectInstallationRecordReconciliationService.java:30)
- [ProjectInstallationRecordReconciliationService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectInstallationRecordReconciliationService.java:63)
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
- [ProjectSourceAttributesService.java::validateActor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectSourceAttributesService.java:74)
- [ProjectSourceAttributesService.java::validateProject](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectSourceAttributesService.java:79)

多表关联UPDATE JOIN：已定位 2 条声明，详该方式SQL第4节；声明存在不等于实际执行过。
### 独立直写任务

DirectInstallationMapper，范围发布前置处失败。

当前入口：`DPPMS_INSTALLATION_HISTORY_DIRECT`。

定义来源：[tasks-before.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P33 | 2111097529066246145 | 2111097529649254402 | FAILED | 0 | {} 执行失败（BadSqlGrammarException），请检查连接、容量和服务状态；凭据及原始SQL不写入日志 |

回执来源：[owned-only-status.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| ReviewedDirectConfigurationTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-230420.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-230420.log) |
| DirectInstallationTransactionTest | 4 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-043708.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-043708.log) |

真实前后置方法及实现位置：

- [DirectInstallationPersistence.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectInstallationPersistence.java:33)
- [DirectInstallationPersistence.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectInstallationPersistence.java:64)
- [DppmsDirectInstallationTask.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectInstallationTask.java:43)
- [DppmsDirectInstallationTask.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectInstallationTask.java:45)
- [DppmsDirectInstallationTask.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectInstallationTask.java:46)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 新虚基类套件

当前采用原用户名及原项目ID规则，最新回执：[P33-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-20261019/P33-preview.json)。

新P16范围；地址与设备选择公共API保留；当前缺新P16正式范围，不伪造预览地址ID。

当前入口：`BASE_P33`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P33 | 2111504368203362306 | 2112057626944815105 | FAILED | 0 | P33_SOURCE_CAPACITY |
| P33 | 2111504368203362306 | 2112051644151799809 | FAILED | 0 | COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED |
| P33 | — | 2111517888991109121 | FAILED | 0 | {"INSERTED": 0, "UPDATED": 0, "UNCHANGED": 0} COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED |

最新回执：[P33-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P33-current-preview.json)；其余表格行保留初次汇总时的历史证据。

回执来源：[P33-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018-wave9/P33-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| NewHistoryTaskMySqlTest | 7 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |

真实前后置方法及实现位置：

- [P33InstallationSyncTask.java::createParameters](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:22)
- [P33InstallationSyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:25)
- [P33InstallationSyncTask.java::syncDataQuery](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:27)
- [P33InstallationSyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:28)
- [P33InstallationSyncTask.java::syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:52)
- [P33InstallationSyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:53)
- [P33InstallationSyncTask.java::syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:59)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| pm_project_shipment | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| imp_eng_installation | id | PRIMARY(id); uk_pms_eng_installation_code(project_id,code); uk_pms_eng_installation_current_equipment(tenant_id,current_equipment_id) |
| ast_device_location | id | PRIMARY(id); uk_ast_device_location_current(tenant_id,current_device_sn); uk_ast_device_location_source(tenant_id,source_system,source_key) |
| ast_address | id | PRIMARY(id) |
| ast_device | id | PRIMARY(id); uk_ast_device_tenant_sn(tenant_id,sn) |
