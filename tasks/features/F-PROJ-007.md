# F-PROJ-007 项目任务树与原生任务工作台

> Feature实施状态：`IMPLEMENTATION_COMPLETE`
> 总体工程阶段：`IMPLEMENTATION_COMPLETE`
> Feature Ready Gate：`PASS / NPDMS-FPROJ007-FEATURE-READY-20260825-01`
> Implementation Done Gate：`PASS / b559978 / 独立复审GO`
> Technical Plan Gate：`PASS / NPDMS-FPROJ007-TECHPLAN-20260825-01-R1`
> 当前阻断：`无`
> 当前任务：`已完成；新基线已同步，按工程链定位下一Feature`
> Requirement ID：`PM-11（V1）`
> Feature Spec：`specs/features/F-PROJ-007-project-task-tree-and-native-workbench.md`
> Feature物理契约：`specs/features/F-PROJ-007-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-25-f-proj-007-project-task-tree-and-native-workbench.md`
> 锁定规格提交：`2cb73611847aeca25db21313da1e77d4e5394103`

## 当前Gate工作单元

- [x] 从PRD/SDS定位PM-11 V1边界
- [x] 核对V1.7 `pms_project_task`与V1.8 `proj_project_task`双模型
- [x] 形成Feature Spec、物理契约和追溯候选
- [x] 获得独立Feature Ready裁决
- [x] 基于锁定基线全新生成Technical Plan
- [x] 闭环首次计划复审四项NO-GO
- [x] 获得Technical Plan独立裁决
- [x] Task 1 Implementation Done（独立裁决GO；`63c442b`、`20edc84`、`7637add`）
- [x] Task 2 Implementation Done（独立裁决GO；`fa55d74`、`ef4e955`）
- [x] Task 3 Implementation Done（独立裁决GO；`8c2f0f1`）
- [x] Task 4 Implementation Done（独立裁决GO；`d5892ae`、`43b556c`）
- [x] Task 5 Implementation Done（独立裁决GO；`25071a2`、`bba329e`）
- [x] Task 6 Implementation Done（独立裁决GO；`26cce6b`）
- [x] Task 7 Implementation Done（独立裁决GO；`687388b`、`4db86ed`）
- [x] Task 8 Implementation Done（独立裁决GO；`62c496a`）
- [x] Task 9 Implementation Done（独立裁决GO；`0c64a8e`、`3106797`、`e266992`、`d414336`、`314059c`、`3ff1cd2`）
- [x] Task 10 Implementation Done（独立裁决GO；`b559978`；证据`F-PROJ-007-TASK10-BROWSER-MYSQL-PERF-20260826-01`）

> 检查点（2026-08-26）：基线`5f37b2d`；Task 10与Implementation Done独立GO，提交`b559978`及证据`F-PROJ-007-TASK10-BROWSER-MYSQL-PERF-20260826-01`闭合真实MySQL、性能、Outbox和四档浏览器验收；无阻塞。下一步回写规格追溯/Feature索引并同步新基线，不进入Deployment/SIT/UAT/Release。

## 2026-09-07 修订018影响记录

模板业务规则配置化与独立验收设计已获需求方确认，正文及当前Feature Spec已登记`CHG-PRD-2026-09-07-018`。上文Implementation状态、提交、测试及历史Done保持原范围，不自动覆盖新语义；当前Ready以Feature Spec的REVALIDATION_REQUIRED为准。Q-TPLACC-001限制新增实体创建/范围绑定及其消费者路径，须先完成Phase 2差量再更新唯一实施计划，不从本次文档确认派生Task/Feature完成。

## 2026-09-14 浏览器批注局部调整

- 依据：需求方两处任务办理页批注及追加“工勘详情不要自动弹窗打开”；覆盖PM-11任务通用信息与业务界面承载，不修改PRD/SDS、API、数据库、Owner权限或状态机。
- `ProjectFlowPanel`将任务说明放入基本信息表的全宽单元格，继续使用净化HTML/纯文本展示；`TaskMaintenancePanel`共享原说明与编辑入口，保存、版本、权限和离开保护不复制。
- `TaskStateActions`通过插槽容纳`ProjectTaskDetailsEditor`，编辑资料与其他任务操作同行，窄宽时自然换行，进度表单占整行。
- `site-survey/index.vue`在任务上下文中只装载列表，不因已有objectId自动打开详情；明确点击列表“详情”才打开。独立工勘精确链接行为保留。
- 定向验证：5套19项（流程、任务资料、抽屉共享、状态操作、说明维护）通过；3套70项（工勘上下文、BusinessView、任务业务宿主）通过。均使用仓库Vitest内存渲染及mock API，不连接数据库、不写业务数据。自审完成，不等同独立审查。
- ESLint：4个任务组件0错误、7项既有/样式告警；工勘页仍有原有`readonly` prop/computed重名错误（本轮仅修改其自动打开条件），未借本轮顺手修复。未执行全库构建或后端测试。
- 真实内置浏览器：在`19081`、项目`993109130057`的独立临时标签验证；完整加载后任务说明为表格行，工勘任务进入无详情弹窗，点击详情正常打开，关闭并刷新后无自动重开，捕获error日志为空。用户原标签和业务数据未改动；验收时该任务已DONE，因此可编辑按钮布局此前由热更新页面观察，保存/拒绝保护由上述定向测试覆盖，未宣称重新执行真实保存。
- 原第二条批注的自动关联/关联区撤除依赖多记录归属裁决，登记`Q-TASK-BUSINESS-AUTO-LINK-001`，保持BLOCKED_BY_SPEC；本轮未隐藏原手动区来冒充自动关联完成。未提交、未推送，不晋级Feature/Gate。

