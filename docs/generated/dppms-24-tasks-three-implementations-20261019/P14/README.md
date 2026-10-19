# P14 销售订单与订单行

2026-10-19当前更新：[本次真实预览仍受外部前置阻断，依据及Run](../base-sync-13-failures-followup-20261019.md#p14)。原历史回执保留。

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p14)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P14。原组件不再增设独立任务。

## 业务主键与原身份

订单：tenant_id + source_system + company_code + order_type + order_no；A当前/C的行再加line_no，B已保存定义的行用order_id + line_no；line_type是载荷字段，不混入业务唯一键。原业务键不得被原id替代。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P14:PREVIEW_READY/467190 | 当前候选没有真实同步证据；不继承其他实现结果 |
| 独立直写任务 | PREVIEW_READY / 465804 / ISSUE=1102 | 未找到本实现真实同步完成证据；预览不计同步 |
| 新虚基类套件 | FAILED / 0 | 未找到本实现真实同步完成证据；预览不计同步 |

初次汇总仅引用既有证据；2026-10-19后续已重跑本项真实API预览，新P21另有实现修复和定向测试，详见页首跟进。未做真实同步。A的当前候选与早期回执不是相同配置版本；B/C也不相互替代。类级共同测试不能写成每任务全部用例都已通过。

## 浏览器验证范围

| 方式 | 已有浏览器结果与边界 | 原证据 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 未将历史页面或其他路线页面作为当前A候选的浏览器验收；当前候选专属业务UI验收未证明。 | 未定位到当前候选/最新Run专属证明 |
| 独立直写任务 | 已保留该独立任务运行详情页面证据；确切Run/版本见原截图文本，不将页面可打开扩大为所有业务规则已通过。 | [browser-owned-P14.txt](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/browser-owned-P14.txt) |
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

前置：来源/公司/订单类型映射、同业务键载荷冲突、源版本和父行身份校验；不任选冲突原行。

后置：订单→订单行，父order_id必须真实；可空客户/产品ID留空；禁止自动恢复软删除或写数量完成事实。

### 原通用引擎 / Owner适配器

当前候选TABLE_MAPPING；引用历史回执与当前新候选需分开。

当前入口：`TABLE_MAPPING`。

定义来源：[P14.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P14.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P14 | 2110306872558583810 | 2110507977953607682 | PREVIEW_READY | 467190 | {"ISSUE": 2138, "CREATED": 465052}  |

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

同一GenericSyncEngine，独立P14_DIRECT身份；预览465804且ISSUE1102不是零问题通过。

当前入口：`TABLE_MAPPING`。

定义来源：[P14-creation.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/P14-creation.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P14 | 2111150344182624258 | 2111150410188386306 | PREVIEW_READY | 465804 | {"ISSUE": 1102, "CREATED": 464702}  |

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

独立表头/明细批写；169表头键和10明细键冲突即整体FAILED；保留同版本保护。

当前入口：`BASE_P14`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P14 | 2111504179094777858 | 2112051434155581442 | FAILED | 0 | P14_DUPLICATE_BUSINESS_KEY_CONFLICT |
| P14 | — | 2111504179669397505 | FAILED | 0 | {"INSERTED": 0, "UPDATED": 0, "UNCHANGED": 0} P14_DUPLICATE_BUSINESS_KEY_CONFLICT |

最新回执：[P14-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P14-current-preview.json)；其余表格行保留初次汇总时的历史证据。

回执来源：[P14-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-next13-20261017/P14-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| RemainingTaskMySqlTest | 7 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |

真实前后置方法及实现位置：

- [P14SalesOrderSyncTask.java::createParameters](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:19)
- [P14SalesOrderSyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:22)
- [P14SalesOrderSyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:26)
- [P14SalesOrderSyncTask.java::prepareOrders](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:29)
- [P14SalesOrderSyncTask.java::prepareLines](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:50)
- [P14SalesOrderSyncTask.java::syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:93)
- [P14SalesOrderSyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:94)
- [P14SalesOrderSyncTask.java::syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java:95)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| pm_order_data_from_erp | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |
| pm_order_line_from_erp | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| com_sales_order | id | PRIMARY(id); uk_sales_order_business(tenant_id,source_system,company_code,order_type,order_no); uk_sales_order_source(tenant_id,source_system,source_record_key); uk_sales_order_tenant_row(tenant_id,id) |
| com_sales_order_line | id | PRIMARY(id); uk_sales_order_line(tenant_id,order_id,line_no); uk_sales_order_line_source(tenant_id,source_system,source_record_key); uk_sales_order_line_tenant_row(tenant_id,id) |
