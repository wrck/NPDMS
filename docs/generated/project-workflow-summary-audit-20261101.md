# 项目业务流程摘要统一检查（2026-11-01）

用户要求：项目相关业务发起工作流程时，任务摘要显示项目名称；统一检查项目流程发起逻辑。

本次完成当前工作树代码审计，未修改业务实现、流程定义或历史数据，未重启服务。上一轮19086真实页面中的正常闭环任务摘要为空，与以下代码缺口一致；未为审计发起新的业务流程。

## 发起路径清单

已搜索生产代码中`createProcessInstance`、`createProcessInstance0`、`createProcessInstanceBuilder`及原生Flowable start调用，再追踪PMS冻结定义适配器调用方。

| 项目业务 | 发起实现 | 已有项目身份 | 项目名称摘要现状 |
|---|---|---|---|
| 正常闭环 | `BpmNormalClosureService.start` | projectId | 未传项目名称，未接入摘要提供方 |
| 方案评审 | `SolutionReviewBpmService.start` | projectId | 未传项目名称，未接入摘要提供方 |
| 割接审批 | `CutoverWorkflowService.start` | projectId | 已由`CutoverProjectSummaryProvider`读取审批实例冻结的project_name并补入待办、已办摘要 |
| 阶段施工计划审核 | `StagePlanBatchServiceImpl`提交审核 | projectId | 未传项目名称，未接入摘要提供方 |
| 工期变更审核 | `DurationChangeApplicationService`提交审核 | projectId | 未传项目名称，未接入摘要提供方 |
| 项目进度策略审核 | `ProjectProgressPolicyService`提交审核 | projectId、parentProjectId（父项目） | 未传项目名称，未接入摘要提供方 |
| 项目任务审批 | `PmsNodeApprovalProcessOwner.start` | pmsTaskProjectId | 保留业务表单变量，但未由服务端保证项目名称摘要 |
| 项目阶段审批 | `PmsNodeApprovalProcessOwner.startStage` | pmsStageProjectId | 保留业务表单变量，但未由服务端保证项目名称摘要 |
| 项目门禁审批 | `FlowableProjectStageGateProvider`发起 | pmsGateProjectId | 保留业务表单变量，但未由服务端保证项目名称摘要 |

任务、阶段、门禁的普通表单可通过模型配置偶然显示某个项目名称字段，但当前实现不保证其存在或为服务端权威项目名称，不能视为统一覆盖。

额外边界：`AuthorizationServiceImpl`中仍是“模拟审批流入口”，没有真正发起BPM实例；属于独立功能缺口，不应以补摘要为由实现完整审批。巡检问题待办是领域问题处理，不等同于BPM流程实例。OA请假不属于项目业务，不应改成项目摘要。

## 共性原因和可复用能力

1. `FlowableUtils.getSummary`仅对NORMAL表单解析摘要，对CUSTOM业务页面返回null。只给发起变量加projectName，仍不能修复自定义业务页面的摘要空白。
2. `BpmProjectSummaryService.appendToAuthorizedPage`已提供扩展点，`BpmTaskController`待办、已办分页已接入。当前仅割接模块实现`BpmProjectSummaryProvider`。
3. 该Provider契约只接受BPM已经授权的流程ID集合及当前租户，由业务Owner提供冻结项目名称，不授予额外读取权限。割接查询同时约束租户、逻辑删除、任务关联和授权流程ID，空集合不扩大范围，可直接复用该扩展机制。
4. 我的流程、抄送等读取链路仍直接使用通用摘要；修复时需明确覆盖这些摘要展示，避免待办有名称而其他入口仍空白。
5. 前端待办、已办已经渲染后端summary，根因不是表格缺少摘要列。

## 建议修正范围

- 在上述项目业务发起时，从已授权的真实项目上下文记录项目名称快照，进度策略使用其父项目名称。
- 复用现有项目摘要扩展机制，统一输出“项目名称”；保持非项目流程的原表单摘要规则。
- 不采信前端自行填写的项目名称，不通过跨模块直接查表取名，不从后续审批表单覆盖发起时名称，不批量改写历史流程或批准版本。
- 定向验证：九类发起映射、CUSTOM表单摘要、NORMAL表单兼容、待办与已办及其他摘要入口一致性、租户与授权流程ID边界、空集合、名称快照不被审批更新覆盖。
- 运行验收需在获准部署后，使用真实新发起的项目业务检查摘要；当前尚未执行修复后的构建、测试或浏览器验收。
