# domain-migration 已有优化与本地性能增量

整理日期：2026-10-25（Asia/Shanghai）。范围为在线页面加载、服务端读取及其数据库查询；本轮只核查并整理证据，不修改业务代码、数据库或运行服务。

## 1. 比较基线与计数规则

| 项目 | 本轮锁定值 |
|---|---|
| 来源基线 | `codex/domain-migration`：`e0ae05247e6662c155c5503919506405eed07cf9` |
| 整理时工作树 HEAD | `c674a8a8793cd061056b5dbe85ba7afd6dd1ac9a`，当时 detached HEAD |
| 本地性能提交 | `f0f72c9241e0778362cd5bc7886e08085d43e79a`；分支 `codex/local-performance-20261025`，已提交、未推送 |
| 共同祖先 | `604dc3a4361b0fdda026a43518ad2c0e78282dea` |
| 本地运行环境 | 59286；当前运行清单记录应用 SHA256 `df03ae656d6248ed43e9310db2197fd3e9697482297d131f6f69028edf16622f`，PID 9264 |

两分支已分叉，不能把 `codex/domain-migration..HEAD` 的独有提交数量当成本地优化数量。本轮使用提交祖先关系、来源版本文件内容和当前工作树差异共同判定。

计数规则：

1. 来源基线已有的机制全部归入 A，即使最初在别的工作区实施、曾被旧清单称为“新增优化”，本次也不再计为本地新增。
2. 同一机制的多次提交、合并、修复、测试、部署只算一次。设备授权提交 `aaaa27c76`（来源）和 `5f0122a45`（本地承接）属于同一组，不重复计数。
3. 在已有机制上新增的局部优化归 B，只计算差异部分；不把所在文件的整个历史实现重新计算。
4. 缺失索引补回、规模下崩溃修复分别说明性质，不包装成全新的查询设计。业务故障修复、模板迁移和验收数据不计性能成果。
5. SQL、HTTP、浏览器可用时间分别记录；不同样本、环境或阶段的改善比例不相加。所有历史测量都不是当前统一 P95 或响应时间承诺。

**对上一轮口头汇总的归属修正：项目统计 13→2、项目树批量读取、请求内授权复用和设备组织字段投影，当前来源基线均已包含，必须归 A。**

## 2. A：codex/domain-migration 已有优化（本地只承接，不重复计新增）

### A01 项目列表统计聚合

- 改动：顶部 12 卡由 12 次完整分页改为一次统计，加原分页共 2 次请求；计数口径不变。
- 代码：[项目列表](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/projects/index.vue)、[ProjectStatisticsService](../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectStatisticsService.java)、[ProjectMasterMapper.xml](../../pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml)。
- 提交：来源 `6fa26979c` 已包含统计接口、VO、查询及页面接入；该提交也是当前 HEAD 祖先。页面还有其他未提交差异，不将其整体作为统计新增。
- 部署：历史 59286 两经理列表/统计读回验证通过；当前最新包未重新做逐卡请求数性能采样。
- 实测：旧版页面 13 次分页、约 2.942 秒；新统计 SQL 三次 87～106ms，12 项结果一致。37 项后端、8 项前端检查；后续真实列表/统计 z01010 为 2881/2881、y06496 为 123/123。不能以新 SQL 时间替代优化后页面时间。[E1、E2]

### A02 项目详情按需加载及商务候选查询

- 改动：36 个既有业务面板异步导入；合同、订单查询先取得项目关联候选主键，再读主档。
- 代码：[详情入口](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue)、[ProjectCommerceMapper.xml](../../pms-module-commerce/src/main/resources/mapper/contract/ProjectCommerceMapper.xml)。后者的本地后续增量另见 B03。
- 提交：来源 `b498519eb`，已在当前 HEAD；36 个面板不作为本地新增。
- 部署：原 59191 已受控部署验收；后续当前 59286 商务 XML 已与运行包核对相同，但没有重跑这一历史项目的同条件性能测试。
- 实测：原项目同角色商务概览 HTTP 2730.99→373.37ms；SQL 合同 1224.63→3.03ms、订单 1343.76→3.11ms。结果/排序一致。页面懒加载检查通过，但缺可靠的前后模块数量对照。[E3]

### A03 授权读取复用、项目树批量解析和设备组织投影

