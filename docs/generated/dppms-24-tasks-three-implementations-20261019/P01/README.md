# P01 公司、部门及主管

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p01)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P01, P02, P03。原组件不再增设独立任务。

## 业务主键与原身份

公司/部门：tenant_id + code；原compID/depID/fnd id仅作来源身份，父部门按已核实父键解析。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P01:PREVIEW_READY/180 | 当前候选没有真实同步证据；不继承其他实现结果 |
| 独立直写任务 | SUCCESS / 211（非预览） | 有既有非预览SUCCESS（仅P01，见回执） |
| 新虚基类套件 | PREVIEW_READY / 208 | 未找到本实现真实同步完成证据；预览不计同步 |

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

前置：限定基础EHR部门、FND部门及已审核补全部门；完整校验公司、父部门、同码及人工维护边界。

后置：写入公司/部门后维护来源身份；主管须正式账号，未就绪保持待关联；按实现刷新组织缓存。

### 原通用引擎 / Owner适配器

原组织归并保持原线来源与163130裁决；主管需本线P04；不得借DIRECT绑定。

当前入口：`DPPMS_ORGANIZATION`。

定义来源：[P01.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P01.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P01 | 2110439242576203777 | 2110493084286287874 | PREVIEW_READY | 180 | {"CREATED": 180}  |

回执来源：[preview-repair-20261015.json](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| SyncRunTransactionTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031802.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031802.log) |
| DppmsConsolidationTest | 2 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-011329.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-011329.log) |
| DppmsConsolidatedTemplateTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031117.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031117.log) |
| DppmsDepartmentSyncAdapterTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-003919.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-003919.log) |
| DppmsEhrDepartmentCompletionAdapterTest | 6 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-003919.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-003919.log) |
| EhrOrganizationAdapterTest | 3 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-232058.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-232058.log) |

真实前后置方法及实现位置：

- [DppmsConsolidation.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsConsolidation.java:44)
- [DppmsConsolidation.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsConsolidation.java:45)
- [DppmsConsolidation.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsConsolidation.java:47)
- [DppmsDepartmentSyncAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDepartmentSyncAdapter.java:24)
- [DppmsDepartmentSyncAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDepartmentSyncAdapter.java:26)
- [DppmsDepartmentSyncAdapter.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDepartmentSyncAdapter.java:31)
- [DppmsDepartmentSyncAdapter.java::fields](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDepartmentSyncAdapter.java:72)
- [DppmsEhrDepartmentCompletionAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsEhrDepartmentCompletionAdapter.java:27)
- [DppmsEhrDepartmentCompletionAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsEhrDepartmentCompletionAdapter.java:29)
- [DppmsEhrDepartmentCompletionAdapter.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsEhrDepartmentCompletionAdapter.java:34)
- [DppmsEhrDepartmentCompletionAdapter.java::fields](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsEhrDepartmentCompletionAdapter.java:116)
- [EhrOrganizationAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/EhrOrganizationAdapter.java:26)
- [EhrOrganizationAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/EhrOrganizationAdapter.java:28)
- [EhrOrganizationAdapter.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/EhrOrganizationAdapter.java:32)
- [SystemDepartmentMigrationApiImpl.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemDepartmentMigrationApiImpl.java:27)
- [SystemDepartmentMigrationApiImpl.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemDepartmentMigrationApiImpl.java:56)
- [SystemDepartmentMigrationApiImpl.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemDepartmentMigrationApiImpl.java:69)
- [SystemUserMigrationApiImpl.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:68)
- [SystemUserMigrationApiImpl.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:79)
- [SystemUserMigrationApiImpl.java::validateQuota](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:106)
- [SystemUserMigrationApiImpl.java::validateAuditCorrection](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:136)
- [SystemUserMigrationApiImpl.java::validateReferences](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:189)
- [SystemUserMigrationApiImpl.java::validateRequest](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:202)
- [SystemUserMigrationApiImpl.java::actor](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:294)
- [SystemUserMigrationApiImpl.java::posts](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java:312)
- [ManagedOrganizationApiImpl.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/organization/ManagedOrganizationApiImpl.java:40)
- [ManagedOrganizationApiImpl.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/organization/ManagedOrganizationApiImpl.java:55)
- [ManagedOrganizationApiImpl.java::canonical](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/organization/ManagedOrganizationApiImpl.java:200)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 独立直写任务

