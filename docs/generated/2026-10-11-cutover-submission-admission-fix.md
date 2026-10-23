# 项目割接审批提交入口准入修复

需求方反馈割接流程不能提交，核对项目992203060198。真实浏览器复现：割接上线显示提交审批，但点击后返回“项目任务命令不符合当前规则”。运行日志定位ProjectTaskLifecycleService的START准入校验。当前有效计划的DEPLOY_CUTOVER_ENTER要求TASK DEPLOY_JOINT完成；业务联调当前IN_PROGRESS、未提交，割接执行轮次PENDING。拒绝符合现有规则，缺陷在页面提供了不可办理的提交入口且没有展示具体前置条件。需求方确认按此边界修复。

## 修改

审批工作台在原本允许首次START时返回startAdmission，复用服务端当前生效计划的冻结准入规则与三值求值。未满足或未知时移除START，其他操作保持。预览先锁项目，再读取阶段、规则和事实，与命令保持项目优先的锁顺序；不推进任务、轮次或写规则历史。范围受限的任务可见者只获得总体准入结果，不获得其他任务名称。实际提交的版本、权限、状态机及准入复核继续保留。

TaskApprovalPanel显示未满足的条件结果，未满足或未知时不渲染首次提交表单；即便旧START操作残留也不能调用提交。已启动轮次的APPROVAL驳回重提保持原契约。未修改模板、项目计划、审批人、业务联调状态或历史证据。API字段登记在docs/design/10-api-design.md。

## 验证

- 后端沿用scripts/worktree-toolchain.ps1、固定JDK25/Maven及工作树独占锁：ProjectStageAdmissionServiceTest 14项、ProjectTaskQueryServiceTest 18项、ProjectTaskLifecycleServiceTest 44项，合计76项通过。涵盖冻结规则预览与命令一致、项目优先加锁、未知与未满足移除START、其他操作与重提保留、受限范围不泄露任务名，以及现有授权、状态机和联系人自动完成回归。日志.run/cutover-admission-tests.log。
- 前端现有vitest.pms-file.config.ts运行新taskApprovalAdmission.runtime.spec.ts与原stageGateProcessPanel.runtime.spec.ts，合计10项通过。验证禁止提交时不调用API、条件刷新后恢复表单、已启动轮次驳回可重提。
- 复现后的正式API回读：业务联调任务2107838747850678283为IN_PROGRESS/version4；割接任务2107838747850678284为PENDING_ASSIGN/version3、审批NOT_STARTED。未因失败提交创建审批或改变状态。回执.run/cutover-submission-workbench-before.json。

## 部署边界

scripts/worktree-build.ps1已构建成功并冻结后端产物：.run/build/releases/f24aae1c-a76b-466c-beb8-e774460126e1/manifest.json。构建期间源码清单一致，22项既有已部署修复基线保持；日志.run/cutover-admission-backend-build.log。scripts/worktree-backend.ps1以该清单执行ValidateOnly通过：冻结产物与local,dac配置校验通过，未改变运行服务。

用户明确确认本次重启后，通过scripts/worktree-backend.ps1以该冻结清单执行ApprovedRestart，59191已健康运行，local,dac、PID36984。22项已部署修复基线校验保持。DAC48182未启停，随后独立就绪检查通过；未验证解析业务。

部署后的正式API回读显示startAdmission.outcome=NOT_MATCHED，conditions为业务联调/NOT_MATCHED，allowedActions仅CANCEL、UPDATE、ASSIGN，不再提供START。任务完整对象和审批完整对象与复现后保存的before回执一致：PENDING_ASSIGN/version3、审批NOT_STARTED，未创建审批或推进任务。回执.run/cutover-submission-workbench-after.json。

真实浏览器以现有管理员会话打开localhost:19191项目992203060198的割接上线任务，显示“当前任务准入条件未满足，暂不能提交审批：业务联调（未满足）”，不显示提交审批表单或提交按钮，审批状态保持未发起。当前业务联调仍未完成，故未代用户推进该业务、发起真实割接审批或代服务经理作决定；条件满足后恢复入口与驳回重提由上述定向测试验证。未提交或推送Git，保留其他在途改动。