- 改动：一次同步请求内只复用成功的授权读取；普通用户完整项目范围批量读取树版本/路径，替代逐树查询；设备组织字段随主分页投影返回。
- 代码：[AuthorizationReadScope](../../yudao-framework/yudao-common/src/main/java/cn/iocoder/yudao/framework/common/util/authorization/AuthorizationReadScope.java)、[ProjectTreeScopeService](../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java)、[DeviceListProjection](../../pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/device/projection/DeviceListProjection.java)。
- 提交：来源 `9f9628c2a`，已在当前 HEAD；不是本地未提交的新能力。
- 部署：后续 59286 范围与实际页面读取有验证；缺当前独立查询次数/整体耗时对照，不能由整树打包推定收益。
- 实测：55 项相关检查；3 棵真实树、35 条路径新旧结果一致。租户/用户/动作隔离、异常不缓存、下一请求重读、撤权重读与写入锁路径保留；无独立页面提速数值。[E1、E2]

### A04 默认设备页授权查询优化（两阶段合为一组）

- 改动：先取消冗余 AST 合同候选枚举；再通过 PROJ/COM Owner 公开 SQL 在数据库内关联，消除 Java 大集合往返和巨大 IN 参数。
- 代码：[DeviceQueryService](../../pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/device/DeviceQueryService.java)、[DeviceJoinedPageMapper.xml](../../pms-module-asset/src/main/resources/mapper/device/DeviceJoinedPageMapper.xml)、[项目范围 SQL](../../pms-module-project/pms-module-project-api/src/main/resources/mapper/projectscope/ProjectViewReadScopeSql.xml)、[合同范围 SQL](../../pms-module-commerce/pms-module-commerce-api/src/main/resources/mapper/contract/ContractDeviceReadScopeSql.xml)。局部本地改进另见 B02。
- 提交：来源 `aaaa27c76`；本地承接 `5f0122a45`。已有逐文件核对表明是相同机制，合并或两个提交不能算两项新增。
- 部署：原报告只完成只读 SQL 复验；当前 59286 运行包的 DeviceJoinedPageMapper.xml 与工作树一致，但本轮没有重新进行百万设备默认页 HTTP/浏览器测试。
- 实测：阶段一 SQL 链 15.2338→5.5470 秒；阶段二独立基线 5.8627→4.2923 秒。第二阶段最终默认查询 12 个参数、4564 字符。设备总数 3,154,458、首页全部投影和顺序一致。两阶段不是同一个连续基线，不叠加百分比，也不是页面耗时。[E4、E5、E6]

### A05 当前文件引用读取结果复用

- 改动：保留 Owner 授权和精确读取，直接复用该结果，避免再调用一次相同授权/查询。
- 代码：[FileCurrentReferenceQueryService](../../pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileCurrentReferenceQueryService.java)、[FileQueryService](../../pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileQueryService.java)。
- 提交：来源 `486986c1f`，当前 HEAD 已包含。
- 部署：原专项记录最后为尚未部署，之后存在整体工作树部署；本轮未单独核对该类与运行产物，不把后续整体构建当成逐项部署证明。
- 实测：8 项定向检查，包括失败测试先复现两次 Owner 调用、修复后复用及拒绝场景；没有独立 HTTP 前后时延值。[E7]

### A06 割接工作台、清单与覆盖查询

- 改动：列表不等待慢 KPI；返回工作台避免失活/卸载页面重复刷新；清单字段批量读取、设备覆盖查询收敛、同请求来源文本复用。
- 代码：[割接工作台](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/workbench/index.vue)、[CutoverChecklistApplicationService](../../pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistApplicationService.java)、[CutoverCoverageService](../../pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/implementationreadiness/CutoverCoverageService.java)。
- 提交：来源 `2f18acd95`、`3742782e1`，当前 HEAD 均包含；PLT 引用复用单列 A05，不重复计算。
- 部署：原专项有运行/浏览器验证；当前完整首次加载的性能上限未逐项重验，不能宣称所有割接首次加载均低于 2 秒。
- 实测：返回工作台由重复两组列表/统计改为一组，有浏览器记录；另有 50 项读取/覆盖检查、5 项返回路径检查和 54 项来源复用相关检查。这些有交叉场景，不汇总成去重后的测试总数。[E7]