专用DirectOrganizationMapper；29码及父级范围沿直写裁决；历史P01确有非预览SUCCESS，不属于本次执行。

当前入口：`DPPMS_ORGANIZATION_DIRECT`。

定义来源：[tasks-before.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P01 | 2110783899885760514 | 2111288896144986114 | SUCCESS | 211 | {"CREATED": 28, "UNCHANGED": 183}  |

该任务另有较早真实预览：Run2111092767772037122，PREVIEW_READY/read211，CREATED28/UNCHANGED183；最新SUCCESS为既有非预览历史。本轮未执行任何一个。

回执来源：[owned-only-status.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| DirectOrganizationPlanTest | 9 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-030202.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-030202.log) |
| DirectOrganizationTransactionTest | 5 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031357.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031357.log) |

真实前后置方法及实现位置：

- [DirectOrganizationPlan.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectOrganizationPlan.java:193)
- [DppmsDirectOrganizationTask.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectOrganizationTask.java:40)
- [DppmsDirectOrganizationTask.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectOrganizationTask.java:41)
- [DppmsDirectOrganizationTask.java::syncDataBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectOrganizationTask.java:60)
- [DppmsDirectOrganizationTask.java::syncDataQuery](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectOrganizationTask.java:74)
- [DppmsDirectOrganizationTask.java::syncDataInsertWrapper](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectOrganizationTask.java:102)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 新虚基类套件

四份原表各读一次；已有正式定义仅引用、不接管；8条负责人登记P01_DIRECTOR_ASSOCIATION_PENDING。

当前入口：`BASE_P01`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P01 | — | 2111504172950122498 | PREVIEW_READY | 208 | {"INSERTED": 0, "UPDATED": 0, "UNCHANGED": 208} P01_DIRECTOR_ASSOCIATION_PENDING:162007003;P01_DIRECTOR_ASSOCIATION_PENDING:162012003;P01_DIRECTOR_ASSOCIATION_PENDING:162022003;P01_DIRECTOR_ASSOCIATION_PENDING:310409001;P01_DIRECTOR_ASSOCIATION_PENDING:310422001;P01_DIRECTOR_ASSOCIATION_PENDING:167010;P01_DIRECTOR_ASSOCIATION_PENDING:167020;P01_DIRECTOR_ASSOCIATION_PENDING:167050 |

回执来源：[P01-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-next13-20261017/P01-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

未定位到可明确关联本方式的单任务已执行测试类回执；共享框架/原生SQL检查单列于总报告，不当作该任务写入验收。

真实前后置方法及实现位置：

- [P01OrganizationSyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P01OrganizationSyncTask.java:25)
- [P01OrganizationSyncTask.java::createParameters](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P01OrganizationSyncTask.java:26)
- [P01OrganizationSyncTask.java::syncDataQuery](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P01OrganizationSyncTask.java:39)
- [P01OrganizationSyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P01OrganizationSyncTask.java:80)
- [P01OrganizationSyncTask.java::syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P01OrganizationSyncTask.java:105)
- [P01OrganizationSyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P01OrganizationSyncTask.java:106)
- [P01OrganizationSyncTask.java::syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P01OrganizationSyncTask.java:111)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| ehr_company | compID | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| ehr_department | depID | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| fnd_department | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| system_company | id | PRIMARY(id); uk_system_company_code(tenant_id,code,deleted) |
| system_dept | id | PRIMARY(id); uk_system_dept_code(tenant_id,code,deleted) |
