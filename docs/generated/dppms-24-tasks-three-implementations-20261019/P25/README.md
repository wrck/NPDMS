# P25 逐次续保

[返回24项总表](../README.md)

[历史十项目中本项任务/Owner入口及前后置逻辑](../pilot-ten-projects/tasks-and-processing.md#p25)（只读历史补充；不计入本页A/B/C当前验证）。

合并来源组件：P25。原组件不再增设独立任务。

## 业务主键与原身份

原id + 原SN复合来源身份（SN NULL/空语义按代码）；正式记录ID另生，不能仅按原id去重。

原始ID/来源键与正式表主键分开：正式主键通常由Owner/IdWorker生成；具体物理PK/唯一索引见下方结构快照和每方式SQL。

| 方式 | 已有验证结果 | 真实同步边界 |
|---|---|---|
| 原通用引擎 / Owner适配器 | 当前候选未验证；历史 P25:PREVIEW_READY/453443 | 当前候选没有真实同步证据；不继承其他实现结果 |
| 独立直写任务 | PREVIEW_READY / 453443 | 未找到本实现真实同步完成证据；预览不计同步 |
| 新虚基类套件 | PREVIEW_READY / 462213 | 未找到本实现真实同步完成证据；预览不计同步 |

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

前置：逐次续保历史独立于当前设备保修；保留10原字段、NULL/0、原起止日期与最新标志；年转参考月。

后置：保存ast_device_warranty_renewal_record及来源指纹；不按日期推断当前在保；无必要不查设备ID。

### 原通用引擎 / Owner适配器

当前原线单表读fb_service，再由AST/COM从本地发货RMA/父合同类型判资格；未真实全量/性能验收这份新路径。

当前入口：`DPPMS_WARRANTY_RENEWAL`。

定义来源：[P25.json](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P25.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P25 | 2110439265988808706 | 2110454395728310274 | PREVIEW_READY | 453443 | {"CREATED": 453443}  |

回执来源：[preview-repair-20261015.json](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| DppmsWarrantyRenewalSyncAdapterTest | 5 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031357.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031357.log) |
| DeviceWarrantyRenewalMigrationServiceTest | 7 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-220454.log](D:/CodexTaskArtifacts/project-detail-followup/renewal-20261013/test-220454.log) |
| DeviceWarrantyRenewalMySqlTest | 4 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-220454.log](D:/CodexTaskArtifacts/project-detail-followup/renewal-20261013/test-220454.log) |
| DeviceWarrantyRenewalQueryServiceTest | 4 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [test-220454.log](D:/CodexTaskArtifacts/project-detail-followup/renewal-20261013/test-220454.log) |

真实前后置方法及实现位置：

- [DeviceWarrantyRenewalMigrationApi.java::sourceKey](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/migration/DeviceWarrantyRenewalMigrationApi.java:13)
- [DeviceWarrantyRenewalMigrationService.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/warranty/DeviceWarrantyRenewalMigrationService.java:35)
- [DeviceWarrantyRenewalMigrationService.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/warranty/DeviceWarrantyRenewalMigrationService.java:39)
- [DeviceWarrantyRenewalMigrationService.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/warranty/DeviceWarrantyRenewalMigrationService.java:41)
- [DeviceWarrantyRenewalMigrationService.java::fields](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/warranty/DeviceWarrantyRenewalMigrationService.java:143)
- [DppmsWarrantyRenewalSyncAdapter.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsWarrantyRenewalSyncAdapter.java:32)
- [DppmsWarrantyRenewalSyncAdapter.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsWarrantyRenewalSyncAdapter.java:34)
- [DppmsWarrantyRenewalSyncAdapter.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsWarrantyRenewalSyncAdapter.java:36)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 独立直写任务

独立直写仍按批准来源范围和id+SN；旧预览453443，用时347.893秒。

当前入口：`DPPMS_WARRANTY_RENEWAL_DIRECT`。

定义来源：[tasks-before.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json)。定义快照仅作为SQL/映射来源，报告没有保存或启用该任务。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P25 | 2111096400672641026 | 2111129869134020609 | PREVIEW_READY | 453443 | {"CREATED": 453443}  |

回执来源：[owned-only-status.json](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| DirectWarrantyTransactionTest | 4 / 0 / 0 / 0 | 该类结果独立列出；整轮不计全通过 | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-031357.log](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-031357.log) |
| SpringJdbcStreamingReaderTest | 14 / 0 / 0 / 1 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [targeted-120627.log](D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/targeted-120627.log) |

真实前后置方法及实现位置：

- [DirectWarrantyPersistence.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectWarrantyPersistence.java:33)
- [DirectWarrantyPersistence.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectWarrantyPersistence.java:35)
- [DirectWarrantyPersistence.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectWarrantyPersistence.java:37)
- [DirectWarrantyPersistence.java::fields](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DirectWarrantyPersistence.java:140)
- [DppmsDirectWarrantyTask.java::preview](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectWarrantyTask.java:36)
- [DppmsDirectWarrantyTask.java::apply](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectWarrantyTask.java:38)
- [DppmsDirectWarrantyTask.java::execute](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDirectWarrantyTask.java:40)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。
### 新虚基类套件

整表462213行，无设备/项目/发货资格依赖，无排序，源重复身份预查一次；预览约15秒；不回填设备ID。

当前入口：`BASE_P25`。

已有运行证据：

| 任务/组件 | 任务ID | Run | 终态 | read | 摘要/错误 |
|---|---|---|---|---|---|
| P25 | — | 2111480444740603906 | PREVIEW_READY | 462213 | {"INSERTED": 462213, "UPDATED": 0, "UNCHANGED": 0}  |

回执来源：[P25-wave5-result.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-remaining-20261017/P25-wave5-result.json)。

关联测试类的已执行结果（可能是多个任务的共同验证）：

| 已执行测试类 | tests / failures / errors / skipped | 所属构建 | 证明范围 | 日志 |
|---|---|---|---|---|
| BaseSyncMapperWiringTest | 2 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |
| WarrantyBaseSyncTest | 4 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |
| WarrantySourceMySqlTest | 1 / 0 / 0 / 0 | BUILD SUCCESS | 已执行测试类与本实现类有关联；类内用例可能覆盖多个任务，不作为本任务专属用例数 | [final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log) |

真实前后置方法及实现位置：

- [P25WarrantySyncTask.java::work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P25WarrantySyncTask.java:23)
- [P25WarrantySyncTask.java::syncDataQueryByRowHandler](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P25WarrantySyncTask.java:24)
- [P25WarrantySyncTask.java::syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P25WarrantySyncTask.java:38)
- [P25WarrantySyncTask.java::syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P25WarrantySyncTask.java:63)
- [P25WarrantySyncTask.java::syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P25WarrantySyncTask.java:66)
- [P25WarrantySyncTask.java::syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P25WarrantySyncTask.java:70)

多表关联UPDATE JOIN：未定位到该方式独立UPDATE JOIN原件；实际使用上述Java/Owner/API前填充、父ID传递、单表受保护更新或不回填。

## 物理主键与唯一索引（2026-10-14结构快照）

来源结构为既有盘点，不是2026-10-19实时库检查；V481/V482/V484文件存在不表示数据库已应用。当前新列以实际INSERT及结构前置检查为准。

| 原表 | 原物理PK | 说明 |
|---|---|---|
| fb_service | 盘点未给出稳定PK | 技术来源身份仍须按上方业务规则，不能直接套正式id |

| 正式表 | 物理PK | 唯一索引（盘点） |
|---|---|---|
| ast_device_warranty_renewal_record | id | PRIMARY(id) |
