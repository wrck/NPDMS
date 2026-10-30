# 59299 工期调整审批真实业务闭环验收

执行日期：2026-10-19。环境：后端59299、前端18199，数据库 `npdms_fullcycle_v4_3d5c63bd453d`。用户授权在真实浏览器验证发起、服务经理审批及结果回写。所有业务写入均通过页面操作，未直接改表、未扩大权限、未重置密码或重启服务。

## 结论

发起、服务经理审批和工期生效主链通过；同时发现审批意见未回写工期变更业务字段的缺口，不能将全部回写字段标记为通过。意见完整保留在BPM流转历史，不是意见丢失。

## 实际业务记录

- 项目：`PJT2026000003` / ID `3`（已有隔离验收项目）。项目经理用户 `3`，唯一有效主责服务经理用户 `2`，双方不同且归属经过核对。
- 工期计划：`2`；变更：`2112071256776056834`；候选修订：`2112071256776056835`。
- 流程实例：`039a7151-cb88-11f1-baf3-00ff0b78abd7`，使用补齐布局的 `pms-sol-duration-change:2:452e1f00-cb87-11f1-baf3-00ff0b78abd7`。
- 原工期V1：2026-10-19至2026-11-18，31天。新工期V2：2026-10-19至2026-11-19，32天。原因明确标注真实浏览器验收，分类为OTHER。

## 已通过的实证

1. 项目经理保存唯一变更草稿并提交成功。提交后的真实页面和数据库均保持V1/31天生效，候选V2/32天为`PENDING_APPROVAL`，pending指向本次变更，BPM待办分配给用户2。
2. 服务经理从原生待办打开流程，填写意见并通过。正式审批接口返回成功，BPM服务经理任务完成，流程实例结束，无剩余待办。
3. 项目经理重新查询，页面显示V2/32天，变更历史显示已通过，旧V1仍可见。业务状态为`APPROVED`，current指针切换至候选修订，pending清空；审批人、审批时间正确。
4. 页面和数据库均显示`PENDING_RECALCULATION`，其来源为本次新工期；该状态符合F-SOL-001，审批通过不自动覆盖阶段施工计划。
5. 原有两条工期修订、其他项目的工期计划及本项目成员归属与验收前完全一致。

## 审批意见回写观察

服务经理实际提交意见：“真实浏览器验收：同意工期延长1天，已核对10月19日至11月19日共32天，审批后核验工期回写。”BPM已通过详情和流转记录均显示完整意见，但 `sol_construction_plan_change.approval_opinion` 为NULL。项目工期历史当前也没有审批意见列。

只读定位：`BpmTaskServiceImpl.updateTaskStatusAndReason`将意见保存为任务局部变量；`BpmProcessInstanceServiceImpl.processProcessInstanceCompleted`发布终态事件时读取流程级结束原因；`DurationChangeBpmListener`把该事件reason传给`DurationChangeBpmResultService`并写入业务意见字段。本次同意流程的流程级reason为空，导致业务字段为空。现有Feature明确同意必须切换当前版本，明确驳回必须有意见；没有据此擅自更改平台事件语义或覆盖历史记录。本轮记录该回写差异，未实施代码修复或历史补写。

## 证据

目录：`.run/build/duration-business-cycle-20261019/`。

- `preflight.json`、`submitted-business-snapshot.json`、`final-business-verification.json`：修改前、审批中、审批后业务事实。最终核对中`approvalOpinionPresent=false`如实保留，其余检查通过。
- `pm-create-draft-ui-result.json`、`pm-submit-ui-result.json`、`sm-duration-approve-ui-result.json`：真实页面产生的正式业务请求与响应。
- `pm-submitted-still-effective31.png`：审批中仍生效旧工期。
- `pm-approved-current32.png`：审批后新工期生效及待重算提示。
- `pm-history-revisions.png`、`pm-history-approved-change.png`：旧版和已通过变更。
- `sm-completed-flow-history.png`、`sm-approved-opinion-visible.png`：BPM已通过、审批人及完整意见。
- `browser-cycle-verdict.json`：浏览器验收结论，保留业务意见字段回写缺口。

验证范围为本次同意路径及旧版本保护，不将该结果扩写为驳回、撤回或整个Feature的完整验收。

## 后续修复复验

2026-10-19经授权修复并部署59299后，新变更的完整浏览器闭环及审批意见回写已通过，详见[修复验收报告](2026-10-19-duration-opinion-fix.md)。本报告所述旧变更及其NULL审批意见保持原样，未回填或覆盖历史证据。
