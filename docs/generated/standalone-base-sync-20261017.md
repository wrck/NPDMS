# 独立基类同步套件实施记录

记录日期：2026-10-17；最新核查：2026-10-18。本文是实现与验证记录，不替代已审核业务映射，不表示 24 项已经全部完成。

当前实现及实测以本文末尾“2026-10-18 十二项阻断复核与P04正式依据纠正”及 [实现约定](../coding/independent-base-sync.md) 为准。前面的分轮验证是历史记录，其中任务级清理策略、强制行级动作、基类自动保存来源绑定及 P11 的预查/版本筛选方案已由后续明确约定替代。

## 隔离边界

- 新基类位于 `pms-module-integration-api/.../api/basesync/AbstractSyncTask.java`。新任务直接继承该类。
- 参考 SPMS `AbstractSynchronizeTask` 和 `SyncMesShipmentInfoJob` 的执行顺序：`work → syncData → 原表查询 → 可选清理 → 批次前处理 → 插入/更新 → 批次后处理 → 后置处理`。
- 多表业务可以重载 `work`，依次调用不同 `dataName` 的 `syncData`；复杂业务可以重载 `syncData`。批次前处理接收可修改的数据行，完成引用查找、值替换和字段填充。
- 新基类没有 `SyncPlan`，不调用原 `DataSyncAdapter`、直写任务、通用同步引擎或原任务运行服务。连接配置通过既有 `SyncConnectionService.required` 和 `ExternalDataSourceApi.openReadOnly` 复用。
- 按用户最新约定，7 个新任务统一到集成模块 `basesync/task`；每项一个类，专用类型及 Mapper 内嵌。业务 SQL 放在集成模块 `mapper/basesync`，源查询放在 `base-sync-source`，纯别名 SQL 放在 `base-sync-sql`。详见 [实现约定](../coding/independent-base-sync.md)。
- 公共引用 API 由基类默认批次流程调用。默认查询、清理、批量插入、可选更新和影响数校验通过继承复用；来源绑定由相应任务的前后处理调用公共存储方法，任务只重载业务差异。
- 新入口为 `/api/v1/pms/integration/base-sync`。新任务元数据独立存放于 `int_base_sync_task`、`int_base_sync_run`、`int_base_sync_binding`。不查询、更新或复制原任务、运行和绑定表，也不注册到原任务调度器。
- 仅准备了 `sql/migrations/V483__independent_base_sync_metadata.sql`；未对应用数据库执行该脚本。脚本只创建三个新表，不包含历史任务更新、数据复制或自动调度。
- 配置、运行计数、来源指纹使用命名列。原任务配置、原同步类和原 Mapper 均保留。本侧修改为新套件文件，以及为原生 ResultHandler 增加的 integration-api MyBatis 编译依赖；工作树中已有的其他未提交改动不归本侧改动。

## 原任务还原范围核查

2026-10-17，用户明确选择“恢复到本侧会话开始前，保留主线程已有修复”。据本侧会话可追溯的写入记录，新增套件没有修改原同步任务文件，因此本次需撤回的原任务改动为 0，没有执行文件回退。该结论只限本侧会话，不否认此前主线程对原任务的历史修改。

收敛前的隔离检查：当时新套件 67 个文件均为新增路径，与 Git 已跟踪文件的修改集合没有交集；当前有修改的原代码、XML 和配置文件中未发现新 `basesync` 包、新元数据表或新入口引用。此检查用于确认新增套件的隔离范围，不把 Git HEAD 当作本侧会话起点，也不据此覆盖当前工作树的其他改动。主线程已有修复、新增套件、原任务数据库配置与历史记录全部保留；本次未执行数据库操作。

## 新套件任务清单

| 项目 | 新套件状态 | 新类 |
|---|---|---|
| P01 | 208条 PREVIEW_READY；全部引用既有正式目录，业务写入0 | `integration/basesync/task/P01OrganizationSyncTask` |
| P04 | BLOCKED_BY_SPEC；仅独立来源预检，330条任职的正式关系承载缺失，业务写入实现未完成 | `integration/basesync/task/P04AccountSyncTask` |
| P08 | 已实现；真实预览阻断于缺少原审计账号 w02611、x01861 | `integration/basesync/task/P08DictionarySyncTask` |
| P10 | 已实现；真实预览阻断于目标缺少 erp_customer_code | `integration/basesync/task/P10CrmCustomerSyncTask` |
| P11 | 真实SQL Server全量读取8443行，独立预览PREVIEW_READY；未真实同步 | `integration/basesync/task/P11CrmProductSyncTask` |
| P12 | 真实DPPMS全量读取34976行，独立预览PREVIEW_READY；未真实同步 | `integration/basesync/task/P12SupplementProductSyncTask` |
| P13 | 已实现；新元数据多来源归并索引阻断 | `integration/basesync/task/P13ExecutionOrderSyncTask` |
| P14 | 已实现；原订单169组业务键载荷冲突阻断 | `integration/basesync/task/P14SalesOrderSyncTask` |
| P15 | 已实现；缺包件原合同键列、全量容量结构和正式P24输入 | `integration/basesync/task/P15DeliverySyncTask` |
| P16 | 已实现；新元数据多来源归并索引阻断；后续仍需正式新P04审计身份 | `integration/basesync/task/P16ProjectSyncTask` |
| P18 | 已实现；源查询已拆分；新元数据多来源归并索引阻断 | `integration/basesync/task/P18ProjectContractSyncTask` |
| P19 | 已实现；缺正式项目来源身份；另缺PRJ/project四属性定义 | `integration/basesync/task/P19ProjectFactsSyncTask` |
| P20 | 已实现；缺正式项目来源身份 | `integration/basesync/task/P20RealProductSyncTask` |
| P21 | 真实预览失败；两张缓存表的 project_id 仍非空，不造项目ID | `integration/basesync/task/P21ProjectCacheSyncTask` |
| P22 | 已实现；缺正式项目来源身份及后续正式订单行/合同关联输入 | `integration/basesync/task/P22ProductScopeSyncTask` |
| P23 | 真实预览 PREVIEW_READY，35501行 | `integration/basesync/task/P23CollectionPlanSyncTask` |
| P24 | 真实预览 PREVIEW_READY，81547行；81540条可承接，7条按原裁决排除 | `integration/basesync/task/P24ReceivableSyncTask` |
| P25 | 真实预览 PREVIEW_READY，462213行，约15秒；不回填设备ID | `integration/basesync/task/P25WarrantySyncTask` |
| P30 | 真实预览 PREVIEW_READY，0行；缺少正式DPPMS发货数据，业务未闭合 | `integration/basesync/task/P30DeviceBaseSyncTask` |
| P31 | 已实现；缺正式完整发布范围表及发布；联系人还需正式最终客户相关方 | `integration/basesync/task/P31MemberContactSyncTask` |
| P33 | 已实现；缺正式完整发布范围表及发布；后续必须核对正式设备及项目选择范围 | `integration/basesync/task/P33InstallationSyncTask` |
| P34 | 真实预览 PREVIEW_READY，165801行 | `integration/basesync/task/P34FactoryHistorySyncTask` |
| P35 | 已实现；缺正式完整发布范围表及发布；另缺AST/SoftwareVersionRecord八属性定义 | `integration/basesync/task/P35SoftwareHistorySyncTask` |
| P37 | 真实预览 PREVIEW_READY，475468行；空码加密保留并登记 | `integration/basesync/task/P37LicenseHistorySyncTask` |

完成情况仅指这套全新的基类实现，不能用原任务的实现或预览结果替代。当前23项有业务实现，P04仅来源预检并明确阻断，不计为完整业务实现；没有用转调原任务的包装类补足数量。

## 已有七项的执行步骤

| 项目 | 原表查询与前处理 | 清理 | 插入/更新 | 后处理 |
|---|---|---|---|---|
| P11 | CRM 全部 20 原字段，以别名映射 21 个业务字段；按产品编码和来源版本校验，按已审核空值策略补齐 | 不清理 | 新产品插入；有效新版本或同版本遗漏字段补齐，版本保护更新 | 无额外业务回填 |
| P12 | `fb_items` 标签；按产品编码查已有产品，缺失产品检查冲突标签 | 不清理 | 仅插入缺失产品，已有产品保持 | 无额外业务回填 |
| P21 | 租赁与配置两份完整缓存，保留原字段、NULL及时间；检查目标可空关联结构 | 仅当前租户缓存 | 两类缓存批量插入 | 不补造正式项目关联 |
| P23 | 原收款计划15字段，完整校验后替换缓存 | 仅当前租户缓存 | 批量插入 | 不增加业务关联或原时间占位 |
| P24 | 原回款39字段、金额比例及审计；按原文查唯一合同引用 | 不清理 | 显式插入／受来源指纹保护的更新 | 保存独立来源绑定，保留正式公司和合同关系 |
| P25 | 原续保10字段；保留实际日期、标记，参考年数转月数 | 不清理 | 显式插入／版本保护更新 | 保存独立来源绑定；不产生当前维保状态 |
| P34 | 原出厂版本5字段；按完整原值及重复次序识别历史 | 不清理 | 仅插入不可变历史 | 保存独立来源绑定，不补项目关联 |

纯原表别名 SQL 统一位于集成模块 `src/main/resources/base-sync-sql`，包含 P11、P12、P21（两张源表）、P23、P24、P25、P34，共 8 份。它们未在应用数据库执行。
## 验证证据与限制

- 受控工作树工具链和构建互斥锁执行，未并行写入同一 target。
- `targeted-tests-20261017T142913.log`：基类生命周期 10 项、产品 6 项、保修 4 项通过；相关四模块编译通过。
- `targeted-tests-20261017T143616.log`：`BaseSyncIsolationTest` 六项通过，使用新 Mapper XML 与独立 H2 内存库，覆盖旧任务 ID 拒绝、新任务注册、预览不写业务、同号 ID 隔离、失败回滚、租户和权限限制。未运行 Flyway 或连接实际业务库。
- 日志位于 `D:/CodexTaskArtifacts/project-detail-followup/generic-orders-20261015/`。
- 新套件没有调用注册 API，没有产生真实任务记录，没有执行真实同步或真实预览，没有启动、停止、重启或部署任何应用。
- 新元数据表是运行前置；既有已审核业务列仍依赖对应库版本。尚未实施的 17 个任务属于代码工作未完成，不能归类为外部数据阻塞。

## 2026-10-17 继承与 MyBatis 收敛验证

- 清理的是本侧新增的拆分文件；7 个任务及专用类型已收敛为 7 个顶层任务类。移除了本侧新增的 P24 SYSTEM 引用 API，公共审计引用由基类调用公共 MyBatis 查询。未修改原有同步类、原 Mapper 或原任务配置。
- 新增实现约定位于 `docs/coding/independent-base-sync.md`，新目录 `AGENTS.md` 提供就近入口。
- `D:/CodexTaskArtifacts/project-detail-followup/base-sync-consolidation-20261017/inherited-tests-20261017T151316.log`：42 项测试通过，0 失败、0 跳过；包含 10 项生命周期、5 项默认方法、6 项隔离、1 项真实 Spring Mapper 装配、6 项产品、10 项 P24 MyBatis、4 项保修规则验证。
- P24 使用独立 H2 内存库执行源/目标/公共引用 Mapper，覆盖 39 个源字段到 52 个目标字段、金额精度、精确引用、预览无业务写入、重跑、拒绝接管和后批次失败回滚。MySQL JSON 列在测试夹具中使用 VARCHAR 保留 JSON 文本，未验证真实 MySQL 执行计划及物理 JSON 行为。
- 构建持有同一工作树互斥锁；只清理合并前本侧新增文件对应的过期 class/XML 输出，防止增量构建留下重复任务 Bean。
- 当前完成程度为 7/24 有实现；剩余 17 项仍是代码工作，未标记为外部阻塞。本侧未执行真实迁移、注册、同步、预览或服务操作。
- P12 清理非本任务映射字段后，受影响的产品测试和 Spring Mapper 装配测试共 7 项再次通过：`D:/CodexTaskArtifacts/project-detail-followup/generic-orders-20261015/targeted-tests-20261017T151553.log`。其余 35 项对应代码未再修改，沿用上述结果。

## 2026-10-17 原生 ResultHandler 适配

