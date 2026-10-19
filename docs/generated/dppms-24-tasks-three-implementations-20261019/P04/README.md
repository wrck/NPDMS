# P04 账号、角色、授权和数据范围

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p04)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P04, P05, P06, P07。原组件不再增设独立任务。

## 业务主键与原身份

账号：tenant_id + 精确username；角色：tenant_id + code；关系：user_id + role_id；范围：账号/公司/部门/语义角色及有效期、审计；每份t/fnd原ID独立追溯。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P04:FAILED/1566 + P05:FAILED/31 + P06:FAILED/0 + P07:FAILED/0 | 当前候选没有真实同步证据；不继承其他实现结果 |
| 独立直写任务 | PREVIEW_READY / 5015 | 未找到本实现真实同步完成证据；预览不计同步 |
| 新虚基类套件 | PREVIEW_READY / 12632 | 未找到本实现真实同步完成证据；预览不计同步 |

验证仅引用既有证据，本轮没有重跑测试/预览/同步。A的当前候选与早期回执不是相同配置版本；B/C也不相互替代。类级共同测试不能写成每任务全部用例都已通过。

## 浏览器验证范围

| 方式 | 已有浏览器结果与边界 | 原证据 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 未将历史页面或其他路线页面作为当前A候选的浏览器验收；当前候选专属业务UI验收未证明。 | 未定位到当前候选/最新Run专属证明 |
| 独立直写任务 | 已有真实浏览器终态检查；P04为5015条完整预览，P37为475468条完整预览且第10页空码原行保留。只证明预览详情与未提交提示，不等于真实同步。 | [browser-P04-result.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/P37-empty-code/browser-P04-result.json) |
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

前置：严格既定账号范围，FND优先；EHR精确工号和三项姓名例外；391→88、两组FND别名及五个仅角色账号沿既有裁决。

后置：账号→角色→成员关系→公司部门范围；原admin只作审计引用；保存各原ID、缓存刷新；无源密码迁入。

### 原通用引擎 / Owner适配器

当前候选合并P04/P05/P06/P07；历史预览仍是四个组件，不能算合并候选已验收。

当前入口：`DPPMS_ACCOUNT_FOUNDATION`。

定义来源：[P04.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P04.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P04 | 2110439242966274050 | 2110451979821789185 | FAILED | 1566 | {"FAILED": 1566}  |
| P05 | 2110439243297624066 | 2110442606106021889 | FAILED | 31 | {"FAILED": 31}  |
| P06 | 2110439243515727873 | 2110442655988879361 | FAILED | 0 | {"FAILED": 0}  |
| P07 | 2110439243805134849 | 2110442705154510850 | FAILED | 0 | {"FAILED": 0}  |

回执来源：[preview-repair-20261015.json](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| SystemRoleMigrationPlanTest | 12 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-001426.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-001426.log) |
| SystemRoleMigrationTransactionTest | 10 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-001426.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-001426.log) |
| SystemUserRoleMigrationTransactionTest | 5 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-001426.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-001426.log) |
| SystemUserScopeMigrationTransactionTest | 6 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-001426.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-001426.log) |
| DppmsAccountFoundationSyncAdapterTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031117.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031117.log) |
| DppmsRoleSourceContractTest | 1 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-001759.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-001759.log) |
| DppmsUserRoleSyncAdapterTest | 5 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-001759.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-001759.log) |
| DppmsUserScopeSyncAdapterTest | 7 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-001759.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-001759.log) |
| DppmsUserSyncAdapterTest | 8 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-001759.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-001759.log) |

真实前后置方法及实现位置：

- [DppmsAccountFoundationSyncAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsAccountFoundationSyncAdapter.java:32)
- [DppmsAccountFoundationSyncAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsAccountFoundationSyncAdapter.java:33)
- [DppmsAccountFoundationSyncAdapter.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsAccountFoundationSyncAdapter.java:35)
- [DppmsAccountFoundationSyncAdapter.java::prepare](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsAccountFoundationSyncAdapter.java:89)
- [DppmsRoleSyncAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsRoleSyncAdapter.java:34)
- [DppmsRoleSyncAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsRoleSyncAdapter.java:35)
- [DppmsRoleSyncAdapter.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsRoleSyncAdapter.java:39)
- [DppmsRoleSyncAdapter.java::actors](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsRoleSyncAdapter.java:117)
- [DppmsRoleSyncAdapter.java::actor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsRoleSyncAdapter.java:146)
- [DppmsUserRoleSyncAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserRoleSyncAdapter.java:25)
- [DppmsUserRoleSyncAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserRoleSyncAdapter.java:26)
- [DppmsUserRoleSyncAdapter.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserRoleSyncAdapter.java:30)
- [DppmsUserRoleSyncAdapter.java::fields](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserRoleSyncAdapter.java:123)
- [DppmsUserScopeSyncAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserScopeSyncAdapter.java:35)
- [DppmsUserScopeSyncAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserScopeSyncAdapter.java:36)
- [DppmsUserScopeSyncAdapter.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserScopeSyncAdapter.java:39)
- [DppmsUserScopeSyncAdapter.java::actor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserScopeSyncAdapter.java:194)
- [DppmsUserScopeSyncAdapter.java::fields](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserScopeSyncAdapter.java:232)
- [DppmsUserSyncAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserSyncAdapter.java:42)
- [DppmsUserSyncAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserSyncAdapter.java:43)
- [DppmsUserSyncAdapter.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserSyncAdapter.java:46)
- [DppmsUserSyncAdapter.java::postId](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserSyncAdapter.java:193)
- [DppmsUserSyncAdapter.java::actor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserSyncAdapter.java:249)
- [SystemDepartmentMigrationApiImpl.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemDepartmentMigrationApiImpl.java:27)
- [SystemDepartmentMigrationApiImpl.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemDepartmentMigrationApiImpl.java:56)
- [SystemDepartmentMigrationApiImpl.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemDepartmentMigrationApiImpl.java:69)
- [SystemRoleMigrationApiImpl.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemRoleMigrationApiImpl.java:32)
- [SystemRoleMigrationApiImpl.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemRoleMigrationApiImpl.java:49)
- [SystemRoleMigrationApiImpl.java::validateAuditCorrection](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemRoleMigrationApiImpl.java:98)
- [SystemRoleMigrationApiImpl.java::validateRequest](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemRoleMigrationApiImpl.java:155)
- [SystemRoleMigrationApiImpl.java::validateActors](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemRoleMigrationApiImpl.java:162)
- [SystemRoleMigrationApiImpl.java::planPrimary](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemRoleMigrationApiImpl.java:238)
- [SystemUserMigrationApiImpl.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:68)
- [SystemUserMigrationApiImpl.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:79)
- [SystemUserMigrationApiImpl.java::validateQuota](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:106)
- [SystemUserMigrationApiImpl.java::validateAuditCorrection](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:136)
- [SystemUserMigrationApiImpl.java::validateReferences](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:189)
- [SystemUserMigrationApiImpl.java::validateRequest](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:202)
- [SystemUserMigrationApiImpl.java::actor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:294)
- [SystemUserMigrationApiImpl.java::posts](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:312)
- [SystemUserRoleMigrationApiImpl.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserRoleMigrationApiImpl.java:26)
- [SystemUserRoleMigrationApiImpl.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserRoleMigrationApiImpl.java:38)
- [SystemUserRoleMigrationApiImpl.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserRoleMigrationApiImpl.java:54)
- [SystemUserScopeMigrationApiImpl.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserScopeMigrationApiImpl.java:26)
- [SystemUserScopeMigrationApiImpl.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserScopeMigrationApiImpl.java:38)
- [SystemUserScopeMigrationApiImpl.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserScopeMigrationApiImpl.java:52)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 独立直写任务

复用账号解释器、替换为DirectAccount*持久化；5015为旧来源投影读数，ADOPTED等是预览动作。

当前入口：`DPPMS_ACCOUNT_FOUNDATION_DIRECT`。

