# 项目交付详情 UI 观察清单

本场景已在真实浏览器完成正常闭环，项目 `PJT2026000001` 为 16/16 项任务完成、S0～S6 均 100%。本清单保留 12 项实际观察，区分展示现象、授权拒绝、环境配置补齐和已验证的后续结果；未因本清单修改 UI 展示或样式。正确详情入口为 `/pms/project-management/project-master-detail?projectId=1`。早期旧入口的 16 项观察不纳入本清单。

隔离环境缺少合同/组织映射及验收填写错误不列为 UI 缺陷。各项按实际证据限定结论；通过增加隔离环境授权恢复流程，不等于修复产品的入口判断或错误提示。

S6 曾因硬件任务关联的安装结果 1 已非当前结果而正确阻断关账。两台设备的安装记录均已完成，随后经原生选择性返工，仅对硬件任务和 S4 建立第二轮执行，关联当前结果 2，旧轮次与安装历史保留，S5 历史未重做；复验通过后完成双节点审批。这是既有历史和当前事实保护，不列为功能缺陷。[返工回执](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s6-hardware-controlled-rework-apply-ui-result.json>)、[新旧关联](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s6-hardware-links-after-rework-readonly.json>)、[最终详情](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/fullcycle-correct-project-final-reopened.png>)。

## 1. S2 · 施工计划审批进度

- **现象：** PM 可见“查看审批进度”入口；打开后审批任务、意见、流程图、审批详情等 4 个 BPM GET 均返回 403，页面提示“没有该操作权限”。
- **影响：** 入口可见，但 PM 无法读取审批进度；服务端拒绝仍生效。
- **复现边界：** 正确项目详情 → 施工计划 → 查看审批进度（PM）。仅记录现有拒绝结果；未扩大 BPM 权限，未验证刷新或重开。
- **证据：** [`browser-acceptance/correct-entry-s2-history-permission-failure.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s2-history-permission-failure.png>)、[`browser-acceptance/correct-entry-s2-history-get-responses.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s2-history-get-responses.json>)

## 2. S3 · 批准方案审批记录

- **现象：** 节点名显示 `serviceManagerReview`，结果显示 `APPROVE`，时间显示毫秒时间戳 `1792264707346`，未转换为业务名称、结果文案或日期时间。
- **影响：** 节点、结果和时间可读性较低。
- **复现边界：** 正确项目详情 → 实施方案 → 审批记录。证据只覆盖当前详情；未记录刷新或重开后的变化。
- **证据：** [`browser-acceptance/correct-entry-s3-approved-history.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s3-approved-history.png>)、[`browser-acceptance/correct-entry-s3-approved-history-visible.txt`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s3-approved-history-visible.txt>)

## 3. S4 · 到货签收/任务树

- **现象：** 到货提交后，局部任务树、交付件与阶段进度存在不同步记录；后续正常重开读取时，到货与硬件任务均显示完成、进度一致。未确定状态差异的时点或原因；服务经理页面仅作只读检查，没有服务经理验收动作。
- **影响：** 提交后的局部状态显示不一致；后续读回一致。
- **复现边界：** 正确项目详情 → 实施部署 → 到货签收/任务树。只记录提交后状态差异及后续读回；不把摘要与不同截图当作同一时刻，不推断因果。
- **证据：** [`browser-acceptance/correct-entry-s4-arrival-task-submit.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-arrival-task-submit.png>)、[`browser-acceptance/correct-entry-s4-arrival-task-submit-visible.txt`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-arrival-task-submit-visible.txt>)、[`browser-acceptance/correct-entry-s4-arrival-task-submit-ui-events.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-arrival-task-submit-ui-events.json>)、[`browser-acceptance/correct-entry-s4-arrival-after-submit.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-arrival-after-submit.png>)、[`browser-acceptance/correct-entry-s4-sm-arrival-review.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-sm-arrival-review.png>)、[`browser-acceptance/correct-entry-s4-sm-arrival-review-visible.txt`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-sm-arrival-review-visible.txt>)、[`browser-acceptance/correct-entry-s4-hardware-reopened.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-hardware-reopened.png>)、[`browser-acceptance/correct-entry-s4-hardware-reopened.txt`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-hardware-reopened.txt>)

## 4. S4 · 阶段任务树