- 已重新完整审阅指定 SPMS `AbstractSynchronizeTask.java` 和 `SyncMesShipmentInfoJob.java`。旧基类使用 iBATIS 原生 `queryWithRowHandler`，内嵌 Handler 负责批次编排；发货任务作为子类编排、批次前填充及专用回填的用法参照，不复制其 MES 专属规则。
- 新基类默认 `syncData` 直接接收 MyBatis 原生 ResultHandler，满批立即处理，查询结束处理尾批。删除本侧自建 `SyncRowHandler` 及另一条手工逐行分支；7 个源 Mapper 全部改为 Query 加原生 ResultHandler，XML 字段映射保持。
- P12 仍先收集完整源数据以检查跨批次的规范化产品编码与标签冲突；P21/P23 仍在完整校验后才清理当前租户缓存。其他四项默认逐批处理；身份键或重复次数集合仍随来源数量增长，不宣称总内存恒定。
- 公共源会话使用前向、只读结果集，MySQL 设置原生流式 fetchSize，SQL Server 使用正数抓取大小；复用已有只读连接配置。公共引用、默认写入和来源绑定仍由继承流程提供。目标写入沿用整次 Spring 事务。
- integration-api 仅新增仓库 BOM 已锁定的 MyBatis 编译依赖，以直接引用框架 ResultHandler；未修改任何原同步任务的类、Mapper 或数据库配置。
- 当前代码完整定向验证：`D:/CodexTaskArtifacts/project-detail-followup/base-sync-consolidation-20261017/inherited-tests-20261017T154120.log`，**49 项通过，0 失败、0 错误、0 跳过，BUILD SUCCESS**。组成：生命周期 11、默认方法 5、隔离 6、Spring Mapper 装配 1、原生源会话 3、产品 7、P24 MyBatis 12、保修 4。
- 新增验证覆盖实际 MyBatis/H2 回调期间首批写入、超限回滚已写业务与来源绑定、P12 跨批次规范化标签冲突、清理异常不掩盖原异常，以及真实 MyBatis 配合模拟 JDBC 的抓取参数和连接释放。原生源会话测试没有连接真实 MySQL/SQL Server，不作为真实驱动内存、网络行为或真实业务预览证明。
- 本次仍未执行迁移、真实任务注册、同步、预览、部署或服务启停；当前新套件实现仍为 7/24，其余 17 项未实现。

## 2026-10-17 恢复参考类默认执行能力

本轮依据用户明确批准的边界，纠正上一版本只保留钩子名称、重新设计默认行为的偏离。没有修改原同步任务。

- AbstractSyncTask 的查询、清理、插入均提供默认实现。按任务内部 SourceMapper/TargetMapper 命名空间和 select/delete/insert + dataName 调用已注册的 MyBatis XML；不是动态拼接 SQL。
- 简单任务无需重写查询、删除、插入；SyncMesShipmentInfoJob 的子类用法体现为 work 编排若干 syncData 调用，专用引用、字段替换、回填留在任务重载方法中。
- 清理与读取方式按每次调用的上下文指定。删除 ClearPolicy、requiresSnapshot、WriteRow、逐行插入钩子和基类自动来源绑定协议。公共引用仍按用户要求在默认批次流程中调用。
- P11/P12/P24/P25/P34 的新增记录改为 XML foreach 一批一条 INSERT；P23 使用默认批量插入，P21 保留双表专用批量操作。需要更新的任务明确调用继承的更新方法，版本与影响数校验保留。来源绑定改由相关任务的后处理调用公共 saveAll，未降低历史或来源保护。
- P12/P21/P23 在每次调用中选择完整读取，保留已审核的跨批次去重、完整校验后再清理行为。其他任务继续原生 ResultHandler 流式逐批处理。
- 目标会话使用应用注入的 SqlSessionTemplate，整次 work 仍由 BaseSyncService 的 Spring 事务包裹；多个 syncData、后置写入和成功回执共同提交。步骤成功回调不是提交完成通知。旧类吞异常和删除失败时放宽范围的行为未引入。
- 受控构建日志：`D:/CodexTaskArtifacts/project-detail-followup/base-sync-consolidation-20261017/inherited-tests-20261017T160218.log`，**55 项通过，0 失败、0 错误、0 跳过，BUILD SUCCESS**。组成：生命周期 11、默认 XML 执行 9、隔离 6、Mapper 装配及批量绑定 2、源会话 3、产品 7、P24 13、保修 4。
- 默认执行测试使用两个独立 H2 数据库，实际运行默认查询、按租户删除与批量插入，验证公共引用先于专用前处理、预览不写入、每次调用独立清理、多步骤失败回滚。P24 实测两行只执行一条批量 INSERT，并保存两条绑定；其他改动的插入 XML 通过真实 MyBatis 参数绑定检查。
- 第一轮出现一处 Mockito 测试替身重设错误，修正后完成上述复验；没有降低任何业务断言。此前 49 项结果仅对应上一实现，以本轮证据为当前代码结果。
- 构建持有同一工作树互斥锁，仅清理本侧已移除或改名的过期 class。未执行迁移、应用数据库写入、真实预览、部署或服务操作。实现数量仍为 7/24，剩余 17 项未实现。

## 2026-10-17 逐方法复核与适配

> 更正：本节早先的“51 项都有去向”不能作为逐方法实施完成的证明。后续执行级复核发现业务参数贯穿、默认语句覆盖、原生列表查询、返回值和子类 work 用法仍有遗漏。当前实现以文末“逐方法实施补正”及对应测试证据为准。

本节对应“请重新检查，逐方法进行适配，不要漏任何一处细节”。它补充上一轮遗漏，不覆盖此前验证记录。参照文件为 `D:/EclipseWorkspace/wenrecaikeWorkspace/SPMS/src/com/dp/plat/task/AbstractSynchronizeTask.java` 和同目录 `SyncMesShipmentInfoJob.java`，两者只读。

### 本轮修复

1. 补齐物理分页入口、分页查询默认命名、同一源会话分页、首页清理、尾页与读取上限探测。
2. 补齐自定义映射语句查询、清理、插入、更新；BySql 仍指 Mapper 语句 ID，不是裸 SQL。
3. 补齐切换已有源连接和本地源查询编排；本地查询借用目标 Spring 会话，包含租户查询参数。
4. 修正完整读取模式提前拆批的偏差：整份前处理完成后再拆批。修正前处理扩行后未重新拆批的问题；删光数据仍调用一次该批后处理。P12 目标引用查询仍按配置批大小读取。
5. 补齐日期范围工具、日志标签、默认行回调开关、重试配置与实际重试过滤。重试使用现有 Spring 原生能力，失败先回滚整个 work，每次运行重新计算重试预算。
6. 保护原生 MyBatis 包装之前的回调业务异常；失败回执校验影响数，保存失败明确报出，不能单独释放运行占用。
7. P21/P23/P34 的专用前置校验先调用父类默认日志。原任务及其 Mapper、配置没有成为本轮修改对象。

### 参考基类全部入口：51 项

行号指向只读参考原件。重载单独列项；“适配”不是保留 iBATIS 二进制签名，而是保留默认执行和重载能力、使用具名对象传参及新框架资源管理。

| 序号 | 原方法/构造入口与行号 | 新实现落点和逐项结论 |
|---|---|---|
| 1 | 构造器(appXmlPath,sqlMapConfig)，72 | Spring 构造注入；ConfiguredSyncSource 构建源 MyBatis factory，目标 SqlSessionTemplate 注入。无需子类加载应用 XML。 |
| 2 | getApplicationContext，91 | Spring 容器负责装配；不重复创建 ClassPathXmlApplicationContext，不保留空实现迫使子类重写。 |
| 3 | execute(JobExecutionContext)，94 | AbstractSyncTask.execute(SyncContext) → work；BaseSyncService 负责登录、权限、租户、独立任务身份与事务。异常上抛，不沿用旧吞异常行为；未自动增加 Quartz 调度。 |
| 4 | work，102 | 默认调用 syncData；子类可顺序组合多个 syncData。旧方法仅刷新 ApplicationContext 的初始化由 DI 接管。 |
| 5 | syncData(dataName,sourceDbName,params)，125 | syncData(SyncContext) 提供默认过程；对象承载数据名、连接、批大小等。清理按已审核任务显式指定，未恢复隐式清表默认值。 |
| 6 | syncData(...,clear)，145 | context.forData(name,clear,useRowHandler)；不沿用 clear=false 即关闭事务的旧联动。 |
| 7 | syncData(...,clear,transaction)，168 | 清理保留；事务由外层 Spring 管理，不能用 transaction=false 绕开整体原子性。 |
| 8 | syncData(...,targetDbName,...,clear,transaction)，191 | 已审核目标为应用目标库；目标会话在 context.target。外部连接只读，不新增外部库写入或分布式事务。 |
| 9 | syncData(...,usePaging)，215 | 分页由 syncDataWithPaging(context) 表达；普通读取模式沿用上下文及任务默认设置。 |
| 10 | syncData(...,usePaging,useRowHandler)，238 | 显式行回调选择及分页选择都保留；分页优先。重试移至 BaseSyncService 的整个 work 外侧。 |
| 11 | syncDataInner，269 | 一个公共引擎承接流式、完整读取、分页三条路径，统一钩子和异常处理；不手工提交 SQL session。 |
| 12 | syncDataInner2，437 | 该重复分页/列表引擎并入同一个新引擎；不复制第二套实现和不同返回列表语义。 |
| 13 | syncDataWithPaging(dataName,source,params)，566 | syncDataWithPaging(context)；默认查询 select+dataName+WithPaging，XML 负责物理分页。 |
| 14 | syncDataWithPaging(...,clear)，589 | context 的 clear 每次独立选择，整个分页过程仅清理一次。 |
| 15 | syncDataWithPaging(...,clear,transaction)，612 | 分页过程统一参加外层 Spring 事务；后页失败回滚前页及清理。 |
| 16 | syncDataBefore(dataName,source,params)，624 | syncDataBefore(context)；目标库默认值由 context 承载，不保留位置参数转发重载。 |
| 17 | syncDataBefore(...,target,params)，636 | 默认开始日志；旧 logParams 的来源、目标、操作人、开始时间由具名上下文及独立运行记录承接。 |
| 18 | syncDataSuccess，656 | 同名步骤成功钩子；成功回执在全部 work、后处理和事务提交成功后成立，不能将步骤日志视为已提交。 |
| 19 | syncDataFail，671 | 同名失败钩子；钩子失败不覆盖原异常。最终 FAILED 回执由服务另开事务保存，日志不输出原 JDBC 正文。 |
| 20 | syncDataAfter(dataName,source,params)，687 | syncDataAfter(context)，默认空钩子。 |
| 21 | syncDataAfter(...,target,params)，699 | 与上一项归并；只在该步骤成功且非 preview 时调用，失败后的资源处理另用 syncDataFinally。 |
| 22 | syncDataQuery，709 | 默认经原生回调收集 List，保留子类覆盖入口，读取超限失败；查询异常释放源连接。 |
| 23 | syncDataQueryByPage，723 | PageRead/context/session/PageQuery 对象；默认语句 WithPaging，offset/limit/tenantId/preview 具名。稳定唯一排序由 XML 给出。 |
| 24 | syncDataQueryByRowHandler，742 | MyBatis 原生 ResultHandler；不保留自定义行回调接口。 |
| 25 | syncDataQueryBySql(无 Handler)，754 | MappedCall<场景Query> → List；保留显式指定映射 ID 及独立结果类型，默认读取上限生效。 |
| 26 | syncDataQueryBySql(含 Handler)，766 | MappedCall + 原生 ResultHandler；复用配置连接，执行注册 XML，回调业务异常不被 MyBatis 包装掩盖。 |
| 27 | syncDataClear，795 | 默认 delete+dataName 与 ClearQuery；额外 count 校验影响数，预览只计数。 |
| 28 | syncDataClearBySql，805 | 显式 MappedCall；语句缺失直接失败，不沿用 ByParams 出错后退到无条件删除。 |
| 29 | syncDataInsertWrapper，843 | 公共引用 → 专用前处理 → 按处理后的数据拆批插入 → 一次专用后处理。扩行、删光、值替换均保留。 |
| 30 | syncDataInsertBefore，893 | 同名可重载批次前处理；收到可修改列表。完整读取为整份列表，分页为一页，流式为一批。 |
| 31 | syncDataInsert(默认Local)，901 | syncDataInsert(SyncBatch)，继承默认 XML 批量插入；非空且非预览才写入。 |
| 32 | syncDataInsert(指定dbName)，910 | 目标统一 context.target 的 Spring 会话；不另开可写外部连接。 |
| 33 | syncDataInsertBySql，919 | MappedCall → 原生 insert；默认调用方检查影响数。不保留旧 List/Map 参数报错后猜测兼容重试。 |
| 34 | syncDataInsertAfter，971 | 同名后处理；本次输入的全部拆分批成功后调用，可存来源绑定或执行专用回填。预览不调用。 |
| 35 | getSqlMapClient，980 | context.target、context.source.open、session.mapper(Class)；为新子类提供原生 Mapper/SqlSession 能力，不保留旧客户端类型。 |
| 36 | getTag()，1001 | 使用 taskCode；不依赖上一次同步的可变 cacheDataName。 |
| 37 | getTag(dataName)，1010 | getTag(context) 使用本次 dataName，不串任务或并发运行。 |
| 38 | initSqlMapClient，1029 | ConfiguredSyncSource.sourceSessions/open；缓存 SqlSessionFactory，按运行创建/关闭源会话，复用保存的连接配置。 |
| 39 | initQueryDateRange，1048 | DateRangeRequest → DateRange(start,end)；包含起止日、偏移、当天截止当前秒；六个旧同义 Map 键不重复保存在公共对象。 |
| 40 | setMaxRetries，1070 | 保留配置方法，默认 3；负值拒绝，每个运行有独立重试预算。 |
| 41 | setRetryIntervalMillis，1077 | 保留配置方法，默认 2000ms；支持 0，负值拒绝；等待交给 Spring。 |
| 42 | setEnableExponentialBackoff，1084 | 保留配置方法，默认关闭；开启使用 Spring multiplier=2，不自己位移或循环 sleep。 |
| 43 | setDefaultUseRowHandler，1092 | 保留配置方法，默认开启；服务创建上下文时采用，子类每次显式 forData 设置优先。 |
| 44 | shouldRetry，1102 | 该钩子真正被使用。默认只接受明确的 SQLTransactionRollbackException；业务、约束、未知连接/IO 失败不盲目重试。 |
| 45 | retry，1111 | Spring 原生 RetryTemplate 在服务层包裹 fresh transaction → 整个 work；不共享 retryCount/lastException，也不会丢失本次读取模式。 |
| 46 | sendMessage，1144 | 不迁入硬编码邮箱/凭据和自动外发。当前替代为脱敏日志及独立失败回执；未接入外部通知，不能声称通知能力等价。 |
| 47 | SyncDataRowHandler 构造器，1178 | 攒批状态成为单次 syncData 的局部变量；原生 ResultHandler lambda，不另造公共 Handler 类。 |
| 48 | handleRow，1192 | 原生回调、非空检查、累计限额、满批 flush；异常透传。 |
| 49 | preprocessRow，1209 | 原件为空方法，并没有通用 trim 实现。新任务自己的字段规则放在校验/前处理，未擅自增加全字段清洗。 |
| 50 | flush，1214 | 满批及结束尾批都调用同一个 syncDataInsertWrapper；失败不继续提交后续步骤。 |
| 51 | getTotalCount，1226 | 单次局部读取计数与 SyncCounts；保留原始批输入计数，不因前处理扩行把读取数扩大。 |

