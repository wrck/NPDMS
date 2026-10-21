# P18 项目合同关系

2026-10-20当前结果：**新P18来源预览已闭合**，Run2112256535977709570 PREVIEW_READY/read82676/写入计划0，24项最新Run均PREVIEW_READY。公司范围是正式COM保护，曾被误用为整表来源预览前置；现在未授权公司只登记待输入、不查询其正式合同/关系，实际同步与既存项目管理权继续校验。具体代码、ADR依据、逐公司及8条缺失原记录见[本轮授权审计与真实预览](../P18-company-authorization-audit-preview-20261020.md)。

此前2026-10-20三项续办的23/24阶段回执见[历史结果](../base-sync-P10-P18-P33-followup-20261020.md)；下列2026-10-19阶段内容按原时点保留，不能代替上方当前结果。

此前逐项核查与真实预览：[base-sync-four-blockers-audit-20261019](../base-sync-four-blockers-audit-20261019.md#p18)。当时新P18为FAILED，原任务及历史失败回执保持。

本轮正式输入依赖、追加裁决与真实终态：[P18](../base-sync-five-input-dependencies-20261019.md#p18)；[当前24项JSON](../base-sync-five-input-dependencies-20261019.json)。下方旧阶段回执保留其历史范围，当前C以本轮记录为准。

最新原用户名/原ID规则及回执：[本项结果](../base-sync-original-identifiers-preview-20261019.md#p18)。此前阶段判断和运行原件保留。

较早阶段更新（历史）：[本次真实预览仍受外部前置阻断，依据及Run](../base-sync-13-failures-followup-20261019.md#p18)。原历史回执保留。

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p18)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P18。原组件不再增设独立任务。

## 业务主键与原身份

来源组合：contractSourceId + memberId；业务关系：正式project_id + contract_id；原项目/合同号仅作解析输入。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P18:PREVIEW_READY/82676 | 当前候选没有真实同步证据；不继承其他实现结果 |
| 独立直写任务 | PREVIEW_READY / 82676 / ISSUE=82676 | 未找到本实现真实同步完成证据；预览不计同步 |
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

前置：项目组、成员桥、规范项目与合同精确匹配；活动来源优先，最早创建、最新更新及同刻人员冲突规则。

后置：写正式关系及各原来源追溯；真实项目、合同、公司授权不可省，不借预览父ID。

### 原通用引擎 / Owner适配器

当前原线完整组输入新工厂；ONLY_FULL_GROUP_BY修复只有常量派生表检查，未真实全量验收新候选。

当前入口：`DPPMS_PROJECT_CONTRACT_RELATION`。

定义来源：[P18.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P18.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P18 | 2110307139123380226 | 2110457506849107969 | PREVIEW_READY | 82676 | {"ISSUE": 82676}  |

回执来源：[preview-repair-20261015.json](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| DppmsProjectContractFormalDecisionTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031117.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031117.log) |
| DppmsProjectContractRelationSyncAdapterTest | 44 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031357.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031357.log) |
| ProjectMemberHistoryImportServiceTest | 14 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-022944.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-022944.log) |
| ProjectMemberSourceHistoryServiceTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-023457.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-023457.log) |
| ProjectAssociatedDataServiceTest | 5 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-192821.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-192821.log) |
| ProjectDeviceSelectionContextApiImplTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-205005.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-205005.log) |
| ProjectContractRelationMigrationMySqlTest | 9 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-111620.log](D:/CodexTaskArtifacts/project-detail-followup/contract-relation-restore-20261014/test-111620.log) |
| ProjectContractRelationMigrationServiceTest | 18 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-111620.log](D:/CodexTaskArtifacts/project-detail-followup/contract-relation-restore-20261014/test-111620.log) |
| ProjectMasterMigrationServiceTest | 28 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-015733.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-015733.log) |
| ProjectMasterMigrationMySqlTest | 6 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-015733.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-015733.log) |
| ProjectLeaseProductOwnerMySqlTest | 6 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-024833.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-024833.log) |
| ProjectProductConfigurationOwnerMySqlTest | 7 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-024833.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-024833.log) |
| ProjectRealProductOwnerMySqlTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-024833.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-024833.log) |

