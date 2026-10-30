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

### 2026-10-24 合并差异修复（代码完成，浏览器验收部分完成）

按用户批准的[界面与功能复核报告](../../docs/generated/domain-migration-comparison-20261023/ui-function-review.md)串行修复：先处理正常END_USER维护、历史附件查看、概览辅助请求失败，再恢复源分支界面与功能。项目交付、割接、巡检以源分支为主；同步集成保留当前实现，已有明确裁决优先。以小范围差量恢复，不整文件回退，不改授权、不可变历史及状态机边界。

- 本次同一修复任务跟踪报告R01～R12和旧继承入口的原可见字段；不新增业务契约或跨Owner接口，不修改源分支工作树。
- 验证覆盖：成员有效/历史查询及维护、历史角色60拒绝与租户隔离；概览关系读取失败；附件合法相对链接与不安全协议；创建来源预填及来源一致性；受影响UI真实浏览器。编译、单测和浏览器结果分别记录。
- 割接R10保留“引用失效不读取版本”的既有保护。当前两个接口无法同时保证该条件与无条件并行，不能用删除失败保护恢复并行。
- 后端构建沿用工作树工具链和锁；重启须单独取得明确授权，未部署代码不能写成运行通过。Feature总体状态不因本次局部修复改变。
- 已完成R01～R09、R11～R12差量修复及P09/P25/P41展示恢复；R10按失败保护保留串行。前端定向测试、全量类型检查和后端打包通过；成员40项中39项通过，1项既有表列数断言失败（期望34、实际37）；拓扑附件7项通过。详细结果见上述报告末尾修复回执。
- 使用已停止的独立19085/59285环境启动当前冻结包完成真实页面核验：19项基础布局、合同汇总、来源明细和类别预填可见。没有重启59191/DAC或写远端业务数据。当前数据缺正常END_USER及旧配置附件样例，成员写入、旧文件下载和无日志设备占位仍待浏览器补验；原执行计划读取错误仍在，不以局部通过宣称完整验收。

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

## 2026-10-24 大范围项目查询栈溢出修复

数千树范围的OR表达式在生产SQL缓存序列化时栈溢出，已改为根/版本及根/版本/祖先的精确元组IN查询；保留租户、逻辑删除、空范围拒绝及原授权重读。两项5000组范围失败测试复现后，29项SQL/范围定向回归通过；真实MySQL只读比较2881组范围新旧结果一致。完整证据见[修复记录](../../docs/generated/domain-migration-comparison-20261023/project-scope-overflow-fix-20261024.md)。仅代码与定向验证完成，未发布打包、部署、重启或宣称修复版业务浏览器验收通过；原有并行改动保留。224条成员区间冲突待业务裁决，原同步指纹保护保持。


### 2026-10-24 已裁决隐藏及来源修复复查

按用户明确授权移除基础信息三个重复分组，保留19项字段；补齐合同异步加载状态/重试、两新增面板按需导入；选择性迁入来源4d149f34f施工计划独立菜单限制，V516不执行。前端49项、完整类型检查及ESLint通过，独立59286构建部署后真实z01010/y06496列表分别2881/123，详情/合同号可读，范围外详情和编码搜索均拒绝。未改业务数据及角色授权；224冲突与V515保留。当前无生效计划的旧项目提示仍单列，不宣称计划工作区完整验收。完整复查范围、来源取舍、PID外部变化及证据见docs/generated/domain-migration-comparison-20261023/merge-regression-recheck-20261024.md。

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
