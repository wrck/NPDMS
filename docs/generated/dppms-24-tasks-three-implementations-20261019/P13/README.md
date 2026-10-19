# P13 CRM执行单

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p13)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P13。原组件不再增设独立任务。

## 业务主键与原身份

业务表头：tenant_id + source_system + execution_no；每个原执行单id仍单独追溯到规范表头。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P13:PREVIEW_READY/99895 | 当前候选没有真实同步证据；不继承其他实现结果 |
| 独立直写任务 | PREVIEW_READY / 1895 | 未找到本实现真实同步完成证据；预览不计同步 |
| 新虚基类套件 | PREVIEW_READY / 99895 | 未找到本实现真实同步完成证据；预览不计同步 |

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

前置：有效规范type10项目范围；同业务键完整最新修订，不用多个MAX拼接载荷；可空组织ID不强制回填。

后置：保存表头及每个原始id的来源身份；消费者人工字段不覆盖。

### 原通用引擎 / Owner适配器

原执行单Owner处理规范修订和目录引用；历史99895条全为ISSUE。

当前入口：`DPPMS_CRM_EXECUTION_ORDER`。

定义来源：[P13.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P13.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P13 | 2110439245579325441 | 2110443193249861634 | PREVIEW_READY | 99895 | {"ISSUE": 99895}  |

回执来源：[preview-repair-20261015.json](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| DppmsExecutionOrganizationReferencesTest | 4 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-193735.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-193735.log) |
| DppmsExecutionOrderSyncAdapterTest | 15 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-193735.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-193735.log) |
| DppmsOrderFieldCarryOwnerTest | 13 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-015733.log](D:/CodexTaskArtifacts/project-detail-followup/full-preview-repair-20261014/test-015733.log) |

真实前后置方法及实现位置：

- [DppmsExecutionOrderSyncAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/sync/DppmsExecutionOrderSyncAdapter.java:38)
- [DppmsExecutionOrderSyncAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/sync/DppmsExecutionOrderSyncAdapter.java:45)
- [DppmsExecutionOrderSyncAdapter.java::plan](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/sync/DppmsExecutionOrderSyncAdapter.java:69)
- [DppmsExecutionOrderSyncAdapter.java::convert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/sync/DppmsExecutionOrderSyncAdapter.java:157)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 独立直写任务

DirectExecutionOrderMapper；历史1895条与原方式读数不构成同一范围证明。

当前入口：`DPPMS_CRM_EXECUTION_ORDER_DIRECT`。

定义来源：[tasks-before.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P13 | 2111093193313538050 | 2111114291363082241 | PREVIEW_READY | 1895 | {"CREATED": 1895}  |

回执来源：[owned-only-status.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| DirectExecutionOrderTransactionTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-035334.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-035334.log) |

真实前后置方法及实现位置：

- [DppmsDirectExecutionOrderTask.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectExecutionOrderTask.java:73)
- [DppmsDirectExecutionOrderTask.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectExecutionOrderTask.java:81)
- [DppmsDirectExecutionOrderTask.java::plan](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectExecutionOrderTask.java:110)
- [DppmsDirectExecutionOrderTask.java::convert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectExecutionOrderTask.java:213)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 新虚基类套件

项目前缀目录一次读取，执行单原表流式读取；99895入选、2625不入选；多来源索引是执行前置，预览不预测跨批实际写数。

当前入口：`BASE_P13`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P13 | — | 2111515526163517442 | PREVIEW_READY | 99895 | {"INSERTED": 0, "UPDATED": 0, "UNCHANGED": 0} P13_EXECUTION_SCHEMA_REQUIRED:uk_int_base_sync_binding_target;P13_OUTSIDE_REVIEWED_PROJECT_SCOPE:2625;P13_PREVIEW_SOURCE_VALIDATED;WRITE_COUNTS_DEFERRED_UNTIL_EXECUTION |

回执来源：[P13-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/P13-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| RemainingTaskMySqlTest | 7 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |

真实前后置方法及实现位置：

- [P13ExecutionOrderSyncTask.java::createParameters](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P13ExecutionOrderSyncTask.java:19)
- [P13ExecutionOrderSyncTask.java::syncDataQueryByRowHandler](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P13ExecutionOrderSyncTask.java:20)
- [P13ExecutionOrderSyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P13ExecutionOrderSyncTask.java:33)
- [P13ExecutionOrderSyncTask.java::syncDataBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P13ExecutionOrderSyncTask.java:34)
- [P13ExecutionOrderSyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P13ExecutionOrderSyncTask.java:35)
- [P13ExecutionOrderSyncTask.java::syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P13ExecutionOrderSyncTask.java:75)
- [P13ExecutionOrderSyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P13ExecutionOrderSyncTask.java:76)
- [P13ExecutionOrderSyncTask.java::syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P13ExecutionOrderSyncTask.java:77)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| pm_project_property_from_sms | id | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| com_crm_execution_order | id | PRIMARY(id); uk_crm_execution(tenant_id,source_system,execution_no); uk_crm_execution_order_tenant_row(tenant_id,id) |