### 示例子类全部入口：15 项

该类提供继承和编排用法，MES 业务不属于这 24 项的新增范围。每个入口都已判定复用方式，不把“流程参照”误写为“已移植 MES 同步业务”。

| 序号 | 示例方法/构造入口与行号 | 适配结论 |
|---|---|---|
| 1 | 带配置路径构造器，41 | 新任务构造注入专用嵌套 Mapper；公共连接和目标会话由框架提供。 |
| 2 | 无参构造器，51 | Spring 装配，不在任务内写死旧 applicationContext/SqlMap 配置路径。 |
| 3 | getApplicationContext，57 | 不再读取旧静态上下文，沿用应用容器。 |
| 4 | work，66 | 同一业务类顺序组合多个 syncData；默认简单任务只调用一个。多步骤共用 Spring 事务。 |
| 5 | syncItems，106 | 简单单表样式：数据名 + 源查询 + 清理开关 + 插入 XML；无需重写通用数据库步骤。 |
| 6 | buildOfficeMap，127 | 多连接引用查询通过 forSource 和场景 Query；引用填充放入任务自己的前处理。不复制共享可变 officeMap 或不属于本次范围的 CRM/SAP/D365 办事处规则。 |
| 7 | syncContract，209 | syncData 后的具名更新可使用 syncDataUpdateBySql 或 TargetMapper；旧临时表 DDL 不迁入，避免隐式提交破坏原子性。 |
| 8 | syncShipment，246 | 前置引用查询和批次值填充可直接重载；保修规则按现有已审核映射，未移植装箱日+90天等 MES 专属计算。 |
| 9 | syncShipmentBarcode，286 | 继承默认单表执行；不要求专用 Service/Helper。 |
| 10 | syncShipmentBarcodeChangeLog，310 | 本地游标查询、外部增量查询、后置更新、本地第二阶段查询均可用场景 Query/Command 编排；增删操作的范围由 XML 明确。未新增 MES 日志任务或来源表。 |
| 11 | syncService，351 | 单表同步后置更新留在任务类，仍参加外层事务；未照搬 MES 状态修改规则。 |
| 12 | syncMarketSystem，379 | forLocalData 对应 Local → Local 读取；复用目标 Spring 会话及 TargetMapper。 |
| 13 | syncDataInsertBefore，404 | 允许引用补齐、值替换、删行、增行；已验证前处理先于拆批。任务专属对象和辅助方法内聚在任务类中。 |
| 14 | xmlToMap，498 | MES XML 日志专属解析，不属于公共同步基类；当前任务没有该来源，不增加无用解析器或泛用 Map。 |
| 15 | main，523 | 不复制直接连接生产配置并写库的启动器；采用受权限保护的独立任务入口，没有执行真实注册或同步。 |

### 当前 7 个新任务的受影响方法

| 任务 | 方法复核范围及结论 |
|---|---|
| P11 | work、流式查询重载、批次前置、计数/插入、fill/businessValues/code/validate/conflictsAtSameVersion：仍保留 CRM 权威、20 源列/21 别名、遗漏字段补齐、同版本非空冲突拒绝；默认源读取和批量写入继承基类。 |
| P12 | work、syncDataValidate、syncDataInsertBefore、计数/插入：整份规范化去重及冲突标记在写入前完成；整份前处理时目标引用分批查询，既有产品不覆盖；插入继续继承默认拆批。 |
| P21 | work、完整读取、syncDataValidate、syncDataInsertBefore、缓存清理与批量插入：租户缓存与可空关联保护；不补造正式项目ID。 |
| P23 | work、syncDataValidate、syncDataInsertBefore、清理与插入：原15字段和NULL／时间精度保持，完整校验后替换租户缓存。 |
| P24 | work、原生逐行查询、批次前置、计数／插入／更新、fingerprint／values／validate：39字段与审计、金额精度、来源基线及正式关联保护。 |
| P25 | work、原生逐行查询、批次前置、计数／插入／更新、convert／date／flag：保留原10字段，年份转月，日期与标记不生成当前维保结果。 |
| P34 | work、原生逐行查询、批次前置、计数／插入与fingerprint：5个原字段及重复历史保留，禁止覆盖已变化的不可变历史。 |

以上是方法与框架适配核查，不替代对正式库字段、真实来源值域和实际数据的再验收。任务代码、XML 的已审核业务语义没有因适配被放宽。
### 明确边界

- 基类 51 项和示例 15 项都有逐项去向。Spring DI/事务/连接生命周期替代的入口、重复引擎、通知及 MES 专属方法已明确说明，不伪造同名空壳。
- 新套件仍只有 P11/P12/P21/P23/P24/P25/P34 共 7 项实现；P01/P04/P08/P10/P13/P14/P15/P16/P18/P19/P20/P22/P30/P31/P33/P35/P37 共 17 项未实现，不能据本轮基类适配声称 24 项全部完成。
- 目标始终为应用管理的目标库，外部连接只读；事务不可关闭。分页保持同一源会话，但并发来源数据的一致性仍取决于现有连接隔离级别及具体查询；XML 必须使用稳定唯一排序。
- 多步骤 preview 不会模拟前一步“本应插入”的目标数据，本地后续查询只看当前已有数据。该能力的测试证明实际执行事务编排，不证明任意组合业务的完整预览。
- 本轮未执行迁移、真实注册、真实同步/预览、部署或启停服务。原同步任务全部保留；没有执行任何还原或覆盖主线程修复的操作。

### 本轮验证结果

- 最终受控构建：`D:/CodexTaskArtifacts/project-detail-followup/base-sync-consolidation-20261017/inherited-tests-20261017T163527.log`，**73 项通过，0 失败、0 错误、0 跳过，BUILD SUCCESS**。生命周期 11、默认 XML 执行 22、任务/事务隔离 11、Mapper 装配/绑定 2、原生源会话 3、产品 7、P24 13、保修 4。
- 新增 18 项验证覆盖真实 MyBatis/H2 的物理分页、同一会话及清理次数、空分页、超限回滚、前处理扩行与删光、完整读取先前处理后拆批、切换连接、自定义 Query、Local 第二阶段读取未提交数据、后置更新失败回滚、裸 SQL 拒绝、预览写入阻断、日期范围与闰日。
- 服务级验证覆盖可重试失败后业务和绑定完整回滚、重试耗尽、下一运行重试预算重置、默认行回调设置、不可重试错误/线程中断，以及失败回执冲突后保留运行占用并明确提示。
- 首轮 68 项中，两项旧断言仍期待“完整读取先拆批再前处理”；按参考原件 843 行的实际顺序纠正断言，并增加真实 XML 行扩展/整份前处理测试。业务授权、租户、版本、历史、失败回滚等断言未降低。中间 73 项通过后，完成日志接入和子类 super 调用再跑最终上述结果。
- 测试数据库均为唯一命名的独立 H2 内存库，源驱动测试使用模拟 JDBC；没有连接真实业务库。P11/P12/P23/P25/P34 的部分 SQL 验证为参数绑定，不能冒充真实数据库方言、性能或真实任务预览验收。

## 2026-10-17 逐方法实施补正

用户指出上一轮仍遗漏细节后，本轮修改执行方法及调用方，没有把对应表中的“已替代”当作已实施依据。

