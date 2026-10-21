# P10 CRM客户

2026-10-20当前结果：已按本轮明确授权，仅在npdms_reverify_20261014将name扩为VARCHAR(255)。新BASE_P10 Run2112346849270747138 PREVIEW_READY/read59544/规划插入59544，容量告警已消除，没有实际客户写入或新绑定；原任务及既有7条客户完整保持。执行收据、两表保护和真实预览见[容量补丁及最终结果](../P10-P33-capacity-applied-preview-20261020.md)。

此前2026-10-20实现及回执见[三项续办历史结果](../base-sync-P10-P18-P33-followup-20261020.md)，以下2026-10-19内容按原时点保留。

此前逐项核查与真实预览：[base-sync-four-blockers-audit-20261019](../base-sync-four-blockers-audit-20261019.md#p10)。当时新P10为FAILED，原任务及历史失败回执保持。

本轮最新：[base-sync-department-code-preview-20261019](../base-sync-department-code-preview-20261019.md)，本项真实回执[P10-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-deferred-extensions-20261019/P10-preview.json)。此前阶段判断及回执保留。

2026-10-19当前更新：[本次真实预览仍受外部前置阻断，依据及Run](../base-sync-13-failures-followup-20261019.md#p10)。原历史回执保留。

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p10)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P10。原组件不再增设独立任务。

## 业务主键与原身份

来源CRM + AccountId；正式客户按tenant_id + code及有效cus_customer_external_mapping精确定位，不按姓名合并。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P10:PREVIEW_READY/59601 | 当前候选没有真实同步证据；不继承其他实现结果 |
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

前置：CRM字段及ERP客户码映射、租户、原版本和外部身份核验；SQL Server查询与目标MySQL分开。

后置：客户主档和外部映射同事务写入；旧来源、软删除、多义身份和人工维护保护。

### 原通用引擎 / Owner适配器

CRM客户Owner入口；按已批准CRM权威定义处理。

当前入口：`CRM_CUSTOMER`。

定义来源：[P10.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P10.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P10 | 2110439244857905153 | 2110442809483628546 | PREVIEW_READY | 59601 | {"ISSUE": 83, "CREATED": 59518}  |

回执来源：[preview-repair-20261015.json](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| CustomerMasterDataErpCodeTest | 1 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-192821.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-192821.log) |

真实前后置方法及实现位置：

- [CustomerMasterDataApiImpl.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/api/masterdata/CustomerMasterDataApiImpl.java:27)
- [CustomerMasterDataApiImpl.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/api/masterdata/CustomerMasterDataApiImpl.java:81)
- [CrmCustomerSyncAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/CrmCustomerSyncAdapter.java:26)
- [CrmCustomerSyncAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/CrmCustomerSyncAdapter.java:30)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 独立直写任务

CrmDirectCustomerTask复用解释器并替换DirectCustomerPersistence。

当前入口：`CRM_CUSTOMER_DIRECT`。

定义来源：[tasks-before.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P10 | 2111093064175112194 | 2111112915576844289 | FAILED | 0 | {} 执行失败（BadSqlGrammarException），请检查连接、容量和服务状态；凭据及原始SQL不写入日志 |

回执来源：[owned-only-status.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| CrmDirectCustomerSourceTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-032956.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-032956.log) |
| DirectCustomerTransactionTest | 5 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-034205.log](D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-034205.log) |

真实前后置方法及实现位置：

- [CrmDirectCustomerTask.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/CrmDirectCustomerTask.java:33)
- [CrmDirectCustomerTask.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/CrmDirectCustomerTask.java:34)
- [CrmDirectCustomerTask.java::syncDataBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/CrmDirectCustomerTask.java:35)
- [CrmDirectCustomerTask.java::syncDataQuery](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/CrmDirectCustomerTask.java:50)
- [CrmDirectCustomerTask.java::syncDataInsertWrapper](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/CrmDirectCustomerTask.java:65)
- [DirectCustomerPersistence.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectCustomerPersistence.java:25)
- [DirectCustomerPersistence.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectCustomerPersistence.java:29)
- [DirectCustomerPersistence.java::validate](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectCustomerPersistence.java:93)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 新虚基类套件

先校验cus_customer_master.erp_customer_code；当前结构缺失，在读源前FAILED。

当前入口：`BASE_P10`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P10 | 2111470474389270529 | 2112067330504867842 | FAILED | 0 | CRM客户原记录存在未决问题：DUPLICATE_CUSTOMER_CODE |
| P10 | 2111470474389270529 | 2112051433660653569 | FAILED | 0 | P10目标缺少已审核列cus_customer_master.erp_customer_code；不执行迁移 |
| P10 | — | 2111473347214880769 | FAILED | 0 | {"INSERTED": 0, "UPDATED": 0, "UNCHANGED": 0} P10目标缺少已审核列cus_customer_master.erp_customer_code；不执行迁移 |

最新回执：[P10-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P10-current-preview.json)；其余表格行保留初次汇总时的历史证据。

回执来源：[P10-wave4-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-remaining-20261017/P10-wave4-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| IndependentFoundationTasksTest | 6 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |

真实前后置方法及实现位置：

- [P10CrmCustomerSyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P10CrmCustomerSyncTask.java:23)
- [P10CrmCustomerSyncTask.java::syncDataBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P10CrmCustomerSyncTask.java:24)
- [P10CrmCustomerSyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P10CrmCustomerSyncTask.java:28)
- [P10CrmCustomerSyncTask.java::syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P10CrmCustomerSyncTask.java:79)
- [P10CrmCustomerSyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P10CrmCustomerSyncTask.java:80)
- [P10CrmCustomerSyncTask.java::syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P10CrmCustomerSyncTask.java:87)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| Account | 盘点未给出稳定PK | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| cus_customer_master | id | PRIMARY(id); uk_cus_customer_master_tenant_code(tenant_id,code) |