### 后续确认：统一自动关联与自动完成

需求方已确认所有业务模块共用统一机制，业务完成后自动关联对应任务，在任务完成规则满足时自动完成，无需人工关联或点击完成。`Q-TASK-BUSINESS-AUTO-LINK-001`已记录该确认，不再重复询问是否自动完成。当前只读核对发现其他来源的自动完成/事实接口增量已在同一工作树写入，范围直接重叠；待明确接管或分工后复用已有增量，统一补齐关联解析、Owner接入、页面和验证，不另造第二套自动完成服务。本轮仅记录确认与实际协作阻断，没有修改重叠实现、运行服务或业务数据，也未测试/宣称上述他方新增代码通过。

### 2026-10-27 TF-034 已批准方案被草稿/驳回阻塞

- 原项目84459的PLAN_SOLUTION任务2114152121376350214读取到方案10/11/16/17/22，冻结ALL规则中旧驳回10和草稿22返回false，导致三份已批准方案不能完成任务。Q-TASK-BUSINESS-AUTO-LINK-001已明确仅纳入已确认/审批通过等有效业务成果。
- SolutionCompletionMapper候选分页改为仅status=3；保留租户、项目、类型、删除、游标边界。按ID读取旧方案继续可用。既有关联同步会关闭不再合格的活动关联并保留关联历史，再经状态机重评估；本轮未直接写任务状态或删除旧业务记录。
- 真实生产Mapper的隔离H2失败用例先证明错误纳入，修复后与SolutionApprovalCompletionProvider共6项通过；既有关联/完成评估器32项通过。完整后端构建及22项已部署修复保护通过，但未部署或刷新原项目，未把任务标记为已恢复完成。
- 证据与剩余验收见docs/generated/2026-10-26-test-feedback-triage/问题跟进.md。

### 2026-10-27 TF-034 部署后原项目浏览器复验

- 需求方批准后，冻结包6c41e0a2已通过scripts/worktree-backend.ps1部署至59191，固定local,dac，运行JAR与清单一致。
- 原账号m06425读取项目84459任务2114152121376350214：DONE、100%、version3；当前仅关联批准方案11/16/17且三个完成事实均为true，修复前的驳回10和草稿22已退出当前关联。读取时已完成且无可用操作，因此未再次人工刷新，也未直接写生命周期状态。
- 随后另建测试方案43并保存后，任务仍DONE/100%/version3，草稿43未被纳入批准方案关联；原10/11/16/17/22状态与版本均保留。证据：.run/feedback-20261026/acceptance/tf034-current-task-state.json、tf025-task-link-isolation-evidence.json、tf025-new-draft-reopen-evidence.json。
## 2026-10-27 用户补充：持续维护与提交时序

- 授权口径：设备安装地址、日志等持续维护不因任务已完成而消失；实施方案前置任务未完成时允许维护草稿，禁止提交审核。
- 复用审计：现有任务面板统一按 DONE 只读；安装地址批量服务已有独立权限与项目范围校验、新记录及历史生效边界；日志归档页面已支持任务完成后的追加。方案分级审批缺少当前计划前置校验，复用 PROJ 冻结准入规则，不新增固定阶段顺序。
- 实施范围：分开草稿维护与任务执行上下文；增加 PROJ 跨模块准入检查，SOL 新旧提交入口共用，前端展示阻塞原因。保持旧记录、状态机、审批与权限；不涉及数据库迁移。
- 验证计划：前置未满足/未知时无冻结、无状态写入、无流程；满足后提交、重复提交幂等；完成后维护入口、显式只读与非维护任务保护；真实浏览器验收需后端产物部署后完成。
- 定向结果：先复现2项后端缺失拦截及2项前端维护入口失败；修复后45项后端测试、59项前端运行时测试通过，定向vue-tsc和ESLint通过。实现复核修正了新依赖环（按需获取准入服务）及显式只读时的草稿删除遗漏；旧任务开始规则、历史记录与审批幂等仍受原检查保护。
- 浏览器：本工作树19191（E盘源码、59191后台）已验证84447安装/配置/联调DONE任务的维护和上传入口、安装历史只读及任务版本不变；66943的S0方案草稿可编辑。全程无业务写入。19086属于M盘853c工作树，仅作为入口定位，不作为本次修复验收。
- 追加复核修复安装地点全局查询：复用项目范围接口；不查询范围外历史站点，阻断无效引用保存，显式选择新地点后继续维护；站点请求代次保护避免旧响应覆盖。6项新增失败分支均先复现再修复，地点/安装4套21项通过；当前合计45项后端、80项前端定向测试通过，类型/ESLint通过。证据见主问题跟进MD及task-maintenance-location-scoped-final.json。
- 后端已完整构建并冻结至.run/build/releases/556f837d-f909-4430-afd1-92bb8ff1d928/manifest.json，22项已部署保护及只读部署预检通过。需求方单独批准本次部署和66943非业务方案草稿验收后，已通过固定脚本部署59191（PID43108，UP，local,dac，运行JAR一致）。未启动/重启DAC、执行DDL/迁移、提交或推送。
- 部署后真实闭环：66943的当前计划前置返回allowed=false（所属阶段不允许办理此任务）；通过UI创建明确标记非业务方案的唯一草稿44并保存重开，内容一致。章节提交动作先保存草稿至version1，审批弹框显示前置阻塞并禁用最终提交；真实负例POST提交返回业务码400。随后草稿仍DRAFT/version1、review为空、任务2114162968555331591仍PENDING_ASSIGN/version0、项目仍ACTIVE/S0。证据.run/feedback-20261026/acceptance/task-maintenance-submission-browser-acceptance.json；批准历史及真实方案未修改。