### A07 设备可见范围、序列号与客户设备汇总的早期优化

- 改动：设备范围/SN 查询收敛；客户设备汇总拆分直挂、关系分支，避免全租户扫描。
- 代码：[DeviceQueryMapper.xml](../../pms-module-asset/src/main/resources/mapper/device/DeviceQueryMapper.xml)、[DeviceAccessScopeService](../../pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/security/DeviceAccessScopeService.java)。
- 提交：来源 `611ddd633`、`0a3a88b6d`，当前 HEAD 均包含。
- 部署/实测：历史验证见任务记录；本轮未取得与当前产物绑定的单项部署、同条件时间对照，因此仅确认基线已有代码，不编造部署收益。与 A04 相接，但不将旧收益再次叠加。[E8]

### A08 MyBatis SQL AST 缓存策略

- 改动：解析缓存从写入后 5 秒过期调整为访问后 5 分钟过期，减少热语句反复解析；这不是业务数据或权限缓存。
- 代码：[YudaoMybatisAutoConfiguration](../../yudao-framework/yudao-spring-boot-starter-mybatis/src/main/java/cn/iocoder/yudao/framework/mybatis/config/YudaoMybatisAutoConfiguration.java)。
- 提交：来源 `0548c4f8c`，当前 HEAD 已包含。
- 部署/实测：有对应单测提交；本轮没有单项部署证明或解析 CPU/端到端基准，不给出推测节省值。大集合栈溢出后续修复见 B01，不以关闭此缓存解决问题。

### A09 业务工作台与需求分析上下文读取

- 改动：收敛工作台、业务上下文和动态表单/文件读取中的重复工作，前端整理上下文加载流程。
- 代码：[ProjectTaskBusinessService](../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskbusiness/ProjectTaskBusinessService.java)、[TaskBusinessPanel](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/TaskBusinessPanel.vue)、[需求分析面板](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectRequirementAnalysisPanel.vue)。
- 提交：来源 `0229321f0`，当前 HEAD 已包含。这些文件仍有其他功能/修复差异，不全部算性能增量。
- 部署/实测：本轮确认来源提交与消费者代码；未复核该专项完整运行产物和前后量化证据，故不列提速数字，也不将业务修复算为本地性能新增。

## 3. B：相对当前来源基线的本地增量

以下性能差异已选择性纳入本地提交 `f0f72c9241e0778362cd5bc7886e08085d43e79a`，未推送。仅提交各文件中的性能改动；同文件其他改动仍未暂存。提交动作不代表重新部署，部署与实测状态仍按各行原有证据。

