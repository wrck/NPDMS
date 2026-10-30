# 84572剩余业务视图快照修复与需求分析创建验收

2026-10-24。需求方反馈需求分析“操作未完成，请按错误提示检查填写内容和附件后重试”，随后明确批准“一起修复全部10处”。

## 原因与修复

需求分析创建命令在 `ProjectBusinessExecutionService.requireWritableView` 被拒绝。项目仍沿用原模板v1的冻结绑定；此前只修复工勘一处。该命令尚未进入附件或表单内容校验，通用提示会误导用户。

完整预检识别10个未完成节点仍缺快照：需求分析、施工计划、实施方案、到货签收、硬件实施、割接上线、现场培训、满意度调查、初验和终验。每处均按原声明的精确视图ID核对已发布状态、Owner、实体及组件，不更换业务目标或规则。已完成的人员指派虽仍持有原冻结内容，但不补写；已完成工勘及用户信息历史同样保持。

修复按两个正式事务执行：

- 草稿4仅更新已开始的需求分析、施工计划两处快照。预览只报告这两处STARTED_BINDING_IMMUTABLE，无交付件/里程碑/门禁定义或规则变化；原cancel动作结束两条当前任务，再通过rework-apply建立各自第2轮并生效计划4（计划v3）。
- 草稿5只更新其余8个PENDING节点快照，预览无问题，通过原计划apply生效为计划5（计划v4），不对这8个节点执行返工。

所有其他业务定义均保持。关联REEVALUATE沿既有依赖规则运行，不强制完成任务、推进阶段或创建下游业务记录。本次未执行新迁移、未重启后端、未提交或推送。

## 错误提示修复

共享实体命令请求原本设置silentError，但未设置preserveBusinessError。Axios因此将服务端具体错误替换为字符串`error`，需求分析页面只能显示填写/附件通用提示。现为该命令请求启用既有preserveBusinessError能力，保留服务端错误与原拒绝结果，不改授权、重试或业务完成规则。

真实浏览器在配置修复前执行同一失败请求，页面已显示“操作未完成：项目任务命令不符合当前规则”，没有继续误报填写或附件。配置修复后加载计划v4，admin点击创建需求分析草稿成功，页面展示当前草稿V1、11项动态表单及附件区域。数据库记录实体2113863647666507777、修订2113863647666507778，revision_state=DRAFT、creator=1。未填写业务正文、未上传附件、未完成或冻结该草稿；不将创建成功视为需求分析业务完成。

## 验证与限制

- `verify-remaining-view-repair.py` 对照通过：新计划设计与计划3只差批准的10处快照；全部原已结束轮次逐字段一致；旧计划designer、execution_snapshot、来源和创建证据不变；既有工勘、需求分析与施工计划业务记录在计划修复过程中不变；仅两条任务轮次增至2，其他节点轮次和状态不变。
- EntityPanel.runtime.spec.ts与operationHost.runtime.spec.ts合计21项通过。
- 扩大运行operationAdapters.runtime.spec.ts时，16项中9项通过、7项工勘适配用例失败：旧测试仍使用原工勘路由/回执替身，mock请求没有返回当前receipt，独立路径mock缺service.defaults.transformResponse。该轮总计37项中30项通过、7项失败；未修改这些测试或宣称全套通过。需求分析与错误提示的真实浏览器结果独立成立。
- 其他8个未开始节点仅完成绑定来源、编译和计划生效验证，尚未逐一执行其业务创建、审批或完成流程。

证据目录：`.run/shared-query-audit-20261024/`，包括remaining-view-repair-proposal.json、remaining-active/pending-prepared.json、remaining-active/pending-applied.json、remaining-before/after-repair.json、remaining-preservation-result.json、browser-requirement-actual-error.txt与browser-requirement-created.txt。
