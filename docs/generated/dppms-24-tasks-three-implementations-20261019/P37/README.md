# P37 设备许可历史

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p37)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P37。原组件不再增设独立任务。

## 业务主键与原身份

475468个明确批准的原许可id用于一次性来源身份；正式license记录id另生；不是按原缓存自增ID持续同步。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P37:NOT_PREVIEWED | 当前候选没有真实同步证据；不继承其他实现结果 |
| 独立直写任务 | PREVIEW_READY / 475468 | 未找到本实现真实同步完成证据；预览不计同步 |
| 新虚基类套件 | PREVIEW_READY / 475468 | 未找到本实现真实同步完成证据；预览不计同步 |

验证仅引用既有证据，本轮没有重跑测试/预览/同步。A的当前候选与早期回执不是相同配置版本；B/C也不相互替代。类级共同测试不能写成每任务全部用例都已通过。

## 浏览器验证范围

| 方式 | 已有浏览器结果与边界 | 原证据 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 未将历史页面或其他路线页面作为当前A候选的浏览器验收；当前候选专属业务UI验收未证明。 | 未定位到当前候选/最新Run专属证明 |
| 独立直写任务 | 已有真实浏览器终态检查；P04为5015条完整预览，P37为475468条完整预览且第10页空码原行保留。只证明预览详情与未提交提示，不等于真实同步。 | [browser-P37-result.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/P37-empty-code/browser-P37-result.json) |
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

前置：专用受控读取，内存AES-256-GCM加密；正式密钥上下文、租户、执行人和INT权限；普通快照/日志不接明文。

后置：正式license_code_ciphertext字段保存认证加密封装，nonce随载荷、keyId等元数据位于该封装内；不虚构独立nonce/keyId列。缺设备/合同保留NULL和原业务码；空许可码按原始空串加密并登记问题，不占位不跳过。

### 原通用引擎 / Owner适配器

A仍是旧受控项目Owner路径，当前候选里的旧批准ID/Owner上下文参数未配置且无全量回执。整表批准已经存在；这是A尚未接入已批准整表边界的实现/配置差异，不要求重新批准整表或补造项目Owner。

当前入口：`DPPMS_SECURE_LICENSE`。

定义来源：[P37.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P37.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P37 | — | — | NOT_PREVIEWED | — | {}  |

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
- [DppmsLicenseRecordService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/license/DppmsLicenseRecordService.java:41)
- [DppmsLicenseRecordService.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/license/DppmsLicenseRecordService.java:47)
- [DppmsLicenseRecordService.java::prepare](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/license/DppmsLicenseRecordService.java:70)
- [DppmsLicenseRecordService.java::authorize](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/license/DppmsLicenseRecordService.java:198)
- [DppmsAssetSourceHistoryService.java::executeSourceHistory](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/migration/DppmsAssetSourceHistoryService.java:45)
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
- [ProjectLicenseRecordMigrationService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectLicenseRecordMigrationService.java:41)
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

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 独立直写任务

已移除整表任务的旧项目Owner遗留依赖；保留原试点路径，475468条完整预览及1条空码问题可追溯。

当前入口：`DPPMS_SECURE_LICENSE_DIRECT`。

定义来源：[tasks-before.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P37 | 2111097547311468546 | 2111308333476732929 | PREVIEW_READY | 475468 | {"CREATED": 475468}  |

回执来源：[owned-only-status.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| DirectLicenseCaptureTest | 8 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-120627.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-120627.log) |
| DirectSecureLicenseStreamTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-120627.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-120627.log) |
| DirectLicenseHistoryStreamingTaskTest | 4 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-120627.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-120627.log) |
| DirectLicenseApprovalManifestTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-120627.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-120627.log) |
| DirectLicenseHistoryCipherTest | 4 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-120627.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-120627.log) |
| DirectLicenseHistoryTransactionTest | 6 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-120627.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-120627.log) |
| DirectLicenseCaptureCipherTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-120627.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-120627.log) |
| ReviewedDirectConfigurationTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-230420.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-230420.log) |

真实前后置方法及实现位置：

- [DirectLicenseHistoryPersistence.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectLicenseHistoryPersistence.java:38)
- [DirectLicenseHistoryPersistence.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectLicenseHistoryPersistence.java:44)
- [DirectLicenseHistoryPersistence.java::prepare](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectLicenseHistoryPersistence.java:67)
- [DirectLicenseHistoryPersistence.java::authorize](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectLicenseHistoryPersistence.java:183)
- [DppmsDirectLicenseHistoryTask.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectLicenseHistoryTask.java:33)
- [DppmsDirectLicenseHistoryTask.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectLicenseHistoryTask.java:34)
- [DppmsDirectLicenseHistoryTask.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectLicenseHistoryTask.java:35)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 新虚基类套件

同一批准整表范围，专用Java加密后批写；475468条预览通过，设备/合同关联均待后续明确需要时处理。

当前入口：`BASE_P37`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P37 | — | 2111480525451595777 | PREVIEW_READY | 475468 | {"INSERTED": 475468, "UPDATED": 0, "UNCHANGED": 0} P37_LICENSE_CODE_EMPTY:sourceId=472122;P37_DEVICE_ASSOCIATION_PENDING:475468;P37_CONTRACT_ASSOCIATION_PENDING:475468 |

回执来源：[P37-wave5-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-remaining-20261017/P37-wave5-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| LicenseBaseSyncTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |

真实前后置方法及实现位置：

- [P37LicenseHistorySyncTask.java::createParameters](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P37LicenseHistorySyncTask.java:41)
- [P37LicenseHistorySyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P37LicenseHistorySyncTask.java:44)
- [P37LicenseHistorySyncTask.java::syncDataQueryByRowHandler](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P37LicenseHistorySyncTask.java:51)
- [P37LicenseHistorySyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P37LicenseHistorySyncTask.java:66)
- [P37LicenseHistorySyncTask.java::syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P37LicenseHistorySyncTask.java:92)
- [P37LicenseHistorySyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P37LicenseHistorySyncTask.java:93)
- [P37LicenseHistorySyncTask.java::syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P37LicenseHistorySyncTask.java:94)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| pm_project_license_info_from_license | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| ast_device_license_record | id | PRIMARY(id) |
