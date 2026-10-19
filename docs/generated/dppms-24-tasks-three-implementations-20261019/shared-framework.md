# 公共前后置流程和验证边界

最新公共审计与项目ID承接规则见[base-sync-original-identifiers-preview-20261019](base-sync-original-identifiers-preview-20261019.md)，优先于下面早期阶段的账号解析及正式范围预览描述。

[返回总表](README.md)

2026-10-19后续更新：[base-sync-13-failures-followup-20261019](base-sync-13-failures-followup-20261019.md)。新P21修复预览结构前置，真实执行仍拒绝未就绪结构；下列公共规则保持。

本报告只盘点既有实现和回执。下述是实现中的执行流程；初版汇总未运行，随后13项跟进已按授权预览并仅重载59284。没有正式业务写入、迁移或真实同步。

## 三种实现的边界

| 方式 | 输入与身份 | 写入及前后置 | 当前验证界限 |
|---|---|---|---|
| A 原通用/Owner | 24份原身份候选；19个业务适配器、5个TABLE_MAPPING | 业务Owner/API或GenericSyncEngine；原线组织、账号、项目范围与DIRECT显式隔离 | 当前候选尚未全部保存或真实预览；旧组件回执不能覆盖新版候选 |
| B 独立直写 | 独立任务ID/来源绑定；保留原任务 | Direct* Mapper写正式表；复用原解释器和公共授权/扩展能力。P11/P14/P15/P21/P23仍复用通用引擎 | 既有真实预览/失败逐项引用；P01另有一次既有非预览SUCCESS |
| C 新虚基类 | BASE_Pxx；int_base_sync_*独立任务、运行、来源身份；原连接能力复用 | AbstractSyncTask/DefaultSyncTask模板、专用来源/目标XML、运行内目录；独立正式父身份 | 当前全部24项都有既有真实API终态；14 READY、10 FAILED，READY含P30空输入 |

## C 模板的实际顺序

`BaseSyncService`的权限/任务/连接/租户/容量检查 → 新运行参数 → `work`中的具名步骤 → `syncDataBefore` → 全量或流式读取 → 每批`syncDataValidate` → `syncDataInsertBefore` → 动作统计 → 仅正式分支INSERT/UPDATE和`syncDataInsertAfter` → 仅正式分支`syncDataAfter` → `syncDataFinally`及会话缓存清理。

预览可以读取正式库、验证引用和生成动作统计；不运行目标业务DELETE/INSERT/UPDATE、来源所有权保存、地址或扩展初始化。运行回执、审计/登录等技术记录与正式业务表写入分开。正式分支由Spring事务约束，失败不记完成；预览失败read=0不等于原表未读或无数据。

少量公司、组织、人员、字典等目录按任务实际代码在本次运行内复用；目标既有行按批/业务键查询，不能把这两类查询写成“所有表都只查一次”。SQL出自实际来源定义/XML；SQL嵌套与既定范围在汇总时不改造。

## A/B 通用引擎与Owner

TABLE_MAPPING按配置的targets/keys/mappings/lookups、目录白名单及目标实际Schema解释。插入按行列签名编译多VALUES；更新按列签名编译CASE，按租户/删除标记/本批ID受保护执行，执行前已锁目标。不能用报告中的列模板替代引擎的转换、权限、版本和基线检查。

P21/P23的FULL_CACHE_REPLACE是已批准展示缓存替换：先完整转换/校验全源，再在单事务中按租户DELETE并批量INSERT；预览不清理。只有这三张受控缓存表可以走该分支，不能扩大为一般业务表清空。其实际DELETE XML见shared-framework.sql。

Owner路径的部分INSERT由MyBatis-Plus BaseMapper生成，没有现成独立INSERT XML。各任务SQL中`ORM_BASEMAPPER_COLUMN_TEMPLATE`明确只是结构展示，实际省略列/审计填充/typeHandler以DO和运行策略为准；不把该模板当执行日志。

## 关联后处理

