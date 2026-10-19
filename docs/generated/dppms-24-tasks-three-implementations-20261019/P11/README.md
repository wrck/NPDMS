# P11 CRM产品

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p11)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P11。原组件不再增设独立任务。

## 业务主键与原身份

CRM itemCode；正式tenant_id + product_code。A/B既有来源键与版本策略见其targets配置；C按修剪后的产品编码批量upsert。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P11:PREVIEW_READY/8441 | 当前候选没有真实同步证据；不继承其他实现结果 |
| 独立直写任务 | PREVIEW_READY / 8441 | 未找到本实现真实同步完成证据；预览不计同步 |
| 新虚基类套件 | PREVIEW_READY / 8443 | 未找到本实现真实同步完成证据；预览不计同步 |

验证仅引用既有证据，本轮没有重跑测试/预览/同步。A的当前候选与早期回执不是相同配置版本；B/C也不相互替代。类级共同测试不能写成每任务全部用例都已通过。

## 浏览器验证范围

| 方式 | 已有浏览器结果与边界 | 原证据 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 未将历史页面或其他路线页面作为当前A候选的浏览器验收；当前候选专属业务UI验收未证明。 | 未定位到当前候选/最新Run专属证明 |
| 独立直写任务 | 已保留该独立任务运行详情页面证据；确切Run/版本见原截图文本，不将页面可打开扩大为所有业务规则已通过。 | [browser-owned-P11.txt](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/browser-owned-P11.txt) |
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

前置：SQL Server正式视图；空键、字符串容量、原精度及既定来源保护。

后置：写正式产品；不修改人工document_id；不产生产品标签/扩展副本。

### 原通用引擎 / Owner适配器

TABLE_MAPPING原PRODUCT；较早SQL按同码窗口选一行，历史读取8441。

当前入口：`TABLE_MAPPING`。

定义来源：[P11.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P11.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P11 | 2110439245117952002 | 2110443092087443458 | PREVIEW_READY | 8441 | {"CREATED": 8441}  |

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

仍是GenericSyncEngine，独立P11_DIRECT身份，不是第二套专用SQL写入器。

当前入口：`TABLE_MAPPING`。

定义来源：[P11-creation.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/P11-creation.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P11 | 2111150336083423233 | 2111150345252171778 | PREVIEW_READY | 8441 | {"CREATED": 8441}  |

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

LTRIM/RTRIM仅在源SQL，保留8443原行含2个重复编码；每批INSERT ON DUPLICATE KEY UPDATE，version递增，预览动作计数0。

当前入口：`BASE_P11`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P11 | 2111407905813229570 | 2111436809982627841 | PREVIEW_READY | 8443 | {"INSERTED": 0, "UPDATED": 0, "UNCHANGED": 0}  |

回执来源：[P11-preview-result-v2.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-real-preview-20261017/P11-preview-result-v2.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| BaseSyncMapperWiringTest | 2 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |
| ProductSyncArchitectureTest | 29 / 0 / 0 / 0 | BUILD SUCCESS | 静态架构/隔离检查；不证明业务写入 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |
| ProductBaseSyncTest | 6 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |

真实前后置方法及实现位置：

- [P11CrmProductSyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P11CrmProductSyncTask.java:18)
- [P11CrmProductSyncTask.java::syncDataUsesUpsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P11CrmProductSyncTask.java:19)
- [P11CrmProductSyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P11CrmProductSyncTask.java:20)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| DPtech_v_product_info_single | 盘点未给出稳定PK | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| ast_product_official_info | id | PRIMARY(id); uk_ast_product_official_info_source(tenant_id,source_system,source_key) |
