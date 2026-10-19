# P16 有效项目主档及全部旧身份

最新原用户名/原ID规则及回执：[本项结果](../base-sync-original-identifiers-preview-20261019.md#p16)。此前阶段判断和运行原件保留。

2026-10-19当前更新：[本次真实预览仍受外部前置阻断，依据及Run](../base-sync-13-failures-followup-20261019.md#p16)。原历史回执保留。

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p16)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P16, P17。原组件不再增设独立任务。

## 业务主键与原身份

先对二进制同project_type + project_code取MAX(projectId)，再校验规范行及原别名有效；正式主档按租户/项目编码与本套件来源归属保护。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P16:FAILED/0 | 当前候选没有真实同步证据；不继承其他实现结果 |
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

前置：规范主档与所有有效原ID同一完整范围；正式公司/部门、账号审计及创建/管理权限核验；不把旧数字ID当正式ID。

后置：主档→根树/路径→基础最终客户→全部原ID；不得产生当前经理授权或改写现存项目；A/B的旧完整发布契约独立保留。

### 原通用引擎 / Owner适配器

原Master/Identity Owner和不可变范围发布；正式账号及范围结构仍是前置。

当前入口：`DPPMS_PROJECT_MASTER`。

定义来源：[P16.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P16.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P16 | 2110306158427996161 | 2110445161997438978 | FAILED | 0 | {}  |

回执来源：[preview-repair-20261015.json](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| ProjectMemberHistoryImportServiceTest | 14 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-022944.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-022944.log) |
| ProjectMemberSourceHistoryServiceTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-023457.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-023457.log) |
| ProjectAssociatedDataServiceTest | 5 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-192821.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-192821.log) |
| DppmsProjectMasterSyncAdapterTest | 12 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-200134.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-200134.log) |
| ProjectDeviceSelectionContextApiImplTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-205005.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-205005.log) |
| ProjectMasterMigrationServiceTest | 28 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-015733.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-015733.log) |
| ProjectMasterMigrationMySqlTest | 6 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-015733.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-015733.log) |
| DppmsProjectIdentitySyncAdapterTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-025548.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-025548.log) |
| ProjectLeaseProductOwnerMySqlTest | 6 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-024833.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-024833.log) |
| ProjectProductConfigurationOwnerMySqlTest | 7 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-024833.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-024833.log) |
| ProjectRealProductOwnerMySqlTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-024833.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-024833.log) |

真实前后置方法及实现位置：

- [DppmsProjectIdentitySyncAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectIdentitySyncAdapter.java:33)
- [DppmsProjectIdentitySyncAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectIdentitySyncAdapter.java:35)
- [DppmsProjectIdentitySyncAdapter.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectIdentitySyncAdapter.java:40)
- [DppmsProjectMasterSyncAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectMasterSyncAdapter.java:49)
- [DppmsProjectMasterSyncAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectMasterSyncAdapter.java:50)
- [DppmsProjectMasterSyncAdapter.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectMasterSyncAdapter.java:52)
- [DppmsProjectMasterSyncAdapter.java::executeMasters](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectMasterSyncAdapter.java:65)
- [DppmsProjectMasterSyncAdapter.java::actor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectMasterSyncAdapter.java:206)
- [ProjectHeaderFinalCustomerService.java::validateActor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectHeaderFinalCustomerService.java:100)
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

DirectProject*独立持久化，原/新项目来源身份与正式树；历史未建立正式账号输入。

当前入口：`DPPMS_PROJECT_MASTER_DIRECT`。

定义来源：[tasks-before.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P16 | 2111096042021900290 | 2111114321461407746 | FAILED | 0 | {} 先完成用户迁移及来源绑定，再承接项目审计 |

回执来源：[owned-only-status.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| DirectProjectPlanTest | 28 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-040851.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-040851.log) |
| DirectProjectTransactionTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-041051.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-041051.log) |
| DirectRealProductTransactionTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-042817.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-042817.log) |
| DirectMemberHistoryTransactionTest | 4 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-043355.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-043355.log) |

真实前后置方法及实现位置：

- [DirectProjectIdentities.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectProjectIdentities.java:33)
- [DirectProjectIdentities.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectProjectIdentities.java:35)
- [DirectProjectIdentities.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectProjectIdentities.java:40)
- [DirectProjectPersistence.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectProjectPersistence.java:47)
- [DirectProjectPersistence.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectProjectPersistence.java:49)
- [DirectProjectPersistence.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectProjectPersistence.java:100)
- [DppmsDirectProjectTask.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectProjectTask.java:66)
- [DppmsDirectProjectTask.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectProjectTask.java:67)
- [DppmsDirectProjectTask.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectProjectTask.java:69)
- [DppmsDirectProjectTask.java::executeMasters](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectProjectTask.java:82)
- [DppmsDirectProjectTask.java::actor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectProjectTask.java:224)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 新虚基类套件

当前采用原用户名及原项目ID规则，最新回执：[P16-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-20261019/P16-preview.json)。

限定type10，不扩其他类型；主档/别名包括UNCHANGED均刷新本次独立run归属，COMPLETED与业务同事务提交，供C下游固定完整范围。

当前入口：`BASE_P16`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P16 | 2111504250456666113 | 2112057425274290177 | FAILED | 0 | P16_PROJECT_ID_ALREADY_OWNED |
| P16 | 2111504250456666113 | 2112051494360621058 | FAILED | 0 | P16_FORMAL_ACCOUNT_SOURCE_BINDING_MISSING:USER_FND:79:x00270 |
| P16 | — | 2111517842522415105 | FAILED | 0 | {"INSERTED": 0, "UPDATED": 0, "UNCHANGED": 0} P16_FORMAL_ACCOUNT_SOURCE_BINDING_MISSING:USER_FND:79:x00270 |

最新回执：[P16-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P16-current-preview.json)；其余表格行保留初次汇总时的历史证据。

回执来源：[P16-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018-wave9/P16-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| IndependentScopeMySqlTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |
| NewTaskPreconditionsTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |
| PrerequisiteCompletionMySqlTest | 2 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |

真实前后置方法及实现位置：

- [P16ProjectSyncTask.java::createParameters](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:29)
- [P16ProjectSyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:33)
- [P16ProjectSyncTask.java::syncDataQueryByRowHandler](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:49)
- [P16ProjectSyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:52)
- [P16ProjectSyncTask.java::syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:117)
- [P16ProjectSyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:118)
- [P16ProjectSyncTask.java::syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java:123)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| pm_project | projectId | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| proj_project | id | PRIMARY(id); uk_proj_project_code(tenant_id,project_code); uk_proj_project_code_sequence(tenant_id,code_root_id,project_sequence); uk_proj_project_tenant_row(tenant_id,id) |
| proj_project_party | id | PRIMARY(id); uk_project_party_source(tenant_id,source_system,source_table,source_record_key,party_role); uk_project_party_tenant_row(tenant_id,id) |
| int_sync_binding | id | PRIMARY(id); uk_int_binding_source(tenant_id,task_id,source_object,source_key); uk_int_binding_target(tenant_id,task_id,object_key,exclusive_target_id) |