A/P15当前COM Owner具有临时目录、合同/公司/部门/发货引用/包件回填及汇总UPDATE JOIN，完整SQL原件已摘录到P15/A-original.sql。其他任务若仅有Java预填充、当次父ID传递、公共API、单表受保护更新或明确不回填，就按该实现列明，不生成一套不存在的UPDATE JOIN。

P25/C按fb_service整表独立历史读取，设备ID不回填；A/B保留各自既定范围。P37/B/C按已批准整表边界，空许可原始空串仍加密并登记1项问题，不占位不跳过；Java AES-256-GCM不是SQL AES_ENCRYPT。整表历史同步不需要借用旧试点项目Owner，A尚未接入此边界的旧路径单列差异。

## 已有测试证据

A：原任务修复报告记录分批有效169项。中间有一轮测试JVM堆不足，之后单项提高测试堆重验通过；不能把失败整轮或单项重验描述为一次完整全库成功。

B：逐任务列出既有预览/失败与能明确关联实现类的测试类回执。复用类的测试可能跨A/B覆盖，只能作为共享代码的验证。

C：最终定向日志包含21个测试类、177项，0失败、0错误、0跳过。以下是该整轮原有结果，不是本轮新执行，也不是每个任务各177项。原生MySQL用例只使用连接内临时表，不替代正式物理外键与真实同步验收。

| C最终测试类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| AbstractSyncTaskLifecycleTest | 14 | 0 | 0 | 0 |
| AbstractSyncTaskStreamingTest | 3 | 0 | 0 | 0 |
| BaseSyncDefaultsTest | 37 | 0 | 0 | 0 |
| BaseSyncIsolationTest | 15 | 0 | 0 | 0 |
| BaseSyncMapperWiringTest | 2 | 0 | 0 | 0 |
| ConfiguredSyncSourceNativeTest | 3 | 0 | 0 | 0 |
| ProductSyncArchitectureTest | 29 | 0 | 0 | 0 |
| CacheSourceNullabilityTest | 2 | 0 | 0 | 0 |
| DeviceBaseMySqlTest | 5 | 0 | 0 | 0 |
| IndependentFoundationTasksTest | 6 | 0 | 0 | 0 |
| IndependentScopeMySqlTest | 5 | 0 | 0 | 0 |
| LicenseBaseSyncTest | 5 | 0 | 0 | 0 |
| NewHistoryTaskMySqlTest | 7 | 0 | 0 | 0 |
| NewTaskPreconditionsTest | 3 | 0 | 0 | 0 |
| P04AccountBaseSyncTest | 7 | 0 | 0 | 0 |
| PrerequisiteCompletionMySqlTest | 2 | 0 | 0 | 0 |
| ProductBaseSyncTest | 6 | 0 | 0 | 0 |
| ReceivableBaseSyncTest | 14 | 0 | 0 | 0 |
| RemainingTaskMySqlTest | 7 | 0 | 0 | 0 |
| WarrantyBaseSyncTest | 4 | 0 | 0 | 0 |
| WarrantySourceMySqlTest | 1 | 0 | 0 | 0 |

日志：[final-20261018T015759.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/final-20261018T015759.log)。

每任务页面的测试关联依据为实际测试源引用具体实现类，详verification-matrix.json中的association与scope。静态Architecture/Isolation检查只能证明相应架构/隔离断言；不作为业务写入验收。未找到单任务回执时明确记为未找到，不凭测试源文件存在判通过。

## 公共SQL和原件

- [公共元数据/目录/缓存SQL](shared-framework.sql)
- [保留MyBatis动态语义的原始节点](shared-mapper-nodes.json)

- [AbstractSyncTask.java](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/pms-module-integration-api/src/main/java/cn/iocoder/yudao/module/pms/integration/api/basesync/AbstractSyncTask.java)
- [DefaultSyncTask.java](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/pms-module-integration-api/src/main/java/cn/iocoder/yudao/module/pms/integration/api/basesync/DefaultSyncTask.java)
- [BaseSyncService.java](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/BaseSyncService.java)
- [GenericSyncEngine.java](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java)
- [GenericCacheRefresh.java](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericCacheRefresh.java)
- [GenericSyncJdbcStore.java](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java)