- **现象：** 左侧窄栏中任务名称、任务代码、状态标签和百分比相互挤压，名称/代码被截断或难辨。
- **影响：** S4 任务列表不易辨读。
- **复现边界：** 正确项目详情 → 实施部署 → 左侧阶段任务树。观察仅限左侧任务树；不据此判断主内容字段缺失。
- **证据：** [`browser-acceptance/correct-entry-s4-joint-passed.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-joint-passed.png>)

## 5. S4 · 配置调试新增表单

- **现象：** “调试人员”为普通文本框且未说明需填用户 ID；姓名文本提交到 `debuggerUserId` 返回 400 类型错误，使用有效用户 ID `3` 后提交成功。
- **影响：** 字段身份语义和输入格式不清楚。
- **复现边界：** 正确项目详情 → 实施部署 → 配置调试 → 新增配置调试。记录一次输入错误及有效 ID 成功；未验证重开。
- **证据：** [`browser-acceptance/correct-entry-s4-config-after-save.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-config-after-save.png>)、[`browser-acceptance/correct-entry-s4-config-save-ui-events.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-config-save-ui-events.json>)、[`browser-acceptance/correct-entry-s4-config-save-id3-ui-events.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-config-save-id3-ui-events.json>)

## 6. S4 · 操作区在刷新或返回重进后的显示变化

以下现象分别由各自证据支持，只合并为同类的操作入口显示观察，不推断共同根因。

- **现象：**
  - 配置调试：开始操作后，列表状态已变化但“完成调试”入口未同步出现；正常整页重开后入口可见。
  - 业务联调：正常整页重开后的页面显示“联调通过”操作。
  - 割接 P4：首次进入流程时停留在等级确认表单；返回任务列表后重新办理同一任务，页面显示“开始编制方案”。对应 `GET /cutover-tasks/{id}/plan` 返回 `allowedActions: [CREATE_DRAFT]`，UI 也显示创建入口。
- **影响：** 配置调试/联调的下一步操作或割接方案编制入口在对应初次页面状态中未即时呈现；各自后续重开/返回重进证据中可见。
- **复现边界：** 正确项目详情 → 实施部署 → 配置调试/业务联调/割接上线。配置调试保留重开前后 DOM；联调保留重开后页面；P4 保留初次页面、任务列表和返回重进后的详情/API 读回。只记录可见状态，不推断相互因果。
- **证据：** [`browser-acceptance/correct-entry-s4-config-before-reload.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-config-before-reload.png>)、[`browser-acceptance/correct-entry-s4-config-after-normal-reload.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-config-after-normal-reload.png>)、[`browser-acceptance/correct-entry-s4-config-row-dom-before-reload.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-config-row-dom-before-reload.json>)、[`browser-acceptance/correct-entry-s4-config-row-dom-after-reload.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-config-row-dom-after-reload.json>)、[`browser-acceptance/correct-entry-s4-joint-active-reopened.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-joint-active-reopened.png>)、[`browser-acceptance/correct-entry-s4-joint-active-reopened.txt`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-joint-active-reopened.txt>)、[`browser-acceptance/correct-entry-cutover-p4-initial.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-cutover-p4-initial.png>)、[`browser-acceptance/cut-existing-task-normal-reopen-list.txt`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/cut-existing-task-normal-reopen-list.txt>)、[`browser-acceptance/cut-p4-after-return-reopen.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/cut-p4-after-return-reopen.png>)、[`browser-acceptance/cut-p4-component-plan-get-after-return-reopen.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/cut-p4-component-plan-get-after-return-reopen.json>)、[`browser-acceptance/cut-p4-current-reopen-controls.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/cut-p4-current-reopen-controls.json>)

## 7. S4 · 割接新建/等级问卷

- **现象：** 项目信息中客户名称为空，但问卷显示“三级普通（默认）”；没有真实 CUS 等级来源证据。
- **影响：** 默认展示可能被误认为已配置的客户服务等级。
- **复现边界：** 正确项目详情 → 割接上线 → 新增割接任务 → 查看问卷第五项。只记录当前显示；不推断等级来源或持久化。
- **证据：** [`browser-acceptance/correct-entry-s4-cutover-create-form.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-cutover-create-form.png>)、[`browser-acceptance/correct-entry-s4-cutover-create-form.txt`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-s4-cutover-create-form.txt>)

## 8. S4 · 割接创建错误提示与后续提交