| 编号 | 增量及对应代码 | 提交状态 | 是否部署 | 实测与边界 |
|---|---|---|---|---|
| B01 | 批量树 SQL 的深层 OR 改为精确配对元组 IN：[ProjectTreePathMapper.xml](../../pms-module-project/src/main/resources/mapper/projecttree/ProjectTreePathMapper.xml)、[ProjectScopeDescendantsQuery](../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projecttree/query/ProjectScopeDescendantsQuery.java) | 已本地提交 `f0f72c924`；来源仍为 OR，批量树能力本身归 A03 | 已部署 59286；当前运行包 XML 与工作树一致 | 5000 组失败测试先复现 StackOverflowError；29 项回归；2881 组真实范围等价，SQL 0.537→0.054 秒。后续 z01010 两页面可读。属于规模兼容修复，不新增授权能力。[E9] |
| B02 | 指定项目设备范围裁剪、已指定项目时精简重复 EXISTS：[DeviceJoinedPageMapper.xml](../../pms-module-asset/src/main/resources/mapper/device/DeviceJoinedPageMapper.xml)、[ProjectViewReadScopeSql.xml](../../pms-module-project/pms-module-project-api/src/main/resources/mapper/projectscope/ProjectViewReadScopeSql.xml) | 已本地提交 `f0f72c924`；A04 默认页整体设计不重算 | 历史 59285 部署并实测；当前 59286 DeviceJoinedPageMapper.xml 与工作树一致，另一公共片段本轮未单独提取核对 | 项目设备 HTTP 第二轮 1.746→0.046 秒，交付范围 1.032→0.352 秒；浏览器项目设备 38.8ms、交付范围 305.6ms。样本关联设备为 0，不能代表大结果集。[E8] |
| B03 | 执行单关联 OR 拆 UNION、回款等值预筛选、订单行按订单发现授权候选：[ProjectCommerceMapper.xml](../../pms-module-commerce/src/main/resources/mapper/contract/ProjectCommerceMapper.xml)、[SalesOrderLineMapper.xml](../../pms-module-commerce/src/main/resources/mapper/order/SalesOrderLineMapper.xml)、[CommerceDeliveryScopeQueryService](../../pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeQueryService.java) | 已本地提交 `f0f72c924`；仅计本地差异，A02 不重算 | 已部署历史 59285；当前 59286 两份 XML 与工作树一致 | 28 项定向检查；三轮响应一致，复热 HTTP 合同概览 2.277→0.721 秒、订单行 0.643→0.306 秒；浏览器响应头时间亦有前后采样，不是整页时间。[E8] |
| B04 | 基本信息合同异步独立补齐、加载/失败重试；新增两业务面板按需导入：[ProjectBaseInfo.vue](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectBaseInfo.vue)、[详情 index.vue](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue) | 已本地提交 `f0f72c924`；36 个已有异步面板归 A02；分组、参与方等非性能改动未提交 | 19086 Vite 热加载有真实浏览器验证；不是生产前端静态包发布 | 49 项前端检查、完整类型检查有记录；两经理合同可读，挂起/失败隔离由组件测试验证。无同条件页面加载时间改善值。[E2] |
| B05 | 配置 Log 列表先展示、仅当前页解析/轮询、抽屉来源独立反馈；授权后无归档直接返回：[归档页](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device-log-archive/index.vue)、[设备日志抽屉](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectDeviceConfigLogDrawer.vue)、[DeviceLogArchiveService](../../pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/configurationlog/DeviceLogArchiveService.java) | 已本地提交 `f0f72c924`；前后端同批计一次；无关日志文案及过滤调整未提交 | 19086 前端热加载；59286 历史冻结包 `357bb207…` 已部署实测。当前最新包未逐类重新确认此项，不把历史包当当前运行包 | 前端 15 项；后端定向回归。84 条码项目归档接口单次 225→172ms，另一次列表可用 624ms；旧日志仍约 970ms，抽屉可用约 1419ms。项目无归档日志，尚未覆盖大量真实日志。[E10] |

## 4. 恢复项、排除项与不重复计算

- V474 订单行四列索引补回属于既定 Schema 的恢复；本地 `order-index-applied.json` 有执行回执，但不算 B 中的新索引设计，也不把其收益与 B03 重复累计。索引 DDL 不等于 Git 已提交；最终同条件 HTTP 对照仍以专项证据为准。[E11]
- 合同授权公司/部门约束补迁、最终用户完成规则修复、模板/问卷迁移、材料 version 列修复和文件存储恢复，不列为本报告的性能新增。
- `4e49a9f63`（设备名称并发查询合并）、`596390b81`（默认业务展示权限批读）虽然存在于其他 Git 历史，但本轮祖先检查确认它们既不属于当前来源基线，也不属于当前 HEAD；不把“全仓能搜到提交”当成本工作树已交付。
- 同源提交 `aaaa27c76` 与本地 `5f0122a45`、配置 Log 前后端、割接复用与测试/部署各只计算一次；不计算“性能总提升百分比”，不将各轮测试数相加冒充独立覆盖数。

## 5. 当前部署核对与验证限制

本轮读取 `.run/direct-test/latest-runtime.json`，并只读检查 59286 监听归属：PID 9264 与清单一致。运行应用 SHA256 为开头列出的 `df03ae65…`，不能继续将历史 `357bb207…` 或 `58692…` 称为当前运行包。

本轮从该冻结 application.jar 内嵌模块 JAR 提取以下 XML，与当前工作树按换行归一化比较，均一致：

1. `pms-module-project / mapper/projecttree/ProjectTreePathMapper.xml`。
2. `pms-module-asset / mapper/device/DeviceJoinedPageMapper.xml`。
3. `pms-module-commerce / mapper/contract/ProjectCommerceMapper.xml`。
4. `pms-module-commerce / mapper/order/SalesOrderLineMapper.xml`。