定义来源：[tasks-before.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P04 | 2110802629025939458 | 2111308334340759553 | PREVIEW_READY | 5015 | {"ADOPTED": 6, "CREATED": 12974, "SKIPPED": 1043, "REFERENCED": 2}  |

回执来源：[owned-only-status.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| DirectAccountRolesPlanTest | 12 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-012210.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-012210.log) |
| DirectAccountUsersPlanTest | 7 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-012210.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-012210.log) |
| DppmsDirectAccountTemplateTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-013645.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-013645.log) |
| DirectAccountTransactionTest | 5 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031357.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031357.log) |

真实前后置方法及实现位置：

- [DirectAccountMemberships.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountMemberships.java:26)
- [DirectAccountMemberships.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountMemberships.java:37)
- [DirectAccountMemberships.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountMemberships.java:53)
- [DirectAccountRoles.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountRoles.java:31)
- [DirectAccountRoles.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountRoles.java:47)
- [DirectAccountRoles.java::validateRequest](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountRoles.java:95)
- [DirectAccountRoles.java::validateActors](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountRoles.java:102)
- [DirectAccountRoles.java::planPrimary](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountRoles.java:179)
- [DirectAccountScopes.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountScopes.java:26)
- [DirectAccountScopes.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountScopes.java:37)
- [DirectAccountScopes.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountScopes.java:51)
- [DirectAccountUsers.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountUsers.java:33)
- [DirectAccountUsers.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountUsers.java:43)
- [DirectAccountUsers.java::validateQuota](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountUsers.java:71)
- [DirectAccountUsers.java::validateReferences](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountUsers.java:96)
- [DirectAccountUsers.java::validateRequest](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountUsers.java:109)
- [DirectAccountUsers.java::actor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountUsers.java:205)
- [DirectAccountUsers.java::posts](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectAccountUsers.java:225)
- [DppmsDirectAccountTask.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectAccountTask.java:44)
- [DppmsDirectAccountTask.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectAccountTask.java:45)
- [DppmsDirectAccountTask.java::syncDataBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectAccountTask.java:47)
- [DppmsDirectAccountTask.java::syncDataQuery](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectAccountTask.java:58)
- [DppmsDirectAccountTask.java::syncDataInsertWrapper](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectAccountTask.java:63)
- [DppmsDirectAccountTask.java::syncDataSuccess](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectAccountTask.java:97)
- [DppmsDirectAccountTask.java::prepare](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectAccountTask.java:112)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 新虚基类套件

获批任职历史存custom_info.employmentAssignments；isSysUser原值存systemPresetUser，不授管理员；330任职/1176逻辑账号；12632是四步骤候选行计数。

当前入口：`BASE_P04`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P04 | — | 2111517837875126273 | PREVIEW_READY | 12632 | {"INSERTED": 12605, "UPDATED": 0, "UNCHANGED": 27} P04_EXECUTION_SCHEMA_REQUIRED:uk_int_base_sync_binding_target;P04_ORIGINAL_READ:3307;EXCLUDED_ACCOUNTS:26;ORPHAN_PROFILES:3 |

回执来源：[P04-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018-wave9/P04-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| P04AccountBaseSyncTest | 7 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |

真实前后置方法及实现位置：

- [P04AccountSyncTask.java::createParameters](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:31)
- [P04AccountSyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:32)
- [P04AccountSyncTask.java::syncDataQuery](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:138)
- [P04AccountSyncTask.java::syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:139)
- [P04AccountSyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:140)
- [P04AccountSyncTask.java::syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:141)
- [P04AccountSyncTask.java::syncDataAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:146)
- [P04AccountSyncTask.java::afterCommit](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java:146)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| fnd_roles | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| fnd_user_info | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| fnd_user_power | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| t_role | role_id | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| t_user | user_id | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| t_user_info | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| t_user_role | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| system_users | id | PRIMARY(id) |
| system_role | id | PRIMARY(id) |
| system_user_role | id | PRIMARY(id) |
| system_user_company_department_scope | id | PRIMARY(id) |