- **现象：** 首次界面提交返回确定业务错误码 `1011005122`，响应说明存在多个有效已发布割接配置；界面没有呈现该业务原因，而显示“创建结果尚待确认”，并提供“重试原请求”。后续保存的创建/评估事件显示 `CUTOVER_CREATED` 与评估保存成功；现有证据不将后续成功归因于“重试原请求”按钮。
- **影响：** 确定业务错误被界面呈现为结果待确认，未直接说明业务错误原因；后续存在成功创建记录。
- **复现边界：** 正确项目详情 → 实施部署 → 割接上线 → 新建任务并提交。只记录该次错误响应、待确认提示及后续成功响应；不把错误后的其他操作归因于原重试。
- **证据：** [`browser-acceptance/correct-entry-cutover-create-ui-result.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-cutover-create-ui-result.json>)、[`browser-acceptance/correct-entry-cutover-config-ambiguity-visible.txt`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-cutover-config-ambiguity-visible.txt>)、[`browser-acceptance/correct-entry-cutover-config-ambiguity.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-cutover-config-ambiguity.png>)、[`browser-acceptance/correct-entry-cutover-create-and-assessment-actual-success.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/correct-entry-cutover-create-and-assessment-actual-success.json>)

## 9. S4 · 割接 P5 发起人审批权限

- **现象：** 当前登录人为发起人 PM3，原权限快照及源/目标权限对照显示该账号没有 `pms:cutover-task:approve` 权限；审批组件真实 GET 却返回 `allowedActions: [APPROVE, REJECT]`，页面可见审批操作。原提交被服务端以 HTTP 403（`APPROVAL_PERMISSION_OR_PROJECT_SCOPE_DENIED`）拒绝。用户随后批准仅在隔离环境为 approve 角色配置权限；已有后续证据显示 PM 发起人批准和 SM 原生审批均成功。
- **影响：** 原观察记录了授权前 UI 暴露当前账号不能完成的入口，服务端正确拒绝无权请求。后续隔离授权后审批可继续；这不表示代码已修复，也不抹除原始拒绝现象。
- **复现边界：** 正确项目详情 → 实施部署 → 割接上线 → P5 审批。授权前后证据分别记录；后续权限调整仅限用户批准的隔离环境，不外推为正式角色基线。
- **证据：** [`browser-acceptance/cut-initiator-403-permission-source-target-readonly.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/cut-initiator-403-permission-source-target-readonly.json>)、[`browser-acceptance/cut-initiator-approval-current-component-get.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/cut-initiator-approval-current-component-get.json>)、[`browser-acceptance/cut-d-initiator-approve-ui-result.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/cut-d-initiator-approve-ui-result.json>)、[`browser-acceptance/cut-d-initiator-approval-denied.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/cut-d-initiator-approval-denied.png>)、[`browser-acceptance/cut-d-initiator-authorized-approve-ui-result.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/cut-d-initiator-authorized-approve-ui-result.json>)、[`browser-acceptance/cut-d-initiator-authorized-approved.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/cut-d-initiator-authorized-approved.png>)、[`browser-acceptance/cut-d-sm-approve-ui-result.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/cut-d-sm-approve-ui-result.json>)

## 10. S5 · 培训表单依赖查询失败

- **现象：** PM3 首次打开培训打印/模板组件时，用户分页查询与动态表单模板选择查询均被拒绝（响应业务码 403）；组件记录页面错误并崩溃。源权限映射快照中 PROJECT_MANAGER 与 SERVICE_MANAGER 均未映射 `system:user:query` 或 `pms:dynamic-form-instance:query`。用户批准仅在隔离环境为 PM 加授菜单 `1001` 与 `198803`，保持 Self 数据范围不变；授权后 PM/SM 正常登录 UI，用户与模板选择读取返回 code 0，组件不再崩溃。培训记录随后经真实 UI 保存、重开、生成本地确认链接并完成客户评价及模拟签字，提交 code 0；后续 S5 培训和满意度均已客户确认，项目进入 S6 时为 15/16。培训打印模板所需源模板类别为 `TRAINING_PRINT_FORM`，隔离目标正式配置此前只同步旧 `TRAINING_PRINT`；后续按源当前已批准修订（current revision 2）完成配置同步。这是环境配置同步，不是页面类别代码缺陷或产品代码修复。
- **影响：** 授权前组件未完成装载；隔离环境按批准补齐查询授权与模板基础配置后，已有 UI 证据表明培训流程完成并读回。原 403 证据仍保留；权限和配置结果不外推为正式角色基线或产品修复结论。
- **复现边界：** 正确项目详情 → S5 现场培训/打印模板组件。分别记录授权/同步前失败和隔离环境配置后的 UI 结果。业务确认使用隔离模拟签字；报告不包含客户公开链接或令牌。
- **证据：** [`source-role-permissions-original.json`](<../../../.run/build/source-v4-fullcycle-20261018/source-role-permissions-original.json>)、[`browser-acceptance/s5-training-print-template-failed-reads-and-errors.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-training-print-template-failed-reads-and-errors.json>)、[`browser-acceptance/s5-training-print-template-repro-crash.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-training-print-template-repro-crash.png>)、[`browser-acceptance/training-two-query-authorized-grant-result.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/training-two-query-authorized-grant-result.json>)、[`browser-acceptance/training-query-two-permissions-pending-plan.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/training-query-two-permissions-pending-plan.json>)、[`browser-acceptance/training-current-print-official-sync-results.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/training-current-print-official-sync-results.json>)、[`browser-acceptance/training-source-target-current-print-mapping.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/training-source-target-current-print-mapping.json>)、[`browser-acceptance/training-print-selection-current-response.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/training-print-selection-current-response.json>)、[`browser-acceptance/s5-training-customer-confirm-safe-result.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-training-customer-confirm-safe-result.json>)、[`browser-acceptance/s5-training-owner-confirmed-reopen.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-training-owner-confirmed-reopen.png>)、[`browser-acceptance/s5-training-customer-confirmed.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-training-customer-confirmed.png>)