| 参考方法 | 本轮直接实施的缺项 | 执行证据 |
|---|---|---|
| execute / work | 恢复 void work 顺序编排；execute 在每次尝试创建新的业务参数和运行计数，汇总各个 syncData 的计数。已有 7 个任务全部调整源签名与调用方。 | 多步骤 work 合计计数；重复执行同一运行输入时参数、日志和计数重新创建；服务重试回滚用例继续通过。 |
| syncData 各入口 / syncDataInner | 恢复 List<T> 返回值：完整读取返回处理列表，分页返回各页合并列表，原生流式返回空列表。syncDataInner 恢复 protected 扩展入口。 | 三种读取方式实际执行并检查返回值，子类没有重写公共查询/删除/插入。 |
| syncDataBefore / Success / Fail / After | SyncContext<P> 贯穿同一个具名业务参数对象，前置查询可填引用，批次处理可改变参数供后置使用；StepLog 提供开始/结束、步骤状态、计数、错误类型。失败更新步骤状态。 | 前置从目标库读引用 → 默认源条件查询 → 专用填充 → 默认插入 → 后置参数更新；后置失败整次回滚。 |
| syncDataQuery | 改为真正的 MyBatis selectList，不再把完整列表查询绕到 ResultHandler。P12/P21/P23 的 SourceMapper 同步声明 List 返回值；P21 继续共用一个源会话查询两表。 | 对真实 MyBatis 会话验证调用 selectList 且未调用 Handler 入口；既有 P12 完整校验及跨批重复规则保留。 |
| syncDataQueryBySql 两个重载 | 无 Handler 使用 selectList；带 Handler 使用原生回调，显式 null Handler 使用列表分支。同步读取前清理会话本地缓存，避免 Handler 的空缓存污染后续列表查询。 | null Handler 用例；原“第二步骤应失败并回滚”测试在本轮首先暴露空缓存缺陷，修复后恢复原失败及回滚断言。 |
| syncDataQueryByRowHandler / syncDataQueryByPage | SourceQuery<P>/PageQuery<P> 携带业务参数，XML 使用 parameters 的明确字段；默认流程接受本次 query 语句覆盖，分页包含 offset/limit，不要求任务另写执行器。 | 同一增量参数对象分别走完整、原生流式、物理分页；自定义分页语句使用业务条件与 offset。 |
| syncDataClear / syncDataClearBySql | 有业务参数时默认选 count/delete + dataName + ByParams；显式 count/delete 语句可覆盖默认选项。查询、计数、删除收到同一业务范围。缺失或异常不退到无条件删除。 | 仅替换指定 ID 范围，保留同租户范围外行和其他租户行；自定义清理语句执行。 |
| syncDataInsertWrapper / Before / Insert / InsertBySql / After | SyncBatch<T,P> 在引用、前填充、插入及后填充共享参数；批量 INSERT 可绑定 context.parameters；可按调用覆盖 insert 语句及 batchSize，扩行后拆批保护保留。 | 不重载插入方法也能使用自定义 INSERT，并实际绑定前置查询结果；前后钩子收到同一个参数对象。 |
| syncDataUpdate / 更新辅助方法 | RowParameter(row,context) 保留行级版本检查，同时可读取同次业务参数；后置更新辅助方法在 preview 下不写入。 | typed 参数后置更新、预览无写入；P24 原生 MyBatis 版本及指纹用例继续通过。 |
| getSqlMapClient / 连接切换对应入口 | 上下文复制保留业务参数和整个 work 的计数；每个数据步骤独立日志、独立语句选项；Source/Local 查询保持原有会话所有权。 | 参数跨外部同步和 Local 第二阶段复用；后续本地查询读取事务内前序写入；未手工提交/关闭 Spring 会话。 |
| getTotalCount 对应统计 / finally 异常路径 | 查询行数在校验过滤之前记录，前处理删除或增加行不改变原始读取数；最终清理失败也调用失败钩子，原异常仍不被后续异常覆盖。 | 校验过滤一行仍记录读取三行；成功 SQL 后清理失败触发失败钩子；已有原异常保护测试保留。 |

新增参数类型是任务自己的 P，例如测试中 IncrementParameters 的 afterId、maxId、referenceId、reference、prefix 和后置状态，全部是命名字段。它不是通用查询 Map，没有引入任务专用的顶层类、Service 或 Helper。

本轮的可执行参照为 BaseSyncDefaultsTest 内的 IncrementTask：只声明任务元数据/参数，使用 void work 编排，重载专用前后处理，直接继承查询、条件清理和插入。该示例在隔离 H2 上执行真实 Mapper XML，覆盖原 SyncMesShipmentInfoJob 所需的参数与引用传递方式；没有把其 MES 专属业务、临时表 DDL 或外部邮件发送带入新任务。

