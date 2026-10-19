# P21 项目租赁产品及配置树

2026-10-19当前更新：[P21已修复并完成1190条真实预览；执行结构警告保留](../base-sync-13-failures-followup-20261019.md#p21)。原历史回执保留。

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p21)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P21。原组件不再增设独立任务。

## 业务主键与原身份

展示缓存全量快照，无原表稳定唯一键；保留每条重复，技术行序/新ID不代表自然业务键。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P21:PREVIEW_READY/1294 | 当前候选没有真实同步证据；不继承其他实现结果 |
| 独立直写任务 | FAILED / 1190 | 未找到本实现真实同步完成证据；预览不计同步 |
| 新虚基类套件 | PREVIEW_READY / 1190；执行结构警告保留 | 未找到本实现真实同步完成证据；预览不计同步 |

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

前置：两张原租赁/配置缓存原字段；project_id按已批准可空契约，不得伪造正式项目。

后置：两份缓存按租户全量替换；预览不清空；同次失败回滚此前删除/插入；不转成正式项目产品关系。

### 原通用引擎 / Owner适配器

既定TABLE_MAPPING FULL_CACHE_REPLACE。

当前入口：`TABLE_MAPPING`。

定义来源：[P21.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P21.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P21 | 2110439264919261186 | 2110448007073533954 | PREVIEW_READY | 1294 | {"ISSUE": 1294}  |

回执来源：[preview-repair-20261015.json](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| GenericCacheRefreshTest | 8 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031357.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031357.log) |
| GenericSyncEngineTest | 57 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-022627.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-022627.log) |
| SyncRunTransactionTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031802.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031802.log) |
| DppmsConsolidatedTemplateTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031117.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031117.log) |
| SyncRunMaintenanceTest | 9 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-193533.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-193533.log) |
| SyncBindingRestorationTest | 1 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-213658.log](D:/CodexTaskArtifacts/project-detail-followup/sync-optimization-merge-20261014/targeted-213658.log) |
| DppmsRuntimeConfigurationsTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-runtime-config-recheck.log](D:/CodexTaskArtifacts/project-detail-followup/preview-repair-20261015/final-runtime-config-recheck.log) |

真实前后置方法及实现位置：

- [GenericSyncJdbcStore.java::validateValues](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:33)
- [GenericSyncEngine.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:43)
- [GenericSyncEngine.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:173)
- [GenericSyncEngine.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:174)
- [GenericSyncEngine.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:209)
- [GenericSyncEngine.java::afterCommit](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:442)
- [GenericSyncEngine.java::canonical](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:543)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 独立直写任务

仍GenericSyncEngine独立缓存任务；历史1190后失败，结构project_id非空。

当前入口：`TABLE_MAPPING`。

定义来源：[tasks-before.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P21 | 2111096244766167042 | 2111096244959105026 | FAILED | 1190 | {"FAILED": 1190} 必填目标字段未映射: project_id |

回执来源：[owned-only-status.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| GenericCacheRefreshTest | 8 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031357.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031357.log) |
| GenericSyncEngineTest | 57 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-022627.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-022627.log) |
| SyncRunTransactionTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031802.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031802.log) |
| DppmsConsolidatedTemplateTest | 3 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031117.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031117.log) |
| SyncRunMaintenanceTest | 9 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-193533.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-193533.log) |
| SyncBindingRestorationTest | 1 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-213658.log](D:/CodexTaskArtifacts/project-detail-followup/sync-optimization-merge-20261014/targeted-213658.log) |
| DppmsRuntimeConfigurationsTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-runtime-config-recheck.log](D:/CodexTaskArtifacts/project-detail-followup/preview-repair-20261015/final-runtime-config-recheck.log) |

真实前后置方法及实现位置：

- [GenericSyncJdbcStore.java::validateValues](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:33)
- [GenericSyncEngine.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:43)
- [GenericSyncEngine.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:173)
- [GenericSyncEngine.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:174)
- [GenericSyncEngine.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:209)
- [GenericSyncEngine.java::afterCommit](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:442)
- [GenericSyncEngine.java::canonical](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java:543)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 新虚基类套件

两次单表查询、按批写。2026-10-19已修复预览被结构误挡的问题，1190条预览就绪；真实执行仍因两表project_id非空而在清理前拒绝。

当前入口：`BASE_P21`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P21 | 2111463419926470657 | 2112053188930744321 | PREVIEW_READY | 1190 | P21_EXECUTION_SCHEMA_REQUIRED:project_id_nullable |
| P21 | — | 2111463420241043458 | FAILED | 0 | {"INSERTED": 0, "UPDATED": 0, "UNCHANGED": 0} P21：已审核可空列尚未就绪；禁止补造关联ID或原时间 |

最新回执：[P21-repaired-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P21-repaired-preview.json)；其余表格行保留初次汇总时的历史证据。

回执来源：[P21-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-remaining-20261017/P21-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

2026-10-19新增ProjectCachePreviewMySqlTest原生临时表检查3项通过，覆盖双源重复行只读预览、执行清理前结构拒绝、源精度失败；同时完成19项共享隔离/装配/可空值检查。合计22项通过，范围及日志见[跟进报告](../base-sync-13-failures-followup-20261019.md)。这些检查不是正式写入验收。

真实前后置方法及实现位置：

- [P21ProjectCacheSyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P21ProjectCacheSyncTask.java:20)
- [P21ProjectCacheSyncTask.java::syncDataBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P21ProjectCacheSyncTask.java:21)
- [P21ProjectCacheSyncTask.java::syncDataQuery](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P21ProjectCacheSyncTask.java:28)
- [P21ProjectCacheSyncTask.java::syncDataValidate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P21ProjectCacheSyncTask.java:37)
- [P21ProjectCacheSyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P21ProjectCacheSyncTask.java:88)
- [P21ProjectCacheSyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P21ProjectCacheSyncTask.java:105)
- [P21ProjectCacheSyncTask.java::syncDataClearCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P21ProjectCacheSyncTask.java:111)
- [P21ProjectCacheSyncTask.java::syncDataClear](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P21ProjectCacheSyncTask.java:115)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| pm_project_product_config_level_info_from_crm | 盘点未给出稳定PK | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| pm_project_product_lease_line_from_crm | 盘点未给出稳定PK | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| proj_project_lease_product | id | PRIMARY(id) |
| proj_project_product_configuration | id | PRIMARY(id) |
