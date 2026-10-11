# 割接完成审批创建与验收

2026-10-09，本机后端 `27580`、前端 `27581`。用户要求创建 `pms-cutover-done-approval`，并确认由服务经理审批。流程现已部署，目标模板引用有效定义，独立验收项目已完成“任务内发起 → 服务经理人工审批 → 任务完成 → 重新打开与历史查看”的浏览器闭环。

## 已创建内容

| 项目 | 当前值 |
|---|---|
| 流程标识 | `pms-cutover-done-approval` |
| 流程名称 | 割接完成审批 |
| 模型 ID | `64514bab-c3a7-11f1-8077-00ff0b78abd7` |
| 有效定义 ID | `pms-cutover-done-approval:1:648ddf80-c3a7-11f1-8077-00ff0b78abd7` |
| 版本 / 状态 | v1 / ACTIVE |
| 分类 / 表单 | 项目交付 / 割接完成审批（项目任务），普通表单，无新增业务字段 |
| 流程路径 | 发起割接审批 → 服务经理审批 → 审批结束 |

审批人从本项目有效主责服务经理成员事实解析。流程从任务办理入口发起；服务端固定项目、任务、执行轮次、契约及审批人，完成时复核成员、启用状态和项目访问范围。缺失或不唯一服务经理不能发起，改动表单变量不能指定其他审批人。发起人与服务经理相同也需要人工审批。通过结果沿用原任务审批订阅和状态机完成任务。

该口径来源为 [Q-CUTOVER-APPROVAL-20261009-001](../../decisions/open-questions.md#q-cutover-approval-20261009-001--恢复割接审批流程时的审批人规则)，继续使用既有“审批通过即割接完成”的过渡口径。没有接入或声称已取得外部割接系统事实。

实现复用现有项目成员、范围及任务执行公共 API，并复制既有审批身份冻结方式到独立的 [CutoverTaskApprovalGuard](../../../yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/service/cutover/CutoverTaskApprovalGuard.java)。[BPMN 源文件](../../../yudao-module-bpm/src/main/resources/bpmn/cutover/pms-cutover-done-approval.bpmn)使用现有候选人表达式策略与任务监听器，没有修改旧割接流程。Flowable 接口依据：[RuntimeService](https://www.flowable.com/open-source/docs/all-javadocs/org/flowable/engine/RuntimeService.html)、[Task listeners](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs#task-listeners)。

## 目标模板

`TPL-DELIVERY-FLOW-20261009`（`993009900020`）草稿资源版本 `4 → 5`。本次仅替换 `DEPLOY_CUTOVER.workBinding.parameters.processDefinitionId`；逐字段核对其余内容与版本 4 一致，保留先前工勘、需求分析默认组件适配。

API 预检为 `valid=true, issues=[]`；真实浏览器重新打开目标模板执行 Compiler 预检，页面显示“预检通过”，错误表为空。见[预检截图](browser-precheck.png)。目标模板仍为 `DRAFT`，没有发布，也没有改写现有项目的执行快照。

## 验证结果及边界

- [CutoverTaskApprovalFlowableTest](../../../yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/service/cutover/CutoverTaskApprovalFlowableTest.java)：真实 Flowable/H2 执行 BPMN，共 7 项通过，失败、错误、跳过均为 0。覆盖人工审批正向、独立入口拒绝与回滚、服务经理缺失/不唯一、账号或范围失效、权限版本/任务轮次失效、身份或审批人替换、人员变更后的审批与拒绝保护。先使用旧自选审批人策略复现失败，再使用当前实现通过。
- BPM 模块构建与隔离后端打包通过；重启已获授权的本轮 `27580` 验收后端，使新增守卫生效。`27480/27481` 原有服务、`27581` 前端及基础设施未重启。
- 浏览器验收项目 `PJT2026000012 / 992203060013`，任务 `2108439163390873601`，流程实例 `45b35ad1-c3a8-11f1-8077-00ff0b78abd7`。该隔离项目明确配置用户 `1` 为服务经理；同一用户发起后流程保持[审批中](browser-running.png)，14:12:19 手工通过后任务变为 `DONE / 100%`，审批依据为 `APPROVED / SATISFIED`。
- 重新打开项目任务仍显示[已通过、完成 100%](browser-task-done.png)，[流转历史](browser-history.png)保留服务经理节点、审批时间与审批意见。任务及模板页面采集的浏览器 error 日志为空。
- 验收使用独立模板 `IT_CUTOVER_MANAGER_20261009 / 993009900021` 的单个割接任务；该样本模板创建项目后已停用新项目匹配（API 状态 `RETIRED`）。本次未执行目标模板全部 17 项任务的全流程验收。浏览器正向路径使用同一个发起人和服务经理，其他身份与失败路径由上述 Flowable 测试验证。

结构化结果见 [cutover-approval.json](cutover-approval.json)。原始创建脚本为 `.run/default-chain-20261009/create-cutover-approval.py`，验收结果采集脚本为 `.run/default-chain-20261009/record-cutover-acceptance.py`；原始 API 记录、前后草稿、构建和测试日志位于 `.run/default-chain-20261009/cutover-approval-20261009/`。测试命令：`mvn.cmd -pl yudao-module-bpm -Dtest=CutoverTaskApprovalFlowableTest test`。未执行 Git 提交或推送。