代码契约见 `docs/coding/independent-base-sync.md`。列表与 ResultHandler 的区别遵循 [MyBatis Java API](https://mybatis.org/mybatis-3/java-api.html)，目标事务继续由 [MyBatis-Spring](https://mybatis.org/spring/transactions.html) 管理。

本轮仍只操作本侧新套件及对应测试/文档，未修改原任务、原配置或主线程修复；没有执行迁移、真实注册/同步/预览、部署、端口或服务操作。业务任务实现数量仍为 7/24，本节不表示其余 17 个任务已实施。

最终验证：`D:/CodexTaskArtifacts/project-detail-followup/base-sync-consolidation-20261017/inherited-tests-20261017T170311.log`，**85 项通过，0 失败、0 错误、0 跳过，BUILD SUCCESS**。组成：生命周期 13、默认 XML 执行 32、任务/事务隔离 11、Mapper 装配/绑定 2、原生源会话 3、产品 7、P24 13、保修 4。构建使用工作树固定工具链及同一互斥锁；没有连接实际业务数据库。

## 2026-10-17 保留全部读取能力并落实默认流式批次释放

本轮按用户纠正，撤回将默认流式内存约束扩大为禁止全量读取的改动；完整读取、物理分页、结果返回、前处理扩行后拆批、null Handler 的列表分支及默认读取设置均保留。现有业务任务没有作为基类设计依据，也没有修改任何业务任务实现。原参考的 51 个方法/构造入口按功能归组核对，说明见 `docs/coding/independent-base-sync.md` 第 6 节。

新实施内容：

- 默认流式路径只维护当前输入批次，满批同步执行后释放，尾批结束同样释放；源读取中途失败时也清理尚未处理的半批。没有完整源结果累积，返回空列表的参考语义保持。
- 每批处理完毕清理目标会话一级缓存，预览也执行，避免只查不写时引用查询结果跨批保留。受控源查询前后清理会话缓存；事务仍交给 Spring，没有因清缓存而提交。
- 清理和 finally 钩子的后续异常保留为 suppressed，原始读取或写入错误继续向上抛出；新增双重清理异常验证。
- 保留显式全量和分页对应的列表返回；这两种模式的内存会随所保留的结果增长，不宣称属于默认流式的批次内存上界。保留原批次前处理增删行、拆批 INSERT 和后填充用法。
- `forData(name, clear)` 和 `forLocalData(name, clear)` 提供默认流式的简写；原三参数显式模式入口继续可用。新增借用源 SqlSession 的原生 ResultHandler 查询重载，资源仍由打开方管理。

独立 `AbstractSyncTaskStreamingTest` 不引用现有业务任务。源 JDBC 按 next() 产生 10,000 行，原生 MyBatis 映射每行分配 64 KiB 载荷；若全部保留约为 625 MiB，验证 JVM 堆上限为 256 MiB。实际通过：

- 128 行/批的预览：78 个满批及 16 行尾批，读取下一批前检查上一批条目已释放、目标一级缓存为空，目标数据没有改变。
- 2000 行/批的写入：5 批写完 10,000 行，使用默认 Mapper XML 插入，源结果集、语句和连接均关闭。
- 最后一个批次失败：前 78 批及目标清理全部由 Spring 回滚，原始目标行保留，失败批数据及源资源释放。

第一次回归的 88 项中有 1 项失败：产品测试原先断言目标会话“完全无调用”，新加入的 clearCache 被当成写操作。该断言已改为只允许 clearCache 并禁止所有其他目标会话调用；没有放宽禁止插入、更新、删除的检查，没有改动产品任务代码。

最终证据：`D:/CodexTaskArtifacts/project-detail-followup/base-sync-consolidation-20261017/base-streaming-tests-20261017T172650.log`，**89 项通过，0 失败、0 错误、0 跳过，BUILD SUCCESS**。包含原 85 项回归、3 项独立万行验证和 1 项清理异常保护验证。构建沿用工作树固定工具链和同一互斥锁。

验证边界：源为模拟 JDBC、目标为隔离 H2，证明基类的 MyBatis 映射、批次生命周期及 Spring 事务；未做真实源驱动堆分析或现有业务子类跨批缓存审计。原任务、配置、主线程实现均未操作，没有执行真实数据库迁移、任务注册、同步、预览或服务启停。

## 2026-10-17 用产品同步验证整体架构

**结论：产品功能执行链通过；P11 子类的流式内存要求未闭合，不能据此判定整体架构全部通过。**

本次使用新套件真实 `P11CrmProductSyncTask`、`P12SupplementProductSyncTask`，没有重载其查询或写入来替代生产执行。`ProductSyncArchitectureTest` 串联实际 `BaseSyncService`、`ConfiguredSyncSource`、源/目标 Mapper XML、目标 `SqlSessionTemplate`、Spring 事务及独立运行回执。产品任务中的业务前处理仍在任务类内；普通查询、批量插入、条件更新、异常处理和事务能力从基类及公共运行入口复用。没有改造基类来迁就产品任务。

源和目标使用三个唯一命名的 H2 内存库，分别启用 MSSQLServer、MySQL、MySQL 方言模式；源配置入口在测试中返回 3→7、4→8 的连接引用，实际 JDBC 连接由 ConfiguredSyncSource 打开并关闭。权限/用户及连接配置 API 为测试替身，产品类、Mapper 查询、写入、事务和回执 SQL 都实际执行。目标结构按 V110/V481 的已批准产品字段建测试表；V483 只作为临时 H2 元数据表的 DDL 夹具读取，没有运行 Flyway 或连接应用数据库。

| 验证场景 | 实际结果 |
|---|---|
| CRM 查询全部已批准字段 | 20 个源字段映射到 21 个业务字段，实际读取、实际插入、再从目标 Mapper 读回逐项断言；覆盖此前遗漏的分类、类型、价格、组织、CRM 状态、阶段及生命周期时间。 |
| 预览后执行 | 预览仅写新套件运行回执，产品 INSERT/UPDATE 调用为 0；执行后生成产品，回执 COMPLETED，并释放任务占用。 |
| 重复源版本及重复执行 | 源窗口查询选中更新版本；相同数据再次执行计为 UNCHANGED，不发更新 SQL。 |
| 较新、较旧版本 | 新版本通过基类受保护 UPDATE 更新；旧版本不覆盖；源空字段保留已有值，document_id、创建人及创建时间保留。 |
| 同版本遗漏字段补齐 | 允许填补既有空字段；同版本改写非空事实则失败，并保留原值与版本。 |
| DPPMS 产品补齐 | 使用基类完整读取路径；归一后同编码合并，只插入缺失产品。已有 CRM 产品即使收到冲突标签也不改写。4 条源行归并后，插入 1、保留 1；重放不新增。 |
| DPPMS 新产品标签冲突 | 冲突在第一次 INSERT 前被拒绝，不部分插入同次其他产品。 |
| 末批失败及租户隔离 | 第一批实际执行 INSERT，后一批状态非法后整体回滚；其他租户同编码产品保留。 |
| 乐观锁冲突 | 在实际 UPDATE 前制造目标版本变化，XML 影响数为 0；早先插入及版本变化一起回滚，回执 FAILED。 |
| 目标缺少已批准业务字段 | 查询失败，不退到少字段写入，不产生产品写入。 |
| 六个时间字段精度 | 分别构造亚毫秒输入；预览、执行均拒绝，避免 datetime(3) 静默损失精度。 |
| 10,000 个产品 | 预览 INSERT/UPDATE 调用为 0；batchSize=128 时实际发出 79 批 INSERT，读取及插入均为 10,000；重放 10,000 条全部 UNCHANGED，未增加写入次数。此项验证批次执行，不代表 P11 总内存恒定。 |
| 原任务与源连接 | 每个用例结束核对原任务定义、占用和运行回执均未变化，所有源连接关闭。此处原任务为隔离库保护夹具，未访问实际原任务。 |

字段读回断言包括：product_code、product_model、product_name、product_desc、technical_spec、product_category、product_type、list_price、crm_status、business_unit、product_line、org_id、source_created_at、source_updated_at、crm_state_code、crm_status_code、product_stage、end_of_sale_date、end_of_support_date、end_of_life_date、last_renewal_date。产品没有专用业务后填充或后置更新步骤；没有为覆盖钩子而编造产品业务。P11 采用流式、P12 采用显式全量；分页及通用前后处理仍由本轮一起执行的基类回归覆盖。

### 实际暴露并修复的问题

原 P11 没有校验源时间与目标毫秒精度是否兼容。精度失败用例首次运行显示预览正常放行，不能保证实际写入无损。修复仅在产品校验中调用基类已有 `checkMillisecond`，覆盖源创建/更新时间及四个生命周期时间，不重复实现转换器，也不放宽或变更目标字段。

测试配置曾因 H2 将方括号字段视为大小写敏感而无法执行原 SQL；只调整 MSSQLServer 测试库的标识符大小写配置，生产 Mapper XML 未为 H2 改写。源查询故障消除后，重新验证实际产品失败路径，未把早期 SQL 故障误计为业务校验通过。

### 未通过项：P11 跨批编码集合

`P11CrmProductSyncTask.syncDataQueryByRowHandler` 在读取开始时创建 `HashSet<String> identities`，每条产品编码归一后加入，直到整个源结果结束才释放。10,000 个不同产品就会保留 10,000 个编码，空间随总行数增长。这违反用户“流式只保留当前批次数据”的约束，属于产品子类实现问题，不是正式数据、授权或库版本阻塞；上述 104 项功能验证没有把这一项计为通过。

后续整改应让重复判断在源查询/专用预检中完成，或建立能证明归一后相同编码相邻的稳定读取顺序，再使用常量状态检测。必须保留原归一和跨批冲突规则；不能简单删除重复校验、只检查批内重复，或把任务改成全量模式来宣称流式内存问题已解决。本轮没有扩大改写产品身份及源排序规则。

最终证据：`D:/CodexTaskArtifacts/project-detail-followup/base-sync-consolidation-20261017/product-architecture-regression-20261017T174415.log`，**104 项通过，0 失败、0 错误、0 跳过，BUILD SUCCESS**；其中新增产品链路 15 项，其余为上一轮 89 项回归。构建使用固定工具链和工作树互斥锁。它证明隔离环境的执行、映射和事务行为，不替代正式 SQL Server/MySQL 驱动、真实数据预览或真实运行验收。未修改原任务实现/配置，未启动服务，未执行真实迁移或同步。

## 2026-10-17 P11 跨批编码内存问题修复

**结论：上述 P11 无界编码集合已移除；隔离回归 116 项全部通过。正式 SQL Server 的原生执行及执行计划未在本轮实测。**

本轮只修复新套件 P11 及其验证，不改基类、P12、原任务或原任务配置。全量读取、分页、字段映射、原始编码取最新版本、事务和重复编码拒绝规则均保留。

- P11 源 XML 仍先按原始 itemCode 的既有窗口规则选最新版本。之后在查询内生成独立的排序键，按 Java trim 的 U+0000～U+0020 范围逐次去除首尾字符，保留中间字符、大小写、重音、非 ASCII 空格及原始字段。
- 排序键转为完整 VARBINARY(MAX) 排序，确保 Java 归一后相等的编码相邻，不依赖源库大小写/重音排序规则；没有把归一后重复记录合并或丢弃。空值和空白源行仍交给原校验拒绝。
- 行回调只保留上一条编码，在下一条编码相同时拒绝；这个状态在每次读取时创建，不放入单例任务。额外跨批状态为一个字符串，空间不再随源行数增长。
- 源查询使用单条递归 CTE，原始字段通过静态 MyBatis SQL 片段复用；不再回连重复计算的来源结果。每次递归至少去掉一个首尾字符，MAXRECURSION 0 避免合法长空白超过默认递归深度，原查询超时仍为 60 秒。

SQL 依据：[SQL Server CTE](https://learn.microsoft.com/en-us/sql/t-sql/queries/with-common-table-expression-transact-sql)、[UNICODE](https://learn.microsoft.com/en-us/sql/t-sql/functions/unicode-transact-sql)、[LEN](https://learn.microsoft.com/en-us/sql/t-sql/functions/len-transact-sql)、[VARBINARY](https://learn.microsoft.com/en-us/sql/t-sql/data-types/binary-and-varbinary-transact-sql)。LEN 会忽略末尾空格，因此使用尾部哨兵计算实际字符长度；不使用需要更新库版本的 TRIM 字符列表扩展。

### 本轮验证

1. 先新增旧批编码释放验证：源回调保持运行，读取第 256 条及第 10,000 条时通过弱引用检查首批编码是否可回收。旧实现实际失败，日志 `product-stream-memory-20261017T175458.log`；修复后通过。此项使用逐条提供数据的源会话替身和真实 P11/基类流程，不预先保存全部源行，也不由目标测试替身积累查询参数。
2. 原生 Mapper/XML 链路覆盖全部 33 个 trim 边界字符、混合空白、超过 100 次的首尾修剪，以及内部制表符、大小写、重音、非 ASCII 空格、补充字符的保留与相同编码相邻性。
3. NUL、制表符、回车、U+001F、普通空格造成的归一重复，在 batchSize=1 时均使预览失败；执行时前两批实际 INSERT 后被 Spring 整次回滚。重复判断状态在预览失败与随后执行之间不残留。
4. null、空字符串、普通空格、混合制表/回车及 Unicode 空白仍因产品编码缺失而失败，没有被 SQL 过滤掉。
5. 10,000 条真实 H2 产品源记录仍按 128 条/批写入 79 批，预览不写产品，重放 10,000 条全部 UNCHANGED；之前的全部字段读回、时间精度、版本、租户及原任务隔离验证继续通过。
6. 万行验证曾暴露来源结果回连导致的查询超时；改为单条递归结果直接输出后通过，没有调大超时、增加测试堆或降低行数。最终产品链路 26 项、产品规则 8 项，以及公共框架和其他新任务回归共 **116 项，0 失败、0 错误、0 跳过，BUILD SUCCESS**。

最终日志：`D:/CodexTaskArtifacts/project-detail-followup/base-sync-consolidation-20261017/product-architecture-regression-20261017T180348.log`。使用同一固定工具链和工作树互斥锁，测试堆仍为 256 MiB。

验证边界：H2 不原生支持 SQL Server 的递归 CTE 标记、UNICODE 函数名和 MAXRECURSION 提示，因此仅在测试 JDBC 连接上转换为 WITH RECURSIVE、H2 的 ASCII 字符码函数并去除递归提示；源 XML 的窗口选择、递归修剪、排序及全部字段实际执行，没有使用预制查询结果替代。该转换不进入生产连接或任务代码。未连接正式 SQL Server/MySQL 做执行计划、排序规则或驱动堆验证；未执行真实迁移、任务注册、同步、预览、部署或服务启停。

## 2026-10-17 P11 继承公共默认同步类

依据本侧用户新增要求“按新的虚基类实现默认类，再实现 P11”，以及“itemCode 唯一；首尾修剪直接放查询语句”。该结论取代上一节递归修剪、归一排序及重复编码检查的实现方案，不改变已审核的产品字段和版本保护。

- 新增 DefaultSyncTask<T,P> extends AbstractSyncTask<T,P>，继承原有流式、全量、分页、引用处理、事务内执行、删除及前后置能力。只补充可选的批次预处理 SELECT 和 INSERT/UPDATE/UNCHANGED 分派；没有复制读取循环、连接管理或事务。简单追加/清理后插入任务仅需对应 Mapper SQL。
- 可选预处理返回一条输入对应的一条结果，缺失映射或条数不符直接失败。全量读取保留整体前处理语义，但引用查询仍按 batchSize 拆分；目标查询前后清理 MyBatis 本地缓存。没有新增任务专用服务或辅助类。
- P11 继承默认类。SQL Server 源查询直接查询 DPtech_v_product_info_single，使用 LTRIM(RTRIM(t.[itemCode])) AS [product_code]，完整保留 20 个源字段至 21 个业务字段的映射。纯别名 SQL 同步修改。删除递归 CTE、ROW_NUMBER 选最新、Java 修剪和跨批去重回调，保留固定唯一键排序。
- P11 目标预处理 Mapper SQL 按租户和编码关联，进行非空值补齐、来源保护、旧版本跳过、同版本已存在事实冲突检查和写入动作判定。字符串事实采用完整二进制比较，避免大小写、重音或尾空格被数据库排序规则吞掉；目标唯一键比较规则造成不同编码命中时失败，不悄悄改身份。
- P11 Java 只保留专用状态/容量/精度校验、sourceVersion 文本及新行 ID 填充。精度检查发生在目标 CAST 之前；新增、更新、计数、跳过复用默认方法。原创建字段和 document_id 保留，UPDATE 继续按 tenant/id/expectedVersion/deleted 校验影响数。
- P11 clear=false，未启用 DELETE。公共类的显式 COUNT/DELETE、租户范围及失败回滚已由真实 Mapper/H2 用例验证。

验证：使用工作树工具链和同一构建锁，限定 -Xmx256m。公共默认类/基类、隔离、原生源连接及其他未受后续 SQL 修复影响的检查 85 项通过；最终重跑 P11 产品架构、产品规则与 Mapper 装配 37 项通过。去重后有效结果共 122 项，0 失败、0 错误、0 跳过。没有把首轮失败记为通过：源查询投影遗漏和预处理批次序号类型未声明均已定位、修正并重跑受影响检查。

最终产品证据：
- H2 中运行生产 P11 类、Mapper XML、ConfiguredSyncSource、BaseSyncService 和 Spring 事务；10,000 行预览 0 业务写入，执行按 128 条产生 79 次 INSERT，重放 10,000 条全部 UNCHANGED。
- 原生 ResultHandler 回调仍进行时，已完成首批编码的弱引用释放检查通过；无跨批累计编码集合。
- 21 业务字段、来源键、创建字段、版本、同版本补缺、同版本事实冲突、旧版本跳过、毫秒精度、租户隔离、软删除/未审核来源拒绝、目标乐观锁冲突整次回滚均通过。
- 原来的 CRM 重复源键/递归修剪用例按新唯一键裁决替换为直接 SQL 首尾空格修剪、唯一键约束和字符串事实精确比较用例；P12 的重复补充来源保护用例保留。

日志：
- D:/CodexTaskArtifacts/project-detail-followup/base-sync-consolidation-20261017/product-architecture-regression-20261017T182156.log（公共检查通过，包含已修复的首轮 P11 失败）
- D:/CodexTaskArtifacts/project-detail-followup/base-sync-consolidation-20261017/product-default-task-20261017T182454.log（最终受影响 37 项全部通过）

验证边界：本次没有访问正式源/目标库，没有注册或执行环境中的同步任务，没有执行迁移、部署或服务操作。H2 对 MySQL 无长度 CHAR/BINARY 的行为不同，测试只将这两种 CAST 翻译为 VARCHAR/VARBINARY，业务谓词和写入 SQL 保持；测试不替代真实 SQL Server/MySQL 的驱动、语法、锁与执行计划验收。原有任务与本侧已有其他任务实现未修改。

## 2026-10-17 独立产品同步的原生 MySQL 验证

工作树及分支：`C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup`，`fix/dppms-migration-consolidation-20261012`。本轮检查发现独立套件的当前产品实现和18:59的37项通过证据已经存在，因此沿用该实现，不另造第三套产品同步，也不修改原同步类、任务配置或历史。当前继承与SQL结构如下：

- `AbstractSyncTask<T,P>` 提供参考模板的流式、全量、分页、清理、前后处理、公共引用及事务内执行能力；`DefaultSyncTask<T,P>` 提供默认动作分派。
- `P11CrmProductSyncTask` 继承公共默认类，源SQL直接修剪itemCode首尾普通空格，完整映射已审核字段；一批执行一条原生 MySQL `INSERT ... ON DUPLICATE KEY UPDATE`。不预查目标、不按源版本选新旧、不逐行判定动作；同批重复来源键逐个进入upsert，最后值取决于实际输入顺序，源端相同itemCode的相对顺序不作为业务优先级承诺。
- P11每次更新推进目标version，来源NULL字段保留目标原值，目标创建事实、document_id保留；非批准来源、软删除及排序规则合并不同编码的身份由同一SQL拒绝。来源版本保留为文字证据，不作为筛选或覆盖裁决；旧章节中的预查、旧版本跳过、同版本冲突拒绝及重放UNCHANGED不是当前P11行为。
- `P12SupplementProductSyncTask` 直接继承虚基类，按已审核的缺失补齐规则处理DPPMS产品，不更新已有CRM产品；仍使用独立内部行类型、Mapper及XML。
- 新入口与注册/运行/绑定使用`base-sync`及`int_base_sync_*`，不转调原同步任务或原直写执行器。SQL保持在新套件的源/目标XML中，未接入原任务调度。

新增`ProductBaseSyncMySqlTest`，显式设置`BASE_PRODUCT_TEMP_MYSQL_TEST=true`才连接既定24306/npdms_reverify_20261014；仅创建连接内`CREATE TEMPORARY TABLE ast_product_official_info`，不复制正式表、不执行迁移、不创建普通业务表，连接关闭后临时表自动释放。真实生产P11/P12类、基类默认执行和目标Mapper XML在Spring事务中运行；目标SQL未经H2语法转换，来源回调为合成数据替身，因此不冒称真实CRM源端已验收。

7项真实MySQL验证全部通过：

1. 原生行别名upsert一次写入整批，首次新增、重放更新、同批重复键的实际影响数及计数一致。
2. P11预览仅校验来源，业务零写入且不预测目标新增/更新数。
3. 来源NULL回退、人工document_id、目标id和创建事实保留，源时间较旧仍按本次裁决更新。
4. 早批真实写入后，晚批遇到MANUAL身份失败，Spring整次回滚早批。
5. 原生大小写不敏感排序规则不能静默合并不同产品编码，也不能复活软删除记录。
6. 同编码不同租户保持独立。
7. P12仅新增缺失产品，已有CRM记录全部字段保持。

此次合计**44项通过、0失败、0错误、0跳过**（7项MySQL、29项架构链路、6项产品规则、2项Mapper装配），`BUILD SUCCESS`。构建沿用固定JDK25/Maven、工作树依赖仓库和同一互斥锁，测试堆256 MiB。首次启动因Windows PowerShell将JDK原生访问警告当作终止错误而未执行测试；修正验证启动器的标准错误处理、继续按Maven实际退出码判定后，重新完整运行上述44项通过，未改变产品实现或测试断言。

证据：`D:/CodexTaskArtifacts/project-detail-followup/base-sync-consolidation-20261017/product-native-mysql-20261017T192815.log`、`product-native-mysql-protection.json`。正式产品和原同步task/run/binding的计数及最大ID在测试前后均未变；这只是计数/最大ID保护证据，不是全字段一致性证明。本轮修改为新增MySQL验证和纠正过期记录，没有业务实现修补、任务注册、真实同步/预览、迁移、部署或服务启停，未操作59191。

MySQL语义依据：[8.4原生upsert及影响行数](https://dev.mysql.com/doc/refman/8.4/en/insert-on-duplicate.html)。当前P11在preview下不发目标SQL，返回的动作计数为0，故源码预览不证明目标权限、Schema或保护规则一定可写。SQL Server原生驱动、来源排序/执行计划和真实数据运行仍未验收；应用接入所需的独立元数据DDL仍只是准备文件，不能为验收自动执行迁移。

## 2026-10-17 新P11/P12真实来源与预览完成

用户明确授权继续真实来源核查及新P11/P12真实预览，能修复的实现/配置问题继续修复；禁止迁移、真实同步、原任务操作及59191操作，自动迁移保持关闭。工作树及分支仍为本文前述位置。本轮开始时24306/24379可用，59284/19084未监听；隔离库已经存在三张`int_base_sync_*`表及新P11注册记录2111407905813229570，且有早于本轮的失败预览回执。本轮未创建这些表、未运行V483或其他DDL；不将先前“尚未有独立元数据表”的历史边界当作当前实查结果，也不据表已存在推断由谁或如何建立。

### 真实来源核查

真实SQL Server原生JDBC只读汇总返回：`DPtech_v_product_info_single`共**8443行**，`LTRIM(RTRIM(itemCode))`唯一编码**8441**，缺编码0行，最大编码长度9、名称长度92。当前规则保留全部来源行，不用窗口、版本或去重把它缩为8441行；相同编码的先后次序不新增业务裁决。生产P11源XML原样经SQL Server驱动13.4.0.0读完8443行；随后正式新套件P11预览执行了全部字段映射及长度、状态、金额和时间精度校验并成功。

真实DPPMS `fb_items`共**34976行**、去空格后唯一编码34976、缺编码0；生产P12源XML原样经MySQL Connector/J 9.7.0读取34976行。凭据只通过已有受保护配置在进程内使用，没有输出或保存原连接密码、Token或源数据明细。证据`source-audit.json`、`P11-native-source-probe.txt`、`P12-native-source-probe.txt`。

### 定位并修复封装问题

本轮首次加载仅含新P11/P12与公共执行能力的候选包。真实浏览器先通过19084实际登录，再向59284的`/api/v1/pms/integration/base-sync`原有新套件入口请求预览；两个新任务均在源读取阶段以PersistenceException失败。原任务没有参与，正式产品未写入。新P12通过该独立入口注册，ID **2111435600580231169**，DPPMS连接2110305954295414785、batchSize500、maxRows100000。新P11沿用可追溯的已有注册、CRM连接2110439133532688385、batchSize1、maxRows10000，未保存或修改它的配置。

根因是候选包封装遗漏新目录的ZIP条目：`base-sync-source/P11SourceMapper.xml`和P12 XML虽然存在，`base-sync-source/`目录条目不存在，导致Spring `classpath*:base-sync-source/*Mapper.xml`通配扫描返回0。隔离加载同一封装JAR的真实资源扫描确认修复前0、补齐目录条目后2；修复保持所有原模块条目及迁移资源不变，仅新增套件资源目录条目，产品Java/XML业务规则未变。原失败包、日志和两次失败预览回执保留，未覆盖为成功。

保护配置预检及两个新任务空闲核对后，仅重启59284：PID26432→35376，加载`runtime-candidate-v2`，19084 PID34788保持。冻结包只增补两个模块内的新P11/P12、公共基类/入口/元数据Mapper和相应XML（含6个必要目录条目，共74项），没有将其他新套件任务或工作树无关改动装入本次候选包。没有启动或操作59285、59191。Flyway/Flowable自动Schema更新、Quartz/Batch自动启动及事件驱动绑定均关闭。

另一个已修正的验证脚本问题是JavaScript数值精度：19位connectionId以数值传入浏览器时发生舍入，注册请求被服务端拒绝为连接不存在；改为字符串保持精确ID后注册成功。该次拒绝未生成错误连接或任务，也没有改变服务端校验。

### 实际预览终态

|新任务|独立注册ID|真实运行ID|时间（Asia/Shanghai）|终态与实际计数|
|---|---|---|---|---|
|P11 CRM|2111407905813229570|2111436809982627841|20:38:58.768—20:38:59.454|PREVIEW_READY，读取8443；目标动作计数均0，预览不预判upsert新增/更新|
|P12 DPPMS|2111435600580231169|2111436813732335618|20:38:59.654—20:39:00.734|PREVIEW_READY，读取34976，拟新增34976、更新/未变/删除均0|

两次请求均由真实Chromium在应用实际登录后调用新套件preview API，服务端继续校验当前租户、有效用户、执行权限、独立注册及活动运行，未使用权限替身。没有调用execute接口。新套件尚无独立产品UI面板，因此本次证明的是浏览器认证后的真实API预览和数据库终态回执，不声称已有可视化终态页面或完整产品UI验收。也没有进入原任务或比较原任务运行结果。

20:39保护复核通过：**169张保护表**（165张核心业务表及原同步task/run/binding、新套件binding）的计数或计数/最大ID无差异，正式产品仍1条。新P11配置与本轮前快照一致，两个新任务均空闲；两个独立ID的非预览运行数均0。Flyway最后回执仍V473，迁移容器exited，运行参数中Flyway/Flowable关闭均实查为真。这是计数/最大ID保护证明，不冒称所有业务字段逐值一致。

证据目录：`D:/CodexTaskArtifacts/project-detail-followup/base-sync-real-preview-20261017`。最终证据`browser-preview-result-v2.json`、`P11-preview-result-v2.json`、`P12-preview-result-v2.json`、`final-proof.json`；修复前后资源扫描为`runtime-candidate-resource-probe.txt`与`runtime-candidate-v2-resource-probe.txt`。本次仅新P12注册及新P11/P12预览，没有迁移、真实同步、原任务配置/运行/恢复/删除、原结果对照或59191操作。

本次包修复不改变业务类或SQL，复用上一节44项通过的有效证据，没有重复执行无关回归。新增验证是封装资源0→2的直接复现及修复后两个真实预览。真实SQL Server来源读取与产品映射已实测，但不把P11预览0目标SQL解释为真实目标可写性/保护检查已通过，也不据本次预览授权任何正式产品写入或迁移。

## 原表直写精简与后续任务预览

2026-10-17，本轮继续使用 `fix/dppms-migration-consolidation-20261012` 工作树，仅操作独立 `int_base_sync_*` 注册、预览及本侧遗留预览恢复。原同步任务、配置、运行与绑定不操作；没有执行迁移、真实同步或后置关联回填，没有操作59191。以下结果均为新套件，不能由旧任务结果替代。

本轮新增 P08、P10、P30、P37 四个独立类，现有实现为 **11/24**。尚未实现的 **13项**：P01、P04、P13、P14、P15、P16、P18、P19、P20、P22、P31、P33、P35。它们仍属于实现工作，不登记成外部阻塞，也不计为本轮已经闭合。

按用户最新要求，每项先审原表批量读取和必要转换，不新增备份、公司／项目镜像或不必要的业务ID查询；少量引用放在本次运行上下文复用。P08 的类型和字典项原表各读一次，同一只读快照内计算缺失类型，源SQL零嵌套；本次已核实的类型直接供字典项使用。P24 的审计账号目录本次查询一次，不逐批查询合同ID，原合同号和既有正式关联保留。P30 的产品基础目录在本次运行内只读一次；正式发货最新行查询沿用原始来源的一层窗口SQL，没有额外加深嵌套。

P25 现按原表 `fb_service` 全量承接，使用原10字段别名，不依赖项目、合同、发货或设备主档。目标已有 `device_sn`，没有 `device_id`，不增加回填。先在同一只读快照检查一次复合来源身份，再直接读取原记录；没有对包含TEXT的全部原行排序，也没有跨批保存全部来源键。462213条全部完成真实预览，包含15条原始空字符串SN。保留实际日期、原等级／标记、年转月转换及重复历史，不按SN择一。此前使用发货范围和延长超时的失败证据保留，不作为最终方案。

P37 核对既有批准清单的475468条ID和七项非秘密原值，逐行认证加密后才进入批次；不落明文许可码，不调用旧任务或旧Owner，不增加项目／公司镜像。设备及合同ID可空且不作为初次承接前置，保留原SN、合同和产品原文，登记待关联；本次不执行后置关联。原ID472122的空字符串加密保留并在成功预览回执登记 `P37_LICENSE_CODE_EMPTY:sourceId=472122`。没有用占位或跳过记录。

| 新任务 | 最后已完成的真实预览 | 核对边界 |
|---|---|---|
| P23 | 35501行，PREVIEW_READY | 源创建／更新时间均非空，不再要求无实际必要的可空列升级；实际空值仍必须由目标可空结构承接 |
| P24 | Run 2111480412981334017，81547行，PREVIEW_READY | 81540条可承接；7条空合同号按既有裁决排除；100键以内分段核对避免SQL解析栈溢出 |
| P25 | Run 2111480444740603906，462213行，PREVIEW_READY | 2026-10-17 23:32:22.096至23:32:37.135，约15.04秒；单表、无设备ID回填 |
| P30 | Run 2111481771424153602，0行，PREVIEW_READY | 原SQL解析问题已修复；正式DPPMS发货来源为0，登记 P30_FORMAL_DPPMS_SHIPMENTS_MISSING:0，不算业务闭合 |
| P34 | Run 2111480509467103233，165801行，PREVIEW_READY | 原SN当前均非NULL；实际NULL仍验证目标列可空；相同原值按相邻出现序号保留重复历史 |
| P37 | Run 2111480525451595777，475468行，PREVIEW_READY | 2026-10-17 23:32:41.338至23:33:01.767，约20.43秒；登记1条空码问题及可空关联待处理数量 |

当前真实外部缺项：P08 缺少原审计人员的正式平台账号 `w02611`、`x01861`，不创建假账号或猜配；P10 缺少已审核的 `cus_customer_master.erp_customer_code`（工作树已有V481脚本，本轮不执行），该任务在读源前明确阻断；P21 的 `proj_project_lease_product.project_id`、`proj_project_product_configuration.project_id` 均非空，而原缓存不能补造正式项目ID。这些是当前首先触发的前置缺项，不宣称其余未执行路径都已验收。

验证证据位于仓库外 `D:/CodexTaskArtifacts/project-detail-followup/base-sync-remaining-20261017/`。生产解析器补测实际复现了P30的 `BINARY 字段` 语法兼容问题，改用原实现的 `CAST(... AS BINARY)` 后通过；没有通过关闭租户／数据权限解析解决。完整定向验证145项通过，随后P30解析修复的5项受影响验证通过。真实浏览器从19084正常登录后调用59284新套件API并核对数据库终态；新套件尚无专属任务页面，不能把这些API预览写成专属UI闭环。

最终只读复核：11个本侧独立任务均无运行占用，全部运行均为预览，没有一次真实执行。对照本侧预览前的169张受保护表，数量／既有元数据最大ID没有差异；该证据不冒充全字段校验。上述许可、续保、出厂版本、回款和收款计划五张正式目标表仍各0条。Flyway末版仍473，迁移容器处于exited，运行参数保持自动迁移、Flowable结构更新、自动作业与事件驱动关闭。当前59284加载 `runtime-candidate-wave6`；59191未操作。详情为同一证据目录 `final-proof.json`。


## 2026-10-18 剩余13项实现及真实预览

本轮在 `fix/dppms-migration-consolidation-20261012` 分支原工作树继续。12项补齐独立业务处理流程；P04因固定任职关系持久化契约冲突，仅完成独立来源预检并登记 `Q-BASE-P04-TYPED-EMPLOYMENT-20261018`，不能记作账号导入完成。运行时可见24个新任务入口，其中23个有业务实现，P04明确阻断；这不代表24项已全部验收。

仅重启59284一次，加载 `base-sync-next13-20261017/runtime-candidate-wave7/yudao-server.jar`。冻结包只替换新 basesync 类和 XML；两个嵌套模块中其他原类、原配置和全部迁移资源按字节保持。未修改原任务配置、版本、运行或绑定；没有双线对照，没有执行任何真实同步、后置关联或迁移。自动Flyway、Flowable结构更新、Quartz/Batch自动作业及事件驱动继续关闭。59191未操作。

### 实现取舍

- P01四份组织原表各读一次，29码及8项补全部门沿既有批准；既有正式定义只引用，不接管或更新。8条原负责人关联登记待处理，不能用旧任务绑定证明新任务身份。
- P13保留所有原始执行单身份，按原业务键选择完整最新修订；不为可空公司/部门ID反复查表。P14表头先规划、订单行按批处理，仅父订单ID必须填；客户和产品ID不回填。保留原审核的重复业务键载荷冲突阻断，不按MAX拼接不同记录的事实。临时JSON仅供源冲突检查，不持久化原行镜像。
- P15原发货合同、包件、4283326条条码及设备关系分步直接承接；本地合同归集在原事实阶段之后读取一次。运输字典本次读取一次；可空业务ID留空，具名源字段不用source_payload镜像。代码容量上限已支持500万，但注册行的100万熔断和数据库CHECK未改写，不能截断全量冒充成功。
- P16只执行既有新直写任务批准的type10范围，不擅自扩大到其他项目类型。原37字段、独立来源身份、根树及原最终客户相关方按既定规则处理；不接管原项目、不恢复删除记录、不产生当前经理授权。未发布完整项目范围，也未用此type10范围替代正式完整发布。
- P18将无可用索引的巨型联表拆成合同、组、成员桥、规范项目四份只读原表查询，各读一次，按精确键在本次任务内归并。原审计、活动/历史优先级和所有原身份均保留。显式全量归并与P20全清单、P33完整安装历史规划有容量和内存成本，不宣称恒定批次内存。
- P19相关方直接批量写具名列；仅四项已批准的历史状态扩展复用公共EntityExtensionApi，不写项目生命周期。P20真实产品保存原清单及上游精确来源事实，不做设备分配。P22范围和数量明细同事务写入，必要父关联保留，不进行分配确认或推进当前范围版本。
- P31成员和联系人直接写历史表。人员编码保留，非必要user_id/employee_no留空；authorization_confirmed=false，不进入当前授权。联系人必须有正式最终客户相关方，不造客户主档。P33设备ID是安装历史的必要引用，必须通过正式设备及项目选择校验；保留原地址和正式不可变地址快照，不持久化整份原行镜像，不伪造预览地址ID。P35保留软件批次父关联及原SN，设备ID留空；八项已批准历史扩展使用既有公共初始化能力，原NULL/空串/0完整保留。
- P31/P33/P35先固定正式完整发布范围，再按500个原项目键分批读取。没有发布时在读取业务原表前失败，空范围不扩大成全表。公共目录和定义随本次上下文释放，不放入单例Bean。

### 真实预览回执

| 项目 | 新独立任务ID | 预览Run | 终态 | 回执read | 秒 |
|---|---|---|---|---|---|
| P01 | 2111504172530692098 | 2111504172950122498 | PREVIEW_READY | 208 | 0.644 |
| P04 | 2111504175751917570 | 2111504176540446721 | FAILED | 0 | 0.153 |
| P13 | 2111504177542885378 | 2111504178285277185 | FAILED | 0 | 0.125 |
| P14 | 2111504179094777858 | 2111504179669397505 | FAILED | 0 | 16.562 |
| P15 | 2111504249399701506 | 2111504249550696450 | FAILED | 0 | 0.142 |
| P16 | 2111504250456666113 | 2111504250695741441 | FAILED | 0 | 0.144 |
| P18 | 2111504251568156674 | 2111504251786260481 | FAILED | 0 | 0.167 |
| P19 | 2111504252700618753 | 2111504252864196609 | FAILED | 0 | 10.904 |
| P20 | 2111504298947014658 | 2111504299144146946 | FAILED | 0 | 10.187 |
| P22 | 2111504342123180033 | 2111504342265786369 | FAILED | 0 | 5.849 |
| P31 | 2111504367108648961 | 2111504367293198338 | FAILED | 0 | 0.145 |
| P33 | 2111504368203362306 | 2111504368337580033 | FAILED | 0 | 0.118 |
| P35 | 2111504369105137665 | 2111504369264521218 | FAILED | 0 | 0.119 |

13个真实API预览请求均已结束；只有P01通过。失败回执read=0表示未完成步骤，不能据此推断源表为空。P04实际来源预检读取330条，明确写入阻断信息。P20采用显式完整清单读取后因正式来源身份缺失阻断，也不能把回执0理解为未读源。其余未获前置条件的路径不宣称全量预览通过。

19084真实浏览器正常登录及服务重载后首页恢复已验证；新任务经正常登录上下文调用专属 `/base-sync/registrations`、`/base-sync/preview`，没有 `/execute` 请求。新套件无专属UI，不能把API预览记成专属任务页面闭环。

### 外部缺项及可执行处理

| 项目 | 当前证据与下一步 |
|---|---|
| P04 | 330条任职需正式具名关系承载，旧custom_info.employmentAssignments与当前禁止固定关系JSON的规则冲突。仅登记规格缺口，不猜模型、丢关系或复用授权范围表冒充任职历史。 |
| P13 | 当前 `uk_int_base_sync_binding_target` 是同任务/同对象的目标唯一索引，不能承接已批准的多原始执行单指向一个表头。已准备V484改为普通目标索引，来源唯一索引保持；本轮不执行DDL。 |
| P14 | 91572条订单原行归并91239个业务键，其中169个键有不同载荷；380605条订单行归并379522个键，其中10个冲突键均缺orderNumber，涉及40条原行。需要来源权威修正或已有可执行消歧依据；不能任选、拼接、填占位或跳过。20个表头样例及全部10个行冲突键在外部证据JSON。 |
| P15 | 当前缺 `com_shipment_package.shipment_contract_source_key`（已批准V481）；条码4283326条超过现有注册及表CHECK100万，需要结构版本到位及明确500万配置；还没有新的BASE_P24非预览SUCCESS正式输入。当前只读预览不能补写这些业务事实。 |
| P16 | 与P13相同的多来源目标索引阻断；之后还需成功的新P04正式账号来源绑定，不能借旧任务绑定绕开。 |
| P18 | 与P13相同的多来源目标索引阻断。查询拆分已修复，不再将实现超时归为源库缺项；正式项目及合同必须实际到位，当前未运行到这些后续校验。 |
| P19 | 首批缺规范项目原ID的正式身份，`proj_legacy_detail_source`当前0条，新P16尚无正式成功输入。另未发现PRJ/project四属性正式定义，配置ID保持0，不能自动发布或借其他实体定义。 |
| P20 | 缺正式项目来源身份；不把原项目数字ID直接当正式主键。 |
| P22 | 缺正式项目来源身份；后续还需要真实正式合同关系、订单行和办事处版本。未执行到的后续校验不能算通过。 |
| P31 | 缺 `int_sync_project_scope_publication/member` 两正式范围表（V482未执行）及完整成功发布；联系人还需相应正式FINAL_CUSTOMER相关方。不能降成全表读取或复制旧任务绑定。 |
| P33 | 同样缺正式完整发布范围；之后按源SN要求正式设备和当前项目选择授权，缺设备时不造关联。不接管任何既有当前安装记录。 |
| P35 | 同样缺正式完整发布范围；另未发现AST/SoftwareVersionRecord八属性正式定义，配置ID保持0。不自动发布，也不用其他实体的定义替代。 |

`sql/migrations/V484__base_sync_aliases_and_full_shipment_limit.sql` 仅作为可审查的结构修正文件保存，未执行，也未装入候选运行包的迁移资源。已有V481/V482同样没有执行。正式P16发布、账号和业务输入均不得用预览结果冒充成功同步。

### 查询与验证证据

限定3行的真实源SQL检查：原P18巨型联表180秒超时；本次只取消本会话的精确诊断语句，未取消他人查询。拆分后四条分别约0.4/1.0/0.8/2.8秒。P19相关方由86.6秒到2.8秒，P22由37.6秒到1.5秒；这些是LIMIT3诊断数据，不能外推完整导入耗时。SQL保持租户/权限解析，源嵌套最多一层的规范项目选择，不增加索引或修改源库。

本轮最终164项定向检查全部通过，覆盖24个入口装配、原MyBatis XML、公共事务与隔离，以及新成员、联系人、安装和软件批次的预览零写入、真实父ID、不可变保护和后续失败整批回滚。原生MySQL检查只用连接内临时表；临时表保留适用唯一约束及生成列，但不证明物理外键验收。外部共同Location/Extension API用mock隔离，正式缺失条件下对应写入链尚未真实验收；P01/P15/P16/P18/P19/P22也没有完整正式数据下的真实写入验收。

最终保护：178项表计数/最大ID及保护项无差异，24个新任务均无运行占用，新绑定仍0，Flyway末版473。该证据是数量与最大ID保护，不是全字段校验。所有新运行均preview=true。详细结果、来源冲突、前置条件、冻结清单及浏览器证据位于 `D:/CodexTaskArtifacts/project-detail-followup/base-sync-next13-20261017/`。12项阻断仍未闭合，不将本轮记为全部预览通过。


## 2026-10-18 十二项阻断复核与P04正式依据纠正

本节取代上一轮对当前P04和P31/P33/P35的阻断判断，既有回执和首次判断作为历史保留。工作树及分支保持不变。十二项已逐项复核，受影响八个任务执行了十一轮真实API预览请求，全部结束；P04和P13预览通过，其余十项仍有正式数据、来源冲突、结构或正式扩展定义前置。代码有业务流程不等于正式同步验收完成。

### P04已有依据与本轮修复

已重新找到 `Q-LEGACY-IDENTITY-USERS-TABLE-20261009-008` 两列及账号属性最终裁决、`Q-LEGACY-IDENTITY-USERS-EMPLOYMENT-20261009-011` 历史任职承载裁决。正式 `system_users.employee_no/custom_info` 已存在。原来将P04整体判为“任职无正式承载、需另建表”没有充分追溯具体批准，现撤回，并在本轮创建的问题条目保留纠正记录。任职历史使用已经获批的 `custom_info.employmentAssignments`，不解释为当前EHR任职、审批链或项目授权。

用户本轮补充“isSysUser为系统预设用户”。新套件将原值保存为 `custom_info.systemPresetUser`；本轮329个入选t_user的该值全部为0，不据该标志增减管理员权限或改变原账号筛选。原字段清单当时的缺项作为历史保留，以新决定及本节为当前依据。

P04已从预检占位补齐独立账号、历史任职、角色、成员关系和公司部门范围四个步骤。十二张原表/参考表各读一次，引用仅在本次运行内复用；不调用旧任务或旧Owner。1176个逻辑账号、330条入选任职沿原审核处理，含391→88、EHR姓名例外、邮箱尾TAB例外、两组FND别名、五个仅角色账号及原范围排除。未精确关联的汇报人员保留原员工引用、正式userId=NULL，不按同号猜配。只在真实执行分支生成随机初始凭据并散列，不读原密码、不在预览生成凭据。旧admin仅作已批准的审计引用到当前admin1，不接管账号；执行路径对USER_T:0和USER_FND:81仅保存本套件审计引用，不保存账号所有权基线；本轮未实际写入这些绑定。

最终P04 Run 2111517837875126273 为 PREVIEW_READY。回执read=12632是四个步骤的候选行总和，inserted=12605是预览动作数，unchanged=27；不是实际新增账号数，也没有发生业务写入。P04_ORIGINAL_READ=3307是七张业务来源表读取总行数，不包含五张EHR/公司参考目录；排除26个范围外来源账号及3条已裁决孤立资料。不能继续沿用旧任务含重复来源投影的5015来比较新套件读取量。

### 区分实现缺陷与外部前置

- 修正新套件完成状态：新运行正式终态为COMPLETED，旧SUCCESS检查导致P15/P16即使已有新正式输入也无法识别。只修改新套件查询与检查，旧状态及历史不改。
- 修正P04/P13/P16/P18的预览限制：当前多来源目标唯一索引仍阻断真实写入；只读预览继续后续校验并登记执行所需结构，不能把尚未执行的V484误报为所有预览都不能运行。
- 修正P13 SQL：原相关EXISTS与无索引反连接拆成一次有效规范项目编码前缀目录和一份原表流式读取，两个查询各只有一层派生表；原二进制分组MAX、再校验有效、type10范围保持。读取102520条原行，99895条在范围内，2625条不入选。跨批多原始行指向同表头时，预览不伪造插入/更新计数。
- 修正P31/P33/P35接入：原新实现误依赖旧三类型完整发布入口，而新P16限定已批准type10，无法生成该发布。这是新套件接入缺陷，不能只归为缺V482。现只读取新套件完整非预览COMPLETED的P16回执与其来源身份；P16在同一事务刷新全部主档/别名的last_run_id，包括UNCHANGED行，完整成功回执同事务提交。消费者在同一REPEATABLE_READ事务固定该回执并完整装载成员，校验主档数、别名数、原身份指纹、当前项目类型/编码及容量，再形成运行内只读Map。空范围保留为空，缺完成回执明确阻断。未操作或替换旧不可变发布历史，不依赖旧任务、旧绑定或旧发布表，不新增结构或执行DDL。
- 改善新基类的审计阻断证据：缺少正式账号时登记具体原账号编码，保持原校验强度。

### 十二项当前回执及处理顺序

| 项目 | 当前终态/证据 | 归类与可执行后续 |
|---|---|---|
| P04 | 本轮PREVIEW_READY，330条任职已纳入获批承载 | 原整体规格阻断已纠正。真实执行仍需V484多来源目标索引结构就绪；本轮不执行。 |
| P13 | 本轮PREVIEW_READY，入选99895原行 | SQL和预览实现缺陷已修复。真实执行仍需V484索引修正；不能把预览通过当成实际写入。 |
| P14 | 既有真实预览FAILED；本轮重新全量只读核对源冲突 | 91572个表头原行→91239业务键，169键载荷冲突；380605个明细原行→379522键，10个冲突键的40行均缺订单号。需要来源权威修正或既有可执行的逐键消歧依据；不任选、拼接、填占位或跳过。新诊断JSON保留表头样例及全部10个明细冲突键。 |
| P15 | 本轮FAILED：缺com_shipment_package.shipment_contract_source_key | 已批准V481列须在正式验证结构到位；V484最大容量CHECK到位后，将本侧新任务熔断配置按4283326条全表需求设为500万，现仍100万；还需同源新P24非预览COMPLETED完整输入。状态误认已修复。列/容量/正式输入不能由只读预览补造。 |
| P16 | 本轮FAILED：USER_FND:79:x00270正式账号来源绑定缺失 | 需要新P04正式完成并产生本套件USER_FND/USER_T引用；预览不产生该事实，不能借旧任务绑定。真实写入还需V484。原账号范围与type10范围不变。 |
| P18 | 本轮FAILED：首个缺失审计账号x00270 | 本轮独立只读归并82381个业务关系、82676个原关系组合，缺9个正式审计账号，均在获批P04范围内。需新P04正式账号到位；之后仍核对真实项目、合同及组织授权。真实写入还需V484。来源SQL已拆分，本轮不再超时。 |
| P19 | 既有真实预览FAILED；本轮核对正式项目来源为0 | 需要新P16正式项目身份；另缺PRJ/project四属性正式定义及pms.base-sync.project-state-definition-id。四属性为shipment_status、shipment_status_updated_at、execution_status、execution_status_updated_at。不自动发布定义、不写项目生命周期。 |
| P20 | 既有真实预览FAILED；本轮核对正式项目来源为0 | project_id是目标清单的必要父关联，需新P16正式结果；原项目数字不能当正式主键。不虚造预览父ID。 |
| P22 | 既有真实预览FAILED；本轮核对正式项目来源为0 | 先满足新P16正式结果；然后逐项核对本侧正式合同关系、订单行及办事处版本。这些后续检查当前未到达，不算已通过。 |
| P31 | 本轮FAILED：COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED | 已解除旧发布表/旧发布链依赖。只需新P16本套件完整正式范围；联系人之后仍须相应正式FINAL_CUSTOMER相关方。不会全表放行或增加当前授权。 |
| P33 | 本轮FAILED：COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED | 已解除旧发布依赖。需新P16完整正式范围；之后核验真实设备、项目选择授权及不可变地址快照能力，不猜设备ID或接管当前安装记录。 |
| P35 | 本轮FAILED：COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED | 已解除旧发布依赖。需新P16完整正式范围；另缺AST/SoftwareVersionRecord正式八属性定义及pms.base-sync.software-history-definition-id，不能用其他实体定义代替。八属性为conp/boot/cpld/pcb各自_bak、_change。 |

P18缺失的九个正式审计账号：j01441、j05517、t04026、w02611、w02799、x00270、x01861、x05179、y00439。没有任何一个超出原获批账号范围，不需要重新裁决账号范围；缺的是正式账号数据。本轮没有为此执行P04同步。

处理依赖顺序为：来源权威处理P14冲突；结构版本到位后，另行获准的真实P04/P24/P16输入才可能解除相应下游正式数据前置。此顺序只是明确外部条件，本轮仍不执行迁移、真实同步、后置关联或定义发布，也不要求重复确认已有业务裁决。

### 本轮真实预览明细

| 项目 | Run | 终态 | 回执read | 秒 | 本轮重跑 |
|---|---|---|---|---|---|
| P04 | 2111517837875126273 | PREVIEW_READY | 12632 | 1.143 | 是，最终回执 |
| P13 | 2111515526163517442 | PREVIEW_READY | 99895 | 12.927 | 是，最终回执 |
| P14 | 2111504179669397505 | FAILED | 0 | 16.562 | 否，保留上轮回执；本轮只读复核前置/来源 |
| P15 | 2111515580383285249 | FAILED | 0 | 0.161 | 是，最终回执 |
| P16 | 2111517842522415105 | FAILED | 0 | 2.212 | 是，最终回执 |
| P18 | 2111517851758272513 | FAILED | 0 | 8.753 | 是，最终回执 |
| P19 | 2111504252864196609 | FAILED | 0 | 10.904 | 否，保留上轮回执；本轮只读复核前置/来源 |
| P20 | 2111504299144146946 | FAILED | 0 | 10.187 | 否，保留上轮回执；本轮只读复核前置/来源 |
| P22 | 2111504342265786369 | FAILED | 0 | 5.849 | 否，保留上轮回执；本轮只读复核前置/来源 |
| P31 | 2111517888450043906 | FAILED | 0 | 0.126 | 是，最终回执 |
| P33 | 2111517888991109121 | FAILED | 0 | 0.145 | 是，最终回执 |
| P35 | 2111517889595088898 | FAILED | 0 | 0.154 | 是，最终回执 |

P04、P13的PREVIEW_READY仅表示本轮真实只读预览通过；其他失败回执read=0表示步骤未完整完成，不证明未读取原表或原表为空。P19/P20/P22代码与前置均未改变，P14另作全量只读冲突核查，没有无意义重复相同失败请求。P15沿用本轮wave8结果；P13实现不受后续范围接入修复影响，wave8成功结果仍有效。

### 验证和隔离证据

最终定向177项全部通过（0失败、0错误、0跳过），日志 `base-sync-blockers-20261018/final-20261018T015759.log`。新增原生MySQL检查只使用连接内临时表，覆盖P04账号/关系批写、真实父ID、原admin保护、随机凭据只在执行产生、已批准任职JSON及未解析引用、租户容量、回放和晚期失败回滚；新范围覆盖只接收本套件COMPLETED非预览回执、完整成员、源/目标变更保护、空集合、租户/连接/容量边界及生产SQL解析。新P16的UNCHANGED来源身份也必须刷新本次运行归属。现有公共事务、流式、授权和原任务隔离检查保留。临时表检查不替代正式库的物理外键与全业务真实同步验收。

P04十二份真实源SELECT及P13两份拆分SELECT均完成MySQL5.7只读LIMIT3检查，各约0.3～0.9秒；P13另有完整真实预览约12.8秒业务耗时。未用延长超时、关闭租户/权限解析或修改原库索引解决。

仅按此前授权重启59284两次，当前加载 `base-sync-blockers-20261018-wave9/runtime-candidate-wave9/yudao-server.jar`；前次wave8及旧wave7证据保留。两个冻结包只变更新套件类与XML，原类、原配置和全部迁移资源保持。已有V481/V482以及预备V484均未执行，候选包未装入新迁移资源。没有原任务操作、双线对照或真实同步。19084真实浏览器已验证重载后的已登录首页恢复；新套件无专属任务UI，任务结论来自正常登录上下文的真实API及数据库终态，不冒称专属UI闭环。

最终只读保护复核178项计数/最大ID及保护项没有差异；24个新任务均空闲，全部运行preview=true，正式新绑定0，Flyway仍473。自动Flyway、Flowable结构更新、Quartz/Batch自动作业和事件驱动均关闭，迁移容器exited；59191未操作。计数与最大ID证据不等于全字段校验，正常登录可能更新登录审计字段。

本轮证据目录：`D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018/` 和 `D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018-wave9/`。后者的 `twelve-current-results.json` 汇总十二项当前证据来源，`final-proof.json` 保存最终环境边界。外部缺项仍有十项，不记为全部预览通过。


## 2026-10-19 对照历史十项目逻辑的13项失败复核

13项均完成本次真实API预览；只修复新P21把实际写入结构前置误用于只读预览的问题。修复后454租赁+736配置=1190条全量PREVIEW_READY，回执保留执行结构警告，实际执行仍在清理前拒绝。当前24项为12 PREVIEW_READY、12 FAILED，P30仍0行空预览。

22项定向检查通过（含3项新原生临时表测试）；仅59284按已有授权重载P21类白名单修复包，其他原任务/SQL/迁移资源不变。13次初次预览后仅重跑P21；最终180项保护观察无差异，正式绑定与非预览运行仍0，24任务空闲，Flyway473及自动结构/作业关闭状态保持。未迁移、真实同步、发布定义或操作59191。

剩余12项按正式账号、来源冲突、目标列/容量、正式项目/回款输入及相应扩展定义逐项列明依据；不重新要求已裁决审批。详见[2026-10-19 对照历史十项目逻辑的13项失败复核](dppms-24-tasks-three-implementations-20261019/base-sync-13-failures-followup-20261019.md)。原始回执、首次失败和修复前测试日志均保留，既有历史结论不覆盖。


## 2026-10-19 原用户名及原项目ID承接后的最新复核

按本轮明确指令，原用户名直接进入字符串审计字段，主项目和下游引用采用原主档ID；不改既有类型/编码归并。P31/P33/P35预览从同P16规则的完整源目录取范围，不假造正式完成Run；真实执行保持正式范围、权限及必要外键检查。新P16不覆盖40个既存其他项目占用的1001～1040。

P08/612、P20/7818、P21/1190现均PREVIEW_READY；P24受影响重验81547通过。P22排序规则实现已修复，当前缺正式办事处快照；P33同范围1063717条超过现有100万库CHECK与任务容量。其余缺项逐项详列，不能用用户名/原ID修订推定其他字段或真实同步已通过。

当前24项14 PREVIEW_READY、10 FAILED，P30仍空输入；最新90项检查全通过。最新规则后14次真实预览、2次仅59284加载；最终180项观察无差异，绑定和非预览仍0，Flyway473和关闭自动执行状态保持。未迁移、真实同步、发布定义、操作原任务或59191。详见[逐项证据](dppms-24-tasks-three-implementations-20261019/base-sync-original-identifiers-preview-20261019.md)。
