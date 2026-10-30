# F-PROJ-001 手动项目创建与模板初始化

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Implementation Done Gate：`NOT_READY`（修订019新增量在途；原创建范围PASS保留历史）
> 当前阻断：Q-FPROJ-011待确认新建无MANAGE项目首次指派的授权来源；历史已完成范围不回退，依赖独立验收的路径仍受Q-TPLACC-001约束
> 当前任务：Task 10已有合法管理范围的联合/分次成员管理及UI闭环已交付；新建未指派项目首次授权待裁决，不重复候选裁决
> Requirement ID：`PM-01`、`PM-03`
> Feature Spec：`specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md`
> 历史创建规格SHA-256：`f7051c41ba5b1214fc9cc82fe72e801d8c8f90e9b43bd21e85d357a8b32aa8bb`（不作当前新增量准入）
> Technical Plan：`docs/superpowers/plans/2026-08-23-v18-organization-location-foundation-and-fproj001-rework.md`
> 锁定规格提交：`975107a665f156ce527480e939ad89a614cd1a21`

## 事实边界

- 2026-09-08主线准备检查：原“转入F-PROJ-002”的下一步属于历史记录，不再驱动当前派发。Q-FPROJ-009已解决业务设计，首次项目经理指派应独立落实到相应Spec/计划/实现复验；Q-TPLACC-001只限制依赖其独立验收合同的新增路径。当前Ready仍只由Spec维护，不以本条准备说明恢复整个Feature Ready或扩张历史Done。详见[主线准备分析](../../docs/superpowers/plans/2026-09-08-project-delivery-mainline-preparation.md)。

- 本 Feature 已从 PRD V1.8 首个 Feature 重新审计并改造，没有根据 V1.7 存量实现推定完成。
- `specs/001-project-delivery-platform/`仅作历史参考，不参与当前门禁校验。
- `tasks/plan.md`、`tasks/todo.md`及 2026-08-21 的旧 F-PROJ-001 计划均不作为当前实施输入。
- 当前受管快照已回写 F-PROJ-001、PM-01、PM-03 的实施完成状态与 Code/Test 证据，规格仓库与 NPDMS 进度一致。
- F-PROJ-001 的局部实施计划完成不表示总体 Implementation Phase 完成。按 PRD 顺序，PM-01/PM-03 之后首个尚未实施的 P0 Requirement 是 PM-02；形成其正式 Feature Spec 是下一正常任务，不构成阻断。Feature Spec 提交并同步前不得开始 PM-02 代码改造，所有当前范围 Feature 完成前不得进入 Deployment。

## 任务完成情况

### 当前主线增量