真实前后置方法及实现位置：

- [ProjectContractRelationMigrationService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/ProjectContractRelationMigrationService.java:91)
- [ProjectContractRelationMigrationService.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/ProjectContractRelationMigrationService.java:93)
- [ProjectContractRelationMigrationService.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/ProjectContractRelationMigrationService.java:95)
- [ProjectContractRelationMigrationService.java::canonical](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/ProjectContractRelationMigrationService.java:200)
- [DppmsProjectContractRelationSyncAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectContractRelationSyncAdapter.java:42)
- [DppmsProjectContractRelationSyncAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectContractRelationSyncAdapter.java:44)
- [DppmsProjectContractRelationSyncAdapter.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectContractRelationSyncAdapter.java:45)
- [DppmsProjectContractRelationSyncAdapter.java::actor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectContractRelationSyncAdapter.java:418)
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

DirectProjectRelationMapper；82676条预览均ISSUE(PROJECT_BINDING_MISSING)。

当前入口：`DPPMS_PROJECT_CONTRACT_RELATION_DIRECT`。

定义来源：[tasks-before.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P18 | 2111096144610381825 | 2111141902793523202 | PREVIEW_READY | 82676 | {"ISSUE": 82676}  |

回执来源：[owned-only-status.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| DirectProjectRelationPlanTest | 18 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-041305.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-041305.log) |
| DirectProjectRelationTransactionTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-041559.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-041559.log) |

真实前后置方法及实现位置：

- [DirectProjectRelationPersistence.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectProjectRelationPersistence.java:87)
- [DirectProjectRelationPersistence.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectProjectRelationPersistence.java:88)
- [DirectProjectRelationPersistence.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectProjectRelationPersistence.java:90)
- [DirectProjectRelationPersistence.java::canonical](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectProjectRelationPersistence.java:196)
- [DppmsDirectProjectRelationTask.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectProjectRelationTask.java:51)
- [DppmsDirectProjectRelationTask.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectProjectRelationTask.java:53)
- [DppmsDirectProjectRelationTask.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectProjectRelationTask.java:54)
- [DppmsDirectProjectRelationTask.java::actor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectProjectRelationTask.java:427)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 新虚基类套件

当前采用原用户名及原项目ID规则，最新回执：[P18-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-p22-20261019/P18-preview.json)。

四份原表查询各一次、运行内有界归并；源查询已不超时，但9个正式审计账号缺失，首个x00270。

当前入口：`BASE_P18`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P18 | 2111504251568156674 | 2112059597156204546 | FAILED | 0 | PROJECT_FORMAL_IDENTITY_INVALID |
| P18 | 2111504251568156674 | 2112051502157832193 | FAILED | 0 | 原审计人员缺少已核实的平台账号，禁止猜配：x00270 |
| P18 | — | 2111517851758272513 | FAILED | 0 | {"INSERTED": 0, "UPDATED": 0, "UNCHANGED": 0} 原审计人员缺少已核实的平台账号，禁止猜配：x00270 |

最新回执：[P18-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P18-current-preview.json)；其余表格行保留初次汇总时的历史证据。

回执来源：[P18-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018-wave9/P18-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

未定位到可明确关联本方式的单任务已执行测试类回执；共享框架/原生SQL检查单列于总报告，不当作该任务写入验收。

真实前后置方法及实现位置：

- [P18ProjectContractSyncTask.java::createParameters](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:18)
- [P18ProjectContractSyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:22)
- [P18ProjectContractSyncTask.java::syncDataBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:23)
- [P18ProjectContractSyncTask.java::syncDataQuery](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:24)
- [P18ProjectContractSyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:59)
- [P18ProjectContractSyncTask.java::syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:91)
- [P18ProjectContractSyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:92)
- [P18ProjectContractSyncTask.java::syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:93)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| pm_project_contract | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| pm_project_group | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| pm_project_group_relationship | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| com_project_contract_relation | id | PRIMARY(id); uk_project_contract(tenant_id,project_id,contract_id,relation_role); uk_project_contract_rel_tenant_row(tenant_id,id) |
