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