- Task 10：项目级联合/分次指派、多项目经理与主责管理，`IN_PROGRESS`；上游通用查询见[查询DU](../delivery-units/DU-20260908-PM01-COMPANY-ROLE-QUERY.md)，经理增补/移除/主责写入见[成员写入DU](../delivery-units/DU-20260908-PM01-MANAGER-MEMBERS.md)。已有MANAGE项目的联合事务、当前成员/候选查询与页面操作已取得代码、固定测试库及真实浏览器证据，详见[成员闭环DU](../delivery-units/DU-20260908-PM01-MEMBER-CLOSURE.md)；无MANAGE新项目的首次指派受Q-FPROJ-011阻断，完整跨域消费者及独立验收义务仍按[当前计划](../../docs/superpowers/plans/2026-08-23-v18-organization-location-foundation-and-fproj001-rework.md#项目级成员指派增量2026-09-08)推进。
- 本轮原首次-only/固定T-ASSIGN-PM草案已撤下；不设强制绑定Task 11，不用单manager_id代表所有经理。
- 有效PM角色事实及阶段权限直接消费增量已交付，见[角色权限DU](../delivery-units/DU-20260908-PM01-MANAGER-ROLE-FACT.md)；指定用户不再仅凭主责指针授权，首次指派数据范围仍须裁决。
- [ ] AC-FPROJ-011：已有合法管理范围的联合/分次指派、多经理、主责和历史刷新已验收；新建未指派项目首次授权及完整各阶段/消费者义务未闭环。

下面Task 0～9、历史AC与PASS证据继续覆盖原完成范围，不因修订019进入IN_PROGRESS而撤销历史结论；顶部状态表示当前Feature新增量。当前Ready以Spec为准，查询DU完成不等于Task 10或Feature Done。

### 已完成的历史范围

| 任务 | 结果 |
| --- | --- |
| Task 0 锁定 V1.8 正式规格输入 | COMPLETE |
| Task 1 公司、部门编码与组织范围 | COMPLETE |
| Task 2 AST 地点核心模型 | COMPLETE |
| Task 3 地址、站点、位置树与区划映射 API | COMPLETE |
| Task 4 项目多站点与服务经理人工指派 | COMPLETE |
| Task 5 工勘、安装与设备当前位置事实 | COMPLETE |
| Task 6 组织与地点管理页面 | COMPLETE |
| Task 7 项目、工勘、安装与设备页面闭环 | COMPLETE |
| Task 8 MySQL、模块边界与全量回归 | COMPLETE |
| Task 9 真实浏览器业务验收 | COMPLETE |

## 验收跟踪

- [x] AC-FPROJ-001 候选与预览
- [x] AC-FPROJ-002 显式模板创建
- [x] AC-FPROJ-003 唯一默认模板
- [x] AC-FPROJ-004 WorkBinding 完整性
- [x] AC-FPROJ-005 V1 人工确认服务经理
- [x] AC-FPROJ-006 幂等与并发
- [x] AC-FPROJ-007 权限负向
- [x] AC-FPROJ-008 原子失败
- [x] AC-FPROJ-009 真实界面闭环
- [x] AC-FPROJ-010 创建失败无持久化

AC-FPROJ-007 原阻断已由 V1.8 组织与地点基础改造关闭：公司、部门编码、同一行公司—部门授权范围、AST 地址/站点/位置、项目多站点、区划—办事处建议和人工指派均已形成稳定 API、服务端校验、负向测试及真实浏览器证据。站点不绑定公司或办事处；自动建议只按行政区划编码和办事处部门编码映射，最终由授权人员人工确认。

## 验证证据

- 规格快照校验：PASS。
- PMS 模块边界校验：PASS。
- 后端全量回归：30 个 Reactor 模块全部 SUCCESS。
- 真实 MySQL：V1～V68 空库、重复迁移、validate 及 V63→V68 通过；定向场景 12/12 通过。
- 前端合同测试：15/15 通过。
- 前端类型检查：0 错误。
- 前端本地构建：PASS；仅保留既有 legacy CSS `*zoom` 非阻断警告。
- 真实浏览器：组织与地点、多站点项目、人工指派、工勘、安装及设备位置历史场景刷新后均通过。
- 独立规格与质量复审：当前实施计划 `PLAN COMPLETE GO`。

详细证据：

- `output/f-proj-001-v18/database-evidence.md`
- `output/f-proj-001-v18/browser-acceptance.md`
- `output/location-v18/mysql-acceptance.md`
- `output/location-v18/browser-acceptance.md`
- `output/location-v18/regression-summary.md`

## 当前门禁与下一步

代码、Schema、测试、浏览器证据、评审及受管追溯均已满足本 Feature 的工程实现完成条件，Implementation Done Gate 为 `PASS`。该结论不代表 Deployment、SIT、UAT、Release 或治理 GO。

当前阻断为无。PM-02 正式 Feature Spec 已达到 `BASELINE / READY` 并同步到锁定提交 `b453cb0b80804e288be360b50ee0bfef6809b798`。总体 Implementation Phase 继续转入 F-PROJ-002 全新 V1.8 Technical Plan；旧 F-PM02 Spec、Technical Plan 与现有代码只作存量审计输入，不得据此判断已实现。

## 2026-08-25 集成回归关闭

F-PROJ-004 浏览器验收暴露出 F-PROJ-001 与后续统一 ProjectTreeScope 集成后的回归：
根项目创建成功时未同步建立首版树投影，且创建人基础查看范围未被统一范围服务合并。
本次已在原规格边界内修复：根项目创建事务同步发布版本 1 与自路径，创建人仅获得
`VIEW` 基础范围，不新增成员角色或显式授权。定向单测 18/18、MySQL 13/13、项目模块
完整回归 256 项均通过；真实页面创建项目 `992002000102` 后首次进入及刷新详情均通过。
独立裁决结论为 `GO`。详细证据见
`docs/acceptance/F-PROJ-001-root-creation-scope-regression.md`。

## 2026-09-07 修订018影响记录

模板业务规则配置化与独立验收设计已获需求方确认，正文及当前Feature Spec已登记`CHG-PRD-2026-09-07-018`。上文Implementation状态、提交、测试及历史Done保持原范围，不自动覆盖新语义；当前Ready以Feature Spec的REVALIDATION_REQUIRED为准。Q-TPLACC-001限制新增实体创建/范围绑定及其消费者路径，须先完成Phase 2差量再更新唯一实施计划，不从本次文档确认派生Task/Feature完成。
## 2026-10-09 已授权反馈修复进度

FB-02 日期/节点规则已确认；客户精确筛选与超期查询、双标识、节点完成历史及阶段日期展示已实现。后端定向19项、前端定向18项通过；V422在指定 `npdms_domain_test` 完成 migrate、validate、重复 migrate，既有646阶段和819任务的状态、计划日期、完成日期及版本未变。

项目模块超期/客户增量包已按用户“继续”指令部署；用户随后明确“确认部署并重启59191”，已部署顶部卡片和“是/否/留空全部”的补充筛选修复，后端PID20364，数据库、Redis、DAC及回调网关未重启。新增14项真实MySQL检查通过；应用内浏览器“是”返回1条、“否”返回94条。节点双标识、合法计划更新及完成后历史保留等业务浏览器环节仍待补验。FB-03独立实施/流程/发货等条件仍未完整实现，本记录不改变原Feature总体状态。证据见工作树 `.run/test-feedback-20261009/regression/repair-r4/repair-regression-report.md`。

## 2026-10-22 项目列表查询性能优化（需求方已批准）

本次仅优化读取链路：顶部12张卡片从12次完整分页改为一次 `GET /admin-api/pms/projects/statistics` 聚合，加上原列表分页共2次项目数据请求；原项目列表筛选、分页和展示链路保留。统计复用 `ProjectTreeScopeService.resolveAllFullProjectIds(..., PROJECT_VIEW)` 及既有当前超期SQL片段，不缓存权限或统计，不改生命周期、历史和业务数据；独立于列表筛选的原统计口径保持。S0～S6仅ACTIVE，三种闭环仍按生命周期精确计数，同项目多个超期节点只计一次。

验证：固定JDK25、工作树依赖仓库及独占锁下，37项后端定向检查通过（ProjectStatisticsServiceTest 3、ProjectTreeScopeServiceTest 18、ProjectManualMapperSelectPageMySqlTest 16）；真实MySQL用例事务回滚，验证全部状态与原分页total一致、空/缺失范围、跨租户、逻辑删除及重复超期节点，且验证生产SQL解析器可解析。前端8项运行测试通过，覆盖首次仅1次统计+1次分页、统计失败仍可读列表及筛选参数保留；定向ESLint零错误，4条原有格式警告。全量类型检查被本次未修改的需求分析、设备日志、实施方案测试和任务详情测试错误阻断，未宣称通过。

真实浏览器旧版基线（19191/59191，admin）：6698条项目、13次项目分页请求，网络空闲完成约2.942秒，无pageerror；截图 `.run/project-list-baseline.png`，请求证据 `.run/project-list-baseline.json`。只读MySQL EXPLAIN ANALYZE及3次新SQL执行87～106毫秒，12项计数与基线截图一致，阶段/任务命中现有租户+项目索引，未新增索引或迁移。证据 `.run/project-statistics-explain.json`。这些SQL结果不代表优化后页面耗时；当前后端尚未部署本增量，真实新接口与逐卡浏览器验收待用户明确批准重启59191后执行，脚本 `.run/project-statistics-browser.py` 已备好。

本增量最终受控打包成功，冻结清单 `.run/build/releases/3947e645-b785-46b3-94bd-5c3f4bacab09/manifest.json`；`scripts/worktree-backend.ps1 -ValidateOnly`通过，22项运行修复基线与local,dac配置保持。该包来自当前整个工作树，包含其他已有未提交改动；尚未部署、重启、提交或推送。部署需按AGENTS.md取得本次明确重启批准，之后执行已准备的真实浏览器逐卡一致性/请求数/耗时验收。最初测试编译遗漏JSQLParserException声明已在范围内修复，修复后37项通过；保留首次失败与最终结果日志，不将首次构建失败说成通过。

### 2026-10-23 普通用户全部项目权限改为批量树事实读取

按需求方授权，ProjectTreeScopeService.resolveAllFullProjectIds 保留候选发现（成员、创建人及Owner授权分页）和既有三种动作判定，普通用户不再逐树调用 resolve 查库。新增场景Query及Mapper XML：按租户/根集合以ROW_NUMBER选最新ACTIVE版本（tree_version、id倒序），按精确根/版本对批量取根路径，按每棵树自身的授权锚点集合批量取后代路径；空根/树/锚点集合失败关闭。候选版本发现后批量检查根项目，再批量复核版本，保留失配/缺失的失败行为。发现阶段不冻结最终授权：成员、创建人均在授权阶段再批量读取；权限动作、有效时点、VIEW附加MANAGE授权、EDIT映射MANAGE、仅显式后代授权扩展及租户边界保持。完整ID集合不计算无消费者的路径占位节点；单树resolve/resolveCurrent及写入lockAndRevalidate原路径不变。

固定工具链与工作树构建锁下55项相关测试通过：18项原单树权限测试、5项批量权限测试、1项真实Mapper SQL测试及31项设备/合同/装配链回归。多树三动作与原单树结果逐棵对照一致；覆盖跨树锚点隔离、无活动版本、版本变化、缺失根、Owner失败、发现后成员/创建人撤销、角色复核和空集合。MySQL只读验证3棵实际树及35条根/后代路径，新旧查询结果一致，4种空集合返回空结果。首次测试等待其他构建释放锁，未绕过锁；最新源文件已编译并完成重跑。日志见 .run/project-scope-batch-tests.log、.run/project-scope-batch-retest.log，结果见 .run/project-scope-batch-results.json、.run/project-scope-batch-mysql.json。未修改业务数据，未部署、未重启服务；不以这些证据宣称运行接口已切换或整体时延已提升。

## 2026-10-25 服务经理大项目范围查询栈溢出修复

178测试库真实浏览器使用z01010（周文成）查询项目列表、统计及割接项目候选时出现服务器错误；日志为 `StackOverflowError`。按旧路径审计，`ProjectTreePathMapper.selectScopeRootPaths` 和公开 `ProjectViewReadScopeSql.commonTables` 将大量根/树版本对展开为深层OR表达式，触发SQL解析缓存序列化栈溢出。两处仅改为成对 `(root_project_id, tree_version) IN (...)`；不改变租户、空范围拒绝、成员/创建人/授权重读、授权有效期、树版本复核或管理动作判定，不以提高线程栈或关闭解析缓存绕过问题。

新增5000组范围、512 KiB线程栈的生产解析缓存回归，先在原实现复现栈溢出。修复后42项定向检查通过（项目权限及SQL28项、设备关联查询及大范围14项），覆盖根/版本错配、空范围、跨租户及既有授权语义。固定工具链和同一工作树锁执行，日志 `.run/service-manager-scope-tests-before.log` 与 `.run/service-manager-scope-tests.log`。178库仅SELECT比较3组实际根/版本条件，新旧结果均为3条且相同；证据 `.run/service-manager-scope-mysql-readonly.json`，未执行临时表、DDL或迁移。构建使用固定入口，证据 `.run/service-manager-scope-build.log`；部署及修复后真实浏览器复验另按实际结果记录，不能以测试通过代替运行修复。

经用户明确批准，固定入口完整构建成功，22项运行保护通过，冻结清单 `52285388-7926-4c8d-b94d-abfb91c727cb` 已部署59191，PID41252，健康UP。运行进程配置及Actuator有效来源交叉核对17项，确认178主/从数据源及禁迁移、禁自动建表、Wall保护生效；证据 `.run/service-manager-scope-runtime-20261025/`。部署前后520张表、9490列、4218条索引元数据、241个触发器、22条Flyway历史指纹一致，未执行迁移或DDL。

真实浏览器以周文成账号复验：权限内列表2884条，编码检索 `16200721122903N` 返回1条；原HTTP200/业务500恢复为HTTP200/业务0，负责人显示杨凯添。证据 `.run/project-16200721122903N-20261025/service-manager-list-{before,after}-fix.json` 及截图。未扩大到管理员的79432条项目范围。

关联全流程验收：指定项目 `16200721122903N-0`（178库ID44637），服务经理z01010；用户明确同意由既有项目经理y06496（杨凯添）担任主责，保留liujiazhen_supp（刘佳真）及其他成员。7名成员、已完成用户信息录入及原计划v1/模板v2保留。实际杨凯添登录已确认，通过真实界面完成合成工勘保存、确认与归档；需求分析完成正文保存、数据库存储拓扑附件上传和正式冻结，任务自动更新为4/16，S1完成。施工计划已按生效工期2026-10-25至2026-11-01填写并提交周文成审批；后续全流程尚未完成。证据 `.run/project-16200721122903N-20261025/10-*`、`14-*`至`17-*`；未改写既有结项或审批历史。

### 需求保存、刷新及重复来源交付件展示修复

旧需求表单在普通字段无变化时直接返回，附件独立上传后的Owner事实未回读；统一工作区刷新未接入需求面板，且读取overview也发出changed通知。现补齐无正文修改时的权威回读及失败提示、面板刷新暴露，仅在写入完成后通知外层；显式刷新保留表单挂载。需求上下文重读期间通过inert阻止正文及底部传送操作区交互，避免临时只读切换破坏编辑器；新响应的拒绝、历史只读、完成状态及服务端授权照常生效。按需求方后续要求，仅需求分析隐藏额外“来源交付件”区块，表单附件、引用与历史不删除，其他页面展示不变。

失败测试先复现3项缺口；修复后6组69项相关测试、定向ESLint、完整vue-tsc及env.local前端构建通过。真实浏览器验证普通字段修改保存后刷新仍保留、无正文修改保存会回读附件、连续刷新无新增脚本异常、额外来源区块消失但有效拓扑附件仍可见；随后完成正式冻结与任务自动投影。日志 `.run/requirement-refresh-{before,after,eslint,typecheck,build}.log`，运行证据 `14-*`、`15-*`。

### 178禁DDL环境的空结果查询修复

Druid Wall将合法权限空集合SQL的`AND 1=0`误判为注入，导致工期工作区/历史查询失败。固定启动配置仅允许该恒假条件，不改变空结果SQL或业务权限，不允许DDL。离线固定工具链及工作树锁下先复现，再验证8条DDL/存储调用均被拒绝、5条业务SQL被接受，无数据库连接。用户再次明确批准后部署冻结包`8479be30-8c3e-4b6d-bc59-333f8546693f`，59191 PID46488健康UP，18项有效配置及9条178/npdms连接交叉核对；部署前后表结构及22条Flyway历史指纹一致。真实浏览器工期历史正常显示原V1/8天记录，无需重建或覆盖。证据 `.run/wall-failclosed-*`、`.run/duration-wall-runtime-20261025/`及`16-duration-history-after-fix.txt`。

### 实施方案章节超长保存修复与S2/S3角色验收

用户明确批准不改表、不迁移地修复章节存储及兼容读取。旧方案页面保留；仅增强页`solution-reviewed/SolutionReviewChapterForm.vue`将硬件范围、软件调试、业务配置、归档、培训目的及内容写入既有`section_data`的5.1/5.2/5.3/6.4/7.1/7.2章节键，合并保留`requirementInput`等已有内容。旧`remark`章节兼容读取；只读/批准版本不序列化改写，显式空值优先于旧内容，客户文档指针及普通备注保留。未新增列、修改长度或截断正文。失败测试先复现5606字符写入备注及只读对象被补写的问题；修复后4组61项相关测试、定向ESLint、完整vue-tsc及env.local构建通过。审批记录独立测试在通用配置下因既有window测试环境缺失未启动，未计入通过数；浏览器已完成该链路。

真实浏览器方案#4保存/回读业务码0，备注45字符、章节513字符，需求来源引用仍在，随后拓扑与部署计划补齐、提交审批。周文成分别通过施工计划#3与实施方案#4，意见为明确标注的SYNTHETIC测试意见；审批后刷新自动完成S2/S3，项目更新为6/16并进入S4。证据`18-*`、`19-*`、`22-*`至`25-*`，日志`.run/solution-chapter-storage-*`。此修复仅前端，无新增后端重启；178禁DDL配置及既有审批/结项历史保留。

### S4合成浏览器验收与联调日志重传

2026-10-25结项补验：用户明确批准既有管理/材料审核账号发布受控流程新版本。通过浏览器导入当前冻结包8479be30内原件，模型草稿及新定义v3逐字节SHA-256均为8488f235f0e105c8e1968b969f2be4297b4471ff0ea64bc3c3851e6a12d2be9f；v1/v2原资源散列未变，正常发布停用旧定义，历史保留。杨凯添成功提交申请2114146990853451777，BPM实例2d4023e8-d008-11f1-b922-00ff0b78abd7；周文成于08:08:47完成serviceManagerReview，材料审核人仍为冻结用户1。

材料审核通过触发最终重验时失败并回滚：SatisfactionTaskBusinessObjectProvider.lockAndRevalidate调用采集待办受派人专用queries.get，报SATISFACTION_TASK_NOT_FOUND；项目结果只读查看与受派人工作队列是已有分离边界，既有测试还明确要求锁定命令保留受派限制，不能直接删除该校验。当前申请IN_REVIEW、materialReview未结束，项目仍15/16，未伪称结项。拟采用仅绑定当前结项实例、项目、快照与冻结业务结果的服务端内部重验，保持采集待办原权限；该边界需用户确认后实现，不直接扩大通用lockAndRevalidate权限。55-*至59-*为本次证据；58-*仅记录最终审核尝试，成功与否以59-*回滚核对为准。未修改后端代码、重启、迁移或DDL。

2026-10-25受控内部重验实现：需求方明确批准结项实例专用路径，保留满意度采集待办受派限制。新增内部对象契约及ACC所属审核证据校验，逐项绑定当前登录审核人、租户、项目、申请、BPM实例、冻结快照/摘要及版本，并复用既有双人工审核证据校验；项目侧保持原查看权限、事务锁与关联结果版本复核。无新增HTTP接口、权限授予、历史回填或数据库结构修改；普通待办查询及普通锁定命令仍执行原受派人限制。

失败测试先复现SATISFACTION_TASK_NOT_FOUND；修复后8组90项相关测试通过（组合运行中一项测试mock重新设定错误已修正，受影响36项单独重跑全部通过），日志`.run/closure-internal-before.log`、`closure-internal-combined-tests.log`、`closure-internal-project-retest.log`。固定脚本完整构建成功，冻结包`a3b235f3-e6f4-4dd1-a46b-4a21e09428b7`；22项已部署修复保护及worktree-backend部署预检通过，日志`.run/closure-internal-build.log`、`closure-internal-preflight.log`。当前尚未重启部署，原材料终审任务仍待办，项目15/16；待取得本次明确重启批准后继续原任务真实浏览器验收。178库保持禁迁移/禁自动建表/禁DDL，部署前只读结构指纹证据`.run/test178-switch-20261025/before-closure-internal-deploy-20261025.json`。

2026-10-25结项收口（本项目验收完成，不代表整个Feature全部范围完成）：需求方对上述冻结包明确批准本次重启后，固定worktree-backend部署成功，PID41316，local,dac、健康UP；未重启DAC。现场核对11条后端连接匹配178/npdms，17项有效配置来源确认；Flyway/Liquibase/SQL初始化及Flowable/Hibernate/Quartz/Batch自动建表关闭，DDL Wall生效。部署前、部署后及浏览器结项后结构/Flyway指纹完全一致（520表、9490列、4218索引元数据、241触发器、22条Flyway历史）；日志`.run/closure-internal-deploy.log`及`.run/test178-switch-20261025/*closure*.json`。

真实浏览器以原冻结材料审核人用户1重试原materialReview任务3fea4721-d008-11f1-b922-00ff0b78abd7，08:25:58审批通过；原申请2114146990853451777为APPROVED，仍绑定原流程实例2d4023e8-d008-11f1-b922-00ff0b78abd7及原快照2114145661477187585。周文成08:08:47的原审核记录及意见保留，新增材料终审归档记录；没有重新建申请或覆盖旧审批。完整刷新后项目为NORMAL_CLOSED、16/16、100%，S6任务完成；杨凯添和周文成分别重新登录真实浏览器回看均一致，结项面板只读、不再提供校验/提交入口，BPM历史保留实际处理人、意见及时间且无通过/拒绝按钮。满意度采集待办2114145050731999233仍归杨凯添2112914651746512967，结果2114145238208999426及PASSED保留，未增加权限或修改受派人。证据`.run/project-16200721122903N-20261025/60-*`至`67-*`，其中62为只读数据库核对，业务提交全部由浏览器完成。项目64及74～78未进行操作或历史回填。

本轮界面效果：现有深色风格、任务绿色完成标识与闭环成功提示一致，结项后只读提示清晰，两个角色刷新后无状态回退。仍存在流程图页签只有空白网格（66截图）以及申请详情直接显示人员ID和TASK_CONTRACT_CURRENT等技术码（65记录）；流转记录页签正常显示姓名、审批意见及时间。上述展示问题仅记录，未扩大本次修复或重写已冻结流程资源。验收使用明确标识SYNTHETIC的材料，不代表真实客户签字或生产设备交付；割接设备覆盖仍为此前记载的1/15。

## 2026-10-25 正常闭环流程图与审批详情展示修复

需求方明确批准纯前端方案，修复流程图空白和技术字段直接展示。旧实现审计确认：冻结BPMN有真实节点/连线而无DI坐标，原MyProcessViewer复用；新增normalClosureDiagram只对缺坐标的PMS_MINIMAL_NORMAL_CLOSURE在脱离原件的内存XML中生成展示坐标，按实际sequenceFlow排序，保留原节点/连线ID与执行扩展，不保存、不发布、不替换冻结资源。已有布局及其他流程原XML保持；无法确定的非串行图只显示说明，不伪造节点或路径。补布局图文字使用现有主题色，节点点击继续回看原审批历史。

NormalClosureProcessView以中文条件、实际姓名和原审批意见为主展示；申请/任务/人员ID、原始校验码、快照摘要及冻结流程证据放入默认关闭的技术详情，原值保留。姓名复用本审批实例已授权的BPM时间线数据，按实例ID和业务Key双重匹配；不额外查询人员目录、不加权限，缺失或其他实例的姓名不借用，明确显示“姓名未获取”。正常闭环页头流程编号同时折叠，其他审批页头保持原样。无后端接口、状态机、数据库或冻结流程变更。

最终验证：3组5项定向测试通过（实际连线排序、分支/环/缺失节点拒绝绘制、中文未知码回退、只读大ID精度、姓名上下文绑定和缺失回退、技术原值保留且默认折叠）；定向ESLint、完整vue-tsc及env.local前端构建41.41秒通过，日志`.run/closure-display-{tests-final,eslint-final,types-final,build-final}.log`。19191现有Vite加载前端修改，未重启前后端或DAC。源码热更新期间曾短暂空白，保留78-*当时截图；原实例关闭重开后的最终稳定结果以79-*和81-*为准，不以热更新中的截图冒充通过。

周文成与杨凯添均通过真实浏览器重新登录、刷新原项目44637、打开原审批实例完成最终回归：默认显示杨凯添/周文成/系统管理员姓名、中文条件，无申请长ID或TASK_CONTRACT_CURRENT等技术码；技术详情展开可追溯原始ID、校验码和冻结候选证据，收起恢复；无通过/拒绝操作按钮。流程图均显示发起正常闭环、服务经理审核、工程材料审核、审核结束四个真实节点及三条原连线；周文成点击材料节点可回看原处理人、意见、08:25:58审核时间。完整刷新项目保持NORMAL_CLOSED、16/16、100%。最终详情证据77-*与80-*，最终流程图79-*与81-*，节点历史71-*，其余过程证据69-*至78-*保留。

只读保护核对74-display-history-protection.json：当前项目/原申请/快照摘要、两条人工审核及BPM任务、满意度受派人与62-*结项收口快照逐项一致；冻结BPMN v1/v2/v3字节散列与55-*一致。178库结构及Flyway历史与before-closure-display-20261025指纹一致，未执行迁移或DDL。75-display-runtime-178.json确认59191仍为PID41316、健康UP、实际178/npdms连接、17项迁移/建表/数据源有效配置；不改变其他项目或既有历史。

2026-10-25剩余流程浏览器验收：杨凯添新建割接2114142547964006400，设备限定FW的99020573D222000007，合成D级问卷、简易操作/回退方案，发起人及周文成服务经理双评审通过。审批后项目任务按冻结CUTOVER_APPROVED契约完成并进入S5；独立割接详情继续正式填写闭环，绑定两份明确标记SYNTHETIC的PNG凭据并提交，稳定结果引用CUTOVER_CLOSURE:2114143251499782144:2，07:54归档。范围结果显示1/15设备，未伪称覆盖全部设备；未执行生产命令。

培训记录3通过正式页面创建、生成本地确认入口，并在公开页面填写明确标记合成用途的评价及非真人签名的S测试笔迹；客户确认后任务自动100%。过程中复现培训工程师下拉调用system/user/page而被权限拒绝；改用既有system/user/simple-list（与配置调试一致），本地搜索分页，保留已选人员及失败保留逻辑，不授予系统用户管理权限。失败测试5项先复现；修复后11项通过，定向ESLint、完整vue-tsc和env.local构建37.63秒通过；杨凯添浏览器搜索回读业务码0、无权限提示，原工程师保留。

完工证明采用SYNTHETIC客户单位，仅生成本地确认链接，不发送真实客户消息；统一确认页填写十项合成评分80、明确测试意见及S测试笔迹，生成有效满意度结果，任务13/16。初验2114145339652435970及终验通过受控文件入口上传明确SYNTHETIC PNG，保存、刷新后已完成，项目15/16、94%，进入S6。培训和调查均为测试流程证据，不是实际客户签字、真实实施或客户评价。原批准方案4、旧附件及审批历史未覆盖。

结项尚未完成：闭环条件校验全部满足，提交真实审批返回业务码500；日志明确NORMAL definition is not the approved packaged manual-review template。只读核对178活动BPMN为v2，其SHA-256 e49289a57ba6db4869b0c151b4e7e43f99f782bd83a1f71d3bb7a19ed86f5f36，与受控资源不同，原v1停用并保留。未放宽模板验真，未改流程定义。项目冻结计划7的材料审核人是用户1，不能擅自替换为杨凯添或周文成；acc_project_closure本项目无申请行，失败未创建审批。需获准通过有权限的BPM管理浏览器会话发布受控流程新版本，并由既定材料审核人办理，才能继续结项。

证据为`.run/project-16200721122903N-20261025/40-*`至`54-*`；43-*为首次提交前后中间状态，不作为成功依据，割接成功以44-*为准。运行PID46488复核178主从、Flyway/Liquibase/自动建表关闭及DDL Wall有效；只读元数据复核仍为520表/9490列/4218索引元数据/241触发器/Flyway22条。本轮无后端重启、迁移或DDL。界面记录：长保存期间禁用重复提交有效；项目任务在审批通过后完成，割接闭环须独立入口继续；附件回读后需重新选择未保存的闭环结果；客户确认页签字和声明门禁有效；初终验业务结果与任务投影需刷新核对。

2026-10-25界面局部回归：施工计划无记录时工具栏与空白提示同时渲染创建按钮，移除空白提示内重复入口，保留工具栏及原权限、工期和处理中禁用条件。定向ESLint及三组61项既有运行时测试通过；真实浏览器当前已生效计划仅显示一个“新建调整版本”，未为复验改写既有计划或制造无计划状态。实施方案字段超长问题由需求方确认已修复；浏览器新增草稿#5并保存750字符SYNTHETIC培训内容，服务端回读status=0、version=1、remark=45字符、sectionData=892字符，未提交审批。证据`38-solution-draft-save-readback.json`和`39-schedule-toolbar-after.txt`。批准方案#4及已有审批保持只读；无后端重启、迁移或DDL。

到货签收经页面上传明确标记SYNTHETIC的凭据；硬件实施使用既有“站点未维护”兼容路径，15/15设备追加验收记录，不代表真实现场安装。配置调试先验证无日志时99%门禁，按已授权固定脚本独立启动本地DAC及回调网关后，上传不含Secret、不连接生产设备的合成日志，归档v1；记录完成后任务100%，项目9/16。未重启后端、迁移或执行DDL。

联调记录通过后仍需有效文件。按需求方“再上传一次日志”，在联调任务的“上传日志 / 文件历史”入口重传同一合成文件，归档v2。既有任务来源规则自动归集D_JOINT；重新检测及刷新后联调完成100%，项目10/16、63%，割接任务开放。无需新增归集逻辑，初查的来源缺失判断已在Q-PROJECT-JOINT-DELIVERY-SOURCE-20261025更正。证据`.run/project-16200721122903N-20261025/26-*`至`37-*`；旧v1、审批及项目历史保留。全流程尚未结项。

界面观察：联调确认框将HTML标签显示为文字，联调人输入框要求数字ID但缺少人员选择；日志归档后任务卡仍需交付件重新检测及刷新才更新。上述仅记录，未扩大界面修改范围。独立验收标签与用户正在操作的原标签分离。

## 2026-10-23 SYSTEM组织范围维护补齐

需求方确认在用户管理增加公司部门范围维护。Owner为SYSTEM，既有合同、项目、设备消费者只读取原OrganizationScopeApi。旧实现审计：既有Scope DO/Mapper/API及V64模型直接复用；公司/部门精简列表直接复用；用户管理以新增独立组件挂接，原角色分配、用户字段及旧接口保持。此前无Scope维护Service/Controller，补齐独立应用服务与PMS Business API，契约见SDS10本日“用户公司部门范围维护”。原Task 1历史COMPLETE仅覆盖原基础查询，不自动覆盖本次维护增量。

- 实施：用户范围分页、新增、事务修订、版本停用、用户可见范围与租户校验、SYSTEM操作日志；新增独立查看/维护权限。原行组织/角色/时间区间不覆盖，失败回滚。
- 初始化：前向迁移新增用户管理按钮权限；专用停用示例账户的范围样例覆盖部门级、公司级、多角色、未生效、到期、停用；不给实际用户追加业务授权。
- 验证：本次SYSTEM范围维护增量已完成定向测试、前端检查、聚焦迁移及管理员真实浏览器验收，详见本节2026-10-24收口；不改变F-PROJ-001其他待决业务范围的完成状态。

本次验证结果（2026-10-23）：

- SYSTEM定向测试23/23通过：新增、同一组织行、公司级空部门、多角色、修订原行保留、写入失败事务回滚、陈旧版本与重复停用、到期/未生效不授权、失效主数据仍可撤权、用户绑定、真实租户拦截器与SYSTEM用户数据范围拒绝；接口方法安全验证查询/维护权限分离。命令沿用worktree-toolchain及同一独占锁；日志`.run/user-org-scope/backend-tests-verified.log`。
- 前端交互5/5通过：部门精简响应不带status仍可选；公司级提交/毫秒时间戳；只读权限；非法到期；查询失败重试；用户切换迟到响应隔离（首项合并覆盖精简响应与提交）。完整vue-tsc、定向ESLint、离线图标校验、Vite env.local构建通过。日志位于`.run/user-org-scope/frontend-*.log`。
- 固定npdms-50eb-test环境的应用账号拒绝连接，未更改凭据、重置数据或执行迁移。当前npdms_domain_test全链Flyway检查仍有11个既有未执行历史版本（385/387/478/491/492/493/498/499/500/502/503）；未补跑、repair或清理，原失败日志`.run/user-org-scope/domain-migration-validate.log`保留。
- 复用该验收库既有聚焦迁移方式：Compose Flyway只读挂载V515独立位置、target=515，实际MySQL执行成功，重复migrate无待执行版本。版本515 success=1，新增两个按钮权限、一个停用/空密码示例账户、五条组织范围样例；实际用户授权未修改。日志`.run/user-org-scope/migration-515*.log`。该证据仅证明V515，不代表全链历史缺口已解决，也未执行空库全链重建。
- 后端统一脚本完整构建成功；部署保护发现唯一未登记差异为DeviceAssemblyMapper.xml换行字节，移除CR后与保护基线完全相同，已登记精确SHA-256等价继承，未改SQL源码/业务/校验器。冻结发布清单`.run/build/releases/b29f71c5-9825-4864-a414-d9f67f35ff92/manifest.json`，22项已部署修复保护及worktree-backend -ValidateOnly通过。
- 真实浏览器已通过现有管理员会话打开19191用户管理，无页面错误；新增范围的新增/修订/停用/刷新业务闭环尚未执行。59191仍为原运行包；必须取得本次明确重启批准后由worktree-backend使用上述清单部署，再进行本次真实浏览器验收和操作日志核对。当前增量为实现与部署准备完成，不标记UI验收或Feature Done。

2026-10-24进展：需求方明确批准发布清单b29f71c5-9825-4864-a414-d9f67f35ff92部署并重启59191，统一入口已完成（local,dac，PID17788），健康UP，22项保护通过；日志.run/user-org-scope/deployment.log。真实浏览器已打开管理员公司部门范围并显示原五条范围，非法到期时间被阻止；创建未来2099-01-01生效范围2108571489659667376成功，显示“未生效”、总数5→6，该行不扩大当前访问权限，原五行未改。尚待变更/停用及操作日志闭环。需求方随后转向设备管理越范围查询并指定z01010，当前浏览器已切至该用户，暂先处理Q-AST-DEVICE-CONTRACT-SCOPE-20261024；未把未完成环节记为通过，待恢复管理员会话后继续并停用上述未来验收记录。

2026-10-24管理员验收收口：需求方切回管理员后，在独立临时浏览器标签完成原未来测试行的变更及停用，保留用户原标签中“曹浪”的新增草稿。通过页面将2108571489659667376的生效时间由2099-01-01 10:00:00修订为2099-01-02 10:00:00；原行停用、版本0→1且原日期保留，新行2108571489659667377创建成功，全部记录6→7。再通过页面停用新行，新行状态1、版本1，两条均显示“已停用/历史保留”。刷新后保持；启用筛选5条、停用筛选2条。测试行全程未到生效时间，当前有效范围仍为5条；原五行的公司/部门ID及编码、角色、状态、版本、生效/到期时间与初始快照逐行一致。

操作日志9252/9253/9254分别记录新增、修订（原行和新行ID）及停用，操作者1、业务用户1、success=1；对应真实POST入口，未直接写库完成业务操作。只读数据库用于前后核对，未删除历史。临时标签已关闭，原草稿未改动。该标签无控制台error。证据.run/user-org-scope/browser-revised.png及.txt、browser-disabled-after-refresh.png及.txt、browser-enabled-filter.txt、browser-disabled-filter.png及.txt、browser-audit-final.tsv、browser-scope-and-audit-after.tsv。复用本节既有23项后端测试、5项前端交互与类型/构建、V515迁移证据，未重复构建或重启；历史Flyway全链缺口仍按原记录保留，不将聚焦迁移成功表述为全链通过。

### 2026-10-27 TF-008 / TF-024 / TF-025 / TF-026 测试反馈修复

- TF-008外部最终用户电话/邮箱沿用CUS-04基础格式约定，前后端拒绝无效格式，保留可空和国际电话；历史联系方式不回填。前端16项、后端26项通过；真实浏览器替代账号负例不发业务写请求。后端待部署，有效保存闭环未执行。
- TF-024增强页solution-reviewed复用loadProjectEquipmentList返回的真实型号、描述和精确订单数量，并按产品编码聚合SN。未知数量仍为空，已有保存/只读方案不自动重写；旧方案消费者仅补相同返回类型的测试夹具。3组28项通过；真实项目84459引用17行，16行型号、17行描述、15行数量、16行SN有值，未保存。
- TF-025/026增强页导出将上传图片实际字节嵌入DOCX并复用既有DPtech PNG；非图片拓扑保留原文件链接，不声称Visio/PPT已转图。读取失败拒绝生成空图片。组件/实际DOCX媒体关系与失败检查27项通过，未真实写入交付件或做Word视觉验收。PRE-04原附件引用TF-028仍独立待衔接，不宣称已完成。
- 本轮定向类型检查、ESLint、env.local构建及固定工具链后端构建通过；实际部署与业务写入验收未执行。原始来源、样本与边界统一见docs/generated/2026-10-26-test-feedback-triage/问题跟进.md。

### 2026-10-27 TF-008 / TF-024 / TF-025 / TF-026 部署后受控浏览器验收

- 需求方批准后，冻结包6c41e0a2已按固定脚本部署至59191。TF-008由m06425在84459真实UI验证错误邮箱不发写请求；明确标记的测试联系人使用国际电话和example.invalid邮箱保存，重开一致。留存测试成员assignmentId2113121068914852013，不回填原历史成员。
- TF-024另建独立测试方案43（DRAFT/version0），产品引用保存重开17行：型号16、描述17、项目数量15、发货数量15、SN16行有值；未知数量保留空白。原方案10/11/16/17/22的状态与版本保留，测试草稿不加入已批准方案完成关联，S3任务仍DONE/100%/version3。
- TF-025/026通过页面上传明确TEST_ONLY拓扑、保存并下载Word；交付材料362/fileReferenceId506、类型IMPLEMENTATION_PLAN、来源GENERATED登记成功。实际下载178383字节DOCX的拓扑和LOGO由内部drawing关系引用，字节哈希分别与上传图和仓库品牌图完全一致；已提取查看两张图像。没有将Visio/PPT链接算作已转换图片。
- 真实导出证据：.run/feedback-20261026/acceptance/tf025-word-export-evidence.json、tf025-tf026-downloaded-docx-validation.json；文件SHA-256为df4292c2f83856586702e100fc3d85b4ea667fa78e21497dfe968884af52e557。标准render_docx.py实际执行因缺少soffice.exe失败，整份Word逐页排版尚未视觉验收，未将结构/字节检查等同于版面验证。
- 原始反馈、各项边界和仍待处理问题统一记录于docs/generated/2026-10-26-test-feedback-triage/问题跟进.md；无提交审核、归档真实割接任务或直接写业务生命周期状态。

### 2026-10-27 TF-038 发货数量以订单行为唯一来源

- 需求方明确发货数量直接采用订单行值，不根据设备发货数据聚合。已核对当前Owner链：com_sales_order_line.delivered_qty→OrderController的deliveredQty→projectEquipment.ts→项目清单及方案引用；同一订单行按精确ID去重，设备数与SN不参与数量计算。
- 当前计算主来源已符合该口径；修复其源发货量null/缺失经Number转换为0/NaN的问题。未知发货量保留空值，明确的0保留；不改写已有方案库存快照、设备发货状态或数据库数据。
- 失败用例先复现0/NaN，修复后订单来源与方案组件共26项通过，覆盖订单量与设备数不一致、发货0、无设备的软件发货数、缺失值和重复范围去重；ESLint及定向vue-tsc通过。
- 真实浏览器只读核验84459：产品02052555设备数0但发货数1，与精确订单行2113088200020951202的deliveredQty一致；两条无订单关联的软件产品各有6台设备，发货数仍空。证据.run/feedback-20261026/acceptance/order-line-shipment-source.json及对应宽屏截图；空源值分支由定向失败测试验证，不声称真实库出现过该样本。本次无后端变更或服务重启。

### 2026-10-27 项目详情设备清单查询来源回归修复

- 来源：需求方进一步明确项目详情设备清单此前已改为直接取订单行或已分配订单行。首轮仅核对发货字段，遗漏行集合；当前共用projectEquipment.ts将范围明细和设备产品合并，84459的25条已分配订单行实际15种产品，页面却显示17行。
- 旧实现审计与边界：复用COM现有项目交付范围、订单行及AST产品只读接口；项目详情是本次消费者Owner，服务端权限/查询契约未变。2026-10-10完工证明曾独立批准保留完整设备产品及deviceCount，故复制增强为projectOrderEquipment.ts供项目详情使用，保留验收证明、方案旧消费者和已保存快照，不把其统计规则改成订单数。
- 实现：当前ACTIVE且无effectiveTo的已分配订单行决定产品集合及项目数量，范围拆分明细和SN不参与；发货/未发货字段取精确订单行ID对应的源值，同一行只计一次，缺失值仍未知。名称取订单描述、型号精确匹配官方产品编码。空范围不扩大到其他订单或设备，分页失败不返回部分范围；逐台数量仍在序列号页查看。
- 验证：新来源回归用例在旧实现上先复现8项失败，修复后新旧查询13项通过；相关ESLint及包含项目详情的定向vue-tsc通过。真实浏览器19191/59191使用m06425只读验收84459：清单15行、项目数量119、发货119、未发货0，无devices/page请求及API/页面错误；序列号页仍130台，设备独有53012931/53012932各6台，仅从订单清单排除。源码类型注解修正不改变已验收运行行为。
- 证据：.run/feedback-20261026/acceptance/project84459-order-equipment-browser-acceptance.json、equipment-order-source-regression-before.json、project84459-equipment-order-source-15rows-top.png及bottom.png；测试/检查日志order-equipment-source-before.log、order-equipment-source-after.log、order-equipment-lint.log、order-equipment-types.log。历史项目992203060190在当前账号环境返回项目不存在/无权限，不声称复验通过。
- 本次仅前端修复，无业务数据写入、后端构建或服务重启，未提交或推送；其他反馈项状态保留。

### 2026-10-28 TF-017 项目列表普通信息编辑入口隐藏

- 按需求方已明确的“隐藏项目列表信息编辑操作”，仅移除 `projects/index.vue` 列表行的普通“编辑”按钮。复核既有按钮、编辑弹窗及 `openEdit/submitEdit`：本次为已授权的入口收敛，直接修改当前入口，不复制新页面；原编辑实现保留，更正客户、项目成员、详情、创建及并行办事处精确查询保持原样。
- 已检查本次差异无空白错误，并完成 Vue SFC 模板编译；没有新增仅断言按钮源码的镜像测试。未修改后端授权、项目主档或历史数据，未提交、推送或启停服务。真实浏览器尚待指定验收会话核对列表操作及项目详情入口，不能以源码检查替代业务验收。


## 2026-10-29 TF-011 隔离候选（旧功能范围）

- 用户已裁决未确认不授予，成员授权与成员区间有效期一致。逐调用分类见.run隔离证据member-authorization-callsite-scope.md；TreeScope与任务成员授权使用确认+区间查询，事实筛选/展示/StageReadiness不增加授权Gate。SYSTEM SPI各Owner自查，不接受前端授权IDs，不回退全目录。
- 保留最新UserSelect并复用scopedSource，仅接专项分页API；撤下原专用组件方案。此前projects/index.vue缺失systemUserSelection import已由并行工作补齐，本候选重基保留且不重复增加，不改回填逻辑。
- 真实Mapper+TreeScope确认位两项先红（2个功能failure/0error）；之后211项后端通过、前端新旧复用20项通过。部门SQL一项及真实Advisor外层ignore一项分别先红后绿，类型复跑通过，完整结果以review-status.json为准；历史失败均保留。源码/资源/测试仅在.run，不写活源码/target，不重启/迁移/写业务数据。
- 性能SQL先离线提交根会话，未建立DB连接；现无真实受限Owner完整scope，不用手工组装或旧自过滤11/156当证明。Owner准备+查询+浏览器<1秒仍待受控应用后的真实账号验收。候选未应用，不记反馈关闭或Feature完成。

- ParticipantFact事实/授权边界已与TF022 Owner交接；本候选不改engineering及其事实API。共享ProjectMemberAssignmentMapper两文件已冻结hash供TF022增量依赖，不整覆盖。真实部门DataPermission拦截器、隐藏/跨租户/删除/停用部门、外层ignore恢复均有行为测试，未实际连接数据库。

- 部门名称在同一请求内复用已获权搜索结果，仅补本页缺失部门IDs，避免重复回表；最终后端211/211，前端20/20，类型无诊断。实际账号性能与浏览器仍未通过，候选保留reviewOnly。

- NO-GO整改已转D盘：恢复并核验207a原56文件，真实独立MANAGE+未确认PM先复现3/3失败（startProcess实际触发、insertSnapshot实际触发、实体经理/锁放行），再修复；现行动态表单workspace/锁也另获真实红证。最终263/263、40容器成功，0skip/abort/容器失败；选择器缺失即失败，旧226含解析警告结果不采纳。仅D源码副本/classes/temp/log，固定JDK及共同锁；不写E活源码/target、不改业务库。未运行MySQL业务IT或真实浏览器/一秒验证。