这证明对应 SQL 已装入当前运行产物，不证明当前时点已重新完成全部服务调用链、每个 Java 类、浏览器操作或性能验收。其余项目按各行历史部署/未核实边界报告。本轮未重跑业务测试、压测、发布或重启；只整理已有验证并补做提交归属、源码差异、运行监听和四份部署 XML 核对。

## 6. 证据索引

- E1：[F-PROJ-001 实施记录](../../tasks/features/F-PROJ-001.md)，2026-10-22 统计及 2026-10-23 批量范围段。
- E2：[合并回归复查与两经理验收](domain-migration-comparison-20261023/merge-regression-recheck-20261024.md)。
- E3：[项目基本信息性能记录](2026-10-17-project-basic-information-performance.md)。
- E4：[设备默认页取消候选枚举](device-page-performance-20261023/report.md)。
- E5：[设备授权 SQL 关联实测](device-page-sql-join-20261023/report.md)。
- E6：[同源优化提交核对](domain-migration-comparison-20261023/parallel-optimization-timeline.json)。
- E7：[F-CUT-003 实施记录](../../tasks/features/F-CUT-003.md)，割接读取、返回路径、来源复用及当前文件引用补充段。
- E8：[F-AST-001 实施记录](../../tasks/features/F-AST-001.md)，设备范围、2026-10-23 指定项目及合同/订单行性能段。
- E9：[大范围 SQL 栈溢出修复及部署](domain-migration-comparison-20261023/project-scope-overflow-fix-20261024.md)。
- E10：[配置 Log 优化、部署及浏览器测量](project-template-transfer-20261024/config-log-loading-performance.md)。
- E11：[原分类清单](local-optimization-inventory-20261025.md)，A14 的既定索引恢复记录。该清单的“新增”是当时实施分类，本报告以当前锁定来源提交重新判归属，不照搬其分类。

## 7. 独立提交与本轮复核（2026-10-25 追加）

- 性能代码提交：`f0f72c9241e0778362cd5bc7886e08085d43e79a`，`perf(pms): 收敛本地页面加载与查询增量`。
- 提交分支：`codex/local-performance-20261025`。27 个文件，714 行新增、84 行删除。以 B01～B05 为唯一性能范围；A 类既有优化不重复提交。
- 本文在后续文档提交 `docs: 记录本地性能独立提交与验证状态` 中归档，性能提交号已逐项回填。文档提交不再计算一项性能成果。
- 合同授权公司/部门修复、基本信息分组及参与方展示、日志页文案/过滤、模板与状态机迁移、满意度业务修复等均保留在工作区，未混入性能提交。共享文件采用索引中的局部版本，没有用暂存版本覆盖用户工作文件。
- 后端：沿固定工具链并持工作树构建锁执行三模块及依赖的定向 Maven 检查，BUILD SUCCESS；再将暂存 Java/测试/SQL 导出到独立输出目录，并使用 HEAD 的未修改合同授权接口和 SQL，隔离未暂存授权增量，编译及 83 项定向检查通过。复用既有依赖构建，不宣称是全仓干净检出的完整测试。
- 前端：通过临时 Vite loader 加载 Git 索引中的 Vue/TS 内容，验证合同慢来源独立展示、日志当前页解析/旧响应隔离及设备抽屉分路加载，3 个测试文件共 7 项通过。没有把未暂存的页面分组和其他测试断言当成本提交前提。
- MySQL 条件启用用例 `DevicePageProjectScopeMySqlTest` 和 `ProjectCommerceExecutionChainMySqlTest` 本轮未开启真实数据库运行；原 SQL/HTTP/浏览器记录按上文复用，不以本轮提交冒称新部署或新的端到端验收。
- `git diff --cached --check` 通过。仅将明确路径/改动块纳入暂存；未执行 push、服务重启、数据库写入或迁移。
- 本轮保留证据：`.run/performance-commit-20261025/backend.log`、`index-backend.log`、`index-sources.json`、`before.json` 及选择性暂存脚本。期间观察到其他会话的未暂存文件变化，保留其最新内容，不恢复旧快照。

此文是过程证据汇总，不修改 PRD、SDS、Feature/Task 权威完成状态。代码提交与文档记录提交均仅在本地保存；原部署/测量结论没有因提交而晋级。引用的本地历史报告按各自原状态保留，本轮不为提交此文而整批纳入其他未提交文档。