## 11. S5 · 满意度调查发起权限与界面错误

- **现象：** PM 在已保存的完工证明 1 上可见“发起调查”入口；点击后界面显示“系统异常”（HTTP 200、业务码 500）。服务端记录 `NativeAcceptanceDeliveryAccess.requireCommand` 因权限/项目范围/业务状态拒绝。只读权限证据显示 PM 无 `pms:acc-completion-certificate:audit`，SM 已有菜单 19103 对应权限。PM 失败后证书仍为 status 0/version 0，未创建调查任务或访问授权，满意度 bundle/signing 数量均为 0。随后 SM2 使用既有权限对同一证明原生发起成功（code 0）；未新增权限。后续客户完成与完工证明联合签署的满意度评价，读回为 80 分、达标，并提交为有效满意度交付件，S5 已完成。
- **影响：** PM 可见的入口提交后只呈现通用系统异常，未向用户解释授权拒绝；该次服务端拒绝并回滚，SM 既有授权路径可完成发起。后续客户签署和满意度交付闭环已成功。该观察不表示 PM 权限配置已修复，也不推断 PM 应获得 audit 权限。
- **复现边界：** 正确项目详情 → S5 验收交维 → 已保存完工证明 → 发起调查。PM 拒绝和 SM 后续成功分别有证据；仅记界面入口/提示、服务端拒绝及只读回滚事实。公开确认链接与凭据不纳入报告。
- **证据：** [`browser-acceptance/s5-satisfaction-initial.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-satisfaction-initial.png>)、[`browser-acceptance/s5-satisfaction-issue-safe-result.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-satisfaction-issue-safe-result.json>)、[`browser-acceptance/s5-satisfaction-after-issue.png`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-satisfaction-after-issue.png>)、[`browser-acceptance/s5-satisfaction-issue-500-safe-stack.txt`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-satisfaction-issue-500-safe-stack.txt>)、[`browser-acceptance/s5-pm-issue-rollback-sm-existing-authorization-readonly.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-pm-issue-rollback-sm-existing-authorization-readonly.json>)、[`browser-acceptance/s5-pm-denied-bundle-rollback-counts.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-pm-denied-bundle-rollback-counts.json>)、[`browser-acceptance/s5-sm-satisfaction-issue-safe-result.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-sm-satisfaction-issue-safe-result.json>)、[`browser-acceptance/s5-sm-issue-link-open-safe.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-sm-issue-link-open-safe.json>)、[`browser-acceptance/s5-satisfaction-owner-confirmed-detail.txt`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-satisfaction-owner-confirmed-detail.txt>)、[`browser-acceptance/s5-satisfaction-business-result-submit-ui-result.json`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-satisfaction-business-result-submit-ui-result.json>)、[`browser-acceptance/s5-satisfaction-accepted-deliverable-history.txt`](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s5-satisfaction-accepted-deliverable-history.txt>)

## 12. S6 · 正常闭环后仍保留提交审批指引

- **现象：** 项目已显示“正常闭环”“最近闭环申请：已批准”，绿色提示明确“无需再次校验或提交”；上方任务办理文案仍提示“请在下方校验条件并提交审批”。
- **影响：** 已完成状态与办理指引不一致，容易让用户误以为仍需操作。
- **复现边界：** PM 在双节点审核结束后，正常重开指定项目详情并选择 S6 项目闭环；仅记录文案，不改页面行为。
- **证据：** [最终详情截图](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/fullcycle-correct-project-final-reopened.png>)、[页面文本](<../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/fullcycle-correct-project-final-reopened.txt>)。
