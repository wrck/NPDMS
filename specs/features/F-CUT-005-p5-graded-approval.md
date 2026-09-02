# F-CUT-005 P5分级审批 Feature Spec

> 文档状态：`BASELINE`
> Feature Ready：`READY / GO @ 2e3fdba3`
> Requirement：`CUT-05（V1/P0）`
> Requirement切片覆盖：`CUT-05@V1=FULL`
> Owner Context：`CUT（变更切换与稳定治理）`
> 前置Feature：`F-CUT-002`、`F-CUT-003（A/B/C）`、`F-CUT-004`
> 后置Feature：`F-CUT-006（CUT-06跟踪与闭环）`
> 支撑依赖：`PROJ当前服务经理事实`、`SYSTEM割接二线/研发审批角色候选事实`、`ProjectScopeApi`、`NotifyMessageSendApi`
> 机器合同：`specs/features/F-CUT-005-api-contract.json`、`specs/features/F-CUT-005-physical-contract.json`、`specs/features/F-CUT-005-approval-owner-contract.json`、`specs/features/F-CUT-005-candidate-owner-contract.json`
> 旧实现复用审计：`specs/features/F-CUT-005-legacy-reuse-audit.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-09-01-f-cut-005-p5-graded-approval.md（PASS / GO @ 912d0cdb）`

## 1. 业务目标

割接方案提交后，平台按冻结的人工等级创建串行P5审批实例。发起人、服务经理、二线和研发按A/B/C/D路由逐节点完成五项合理性评审；服务经理同时复核P2问卷与人工等级。任一节点驳回返回P4并保留不可变历史，全部节点通过锁定审批事实并把任务推进P6。V1使用平台待办和站内通知，不建设V2提前时间判断或外部通知。

## 2. Scope

### 2.1 包含

- A=`发起人→服务经理→二线→研发`、B=`发起人→服务经理→二线`、C/D=`发起人→服务经理`四条冻结串行路由；
- 审批实例、节点、五项评审、服务经理P2复核、反馈意见、改派、待办、站内通知及全历史；
- P4提交事务内创建审批，来源失效暂停，驳回P5→P4，全部通过P5→P6及`CutoverApproved`；
- 审批详情、本人待办、管理员改派候选队列、通过、驳回、未处理节点改派及服务端`allowedActions`；
- 幂等、版本CAS、并发单胜、授权失效暂停、通知失败重试与审计；
- PROJ/SYSTEM候选事实消费合同及`src/test`受控替身正向闭环。

### 2.2 不包含

- CUT-04方案正文修改、CUT-06闭环记录或P6执行；
- CUT-05@V2 A/B提前时间判断、INT-10短信/邮件、INT-05钉钉或其他IM；
- 通用审批引擎改造、Yudao基础模块修改、生产Fake/fallback；
- `Q-FCUT004-001`所述P6职责变化回退；
- 旧`pms_cut_task/pms_cut_plan`审批字段迁入新审批事实。

## 3. 业务规则

### BR-FCUT005-001 路由与来源冻结

- `CutoverApprovalFactApi.start`以`MANDATORY`加入F-CUT-004提交事务；受信当前用户须在同事务锁定同项目`ProjectScopeApi.ACTION_EDIT`，并把`userId/projectId/ACTION_EDIT/treeVersion`冻结为`INITIATOR`机器事实。审批实例固定引用同租户任务、已提交方案revision、最终评估、A/B/C有效清单、人工等级和P4来源快照版本。
- 节点固定为：A四节点、B三节点、C/D两节点；首节点`INITIATOR`创建为`PENDING`，后续节点为`WAITING`。P4提交不替代发起人节点评审。
- 服务经理由PROJ当前唯一主责服务经理事实解析；二线/研发先由SYSTEM返回完整、稳定排序的启用角色成员集合，再由CUT逐人读取同项目`ProjectScopeApi.ACTION_VIEW`，只在交集恰有一人时冻结该审批人。SYSTEM全局候选多人但项目交集唯一时必须成功；项目交集为零或多人时实例仍以`PENDING`进入P5但写入`holdReason`，整条实例暂停且不产生可执行待办。候选快照保存完整SYSTEM成员身份/版本、逐人项目范围结果/treeVersion及交集结论；处理前按用户ID稳定锁定完整角色成员事实和项目范围事实并重算交集。Owner事实修复或管理员把所有未决节点改派为合格候选后恢复当前节点。Provider不可用或Owner事实损坏不是合法候选结果，`start`整体失败并使P4提交回滚；不得在项目范围过滤前任选一人、读取跨模块表或用全局角色单独替代项目范围。
- 路由、候选、来源和审批页面引用内容在实例创建时冻结。`ApprovalSourceSnapshot`的精确键、类型、可空性和排序见API机器合同：项目为F-CUT-002原样十一字段；采集分析只含割接类型、原样可空的组网方式、计划操作时间，合法`networkMode=null`不得补默认值或推导值；风险和业务问卷分别冻结CUT-03当前已提交结果并按`stableItemKey`排序；P2冻结四项答案、客户服务等级和人工等级；方案冻结F-CUT-004完整`PlanSourceSnapshot + PlanContentUnion`。审批详情只能读取该不可变快照，不得刷新当前PROJ、CUT-03或CUT-04事实。来源版本变化不得静默刷新；F-CUT-004来源失效命令把实例置`PAUSED_SOURCE_INVALIDATED`，旧实例和节点不恢复。

### BR-FCUT005-002 评审与服务经理复核

- 每个处理节点必须提交五项封闭评审：`PREPARATION/BUSINESS_TEST/EXECUTION/ROLLBACK/OTHER`。每项只能`YES/NO`；`NO`必须有1..1000字符原因，`YES`原因必须为空。
- 通过和驳回使用两个精确请求判别支：`APPROVE`要求五项全部`YES`且服务经理复核为`CONFIRMED`；`REJECT`要求至少一项`NO`，或仅服务经理可在五项全`YES`时以`NOT_REASONABLE`驳回。动作与评审结果不一致稳定返回`DECISION_ACTION_RESULT_MISMATCH`，不得由Controller或状态机猜测。两者均须填写1..1000字符反馈意见。
- `SERVICE_MANAGER`节点必须额外提交`CONFIRMED/NOT_REASONABLE`的P2问卷与人工等级复核结果；`NOT_REASONABLE`必须驳回并填写复核原因，其他节点不得提交该字段。服务经理不修改P2答案或等级。
- 当前节点只允许冻结的当前审批人且仍具备节点Owner资格和项目范围者处理。原始或改派后的`INITIATOR`均须锁定重验同项目`ACTION_EDIT`和冻结treeVersion；其他节点按候选机器合同锁定重验。Owner明确证明候选/范围失效时保持实例`PENDING`并写`APPROVER_UNAVAILABLE`暂停原因，业务决定零写，等待改派；Provider不可用只返回503并保持原实例不变，不得伪装为候选失效或自动换人。

### BR-FCUT005-003 串行推进、驳回和全部通过

- 通过非末节点：当前节点追加不可变决定，下一节点`WAITING→PENDING`，实例版本只增一次并创建下一待办/通知；未来节点不能越级处理。
- 任一节点驳回：当前节点`REJECTED`，未处理节点`CANCELLED`，实例`REJECTED`；同一事务把任务`P5/APPROVING→P4/PLAN_DRAFTING`并追加`P5_APPROVAL_REJECTED`历史。F-CUT-004随后从被驳回方案派生新DRAFT；旧实例不可恢复或覆盖。
- 末节点通过：节点与实例置`APPROVED`，保存决定时间；同一事务把任务`P5/APPROVING→P6/CLOSURE_IN_PROGRESS`并追加`P5_APPROVAL_APPROVED`历史，同时写`CutoverApproved` Outbox。不得修改CUT-04方案正文。
- 任务CAS、审批根CAS、节点CAS、平台幂等、审计与Outbox同事务；任一步失败整体回滚。相同`Idempotency-Key`同摘要返回原结果，异摘要冲突，处理中返回可重试冲突。

### BR-FCUT005-004 改派、待办与通知

- 具备`reassign-approval`权限的审批管理员只能改派`WAITING/PENDING`节点。`INITIATOR`替代处理人须具备同项目`ACTION_EDIT`，节点身份与原发起人历史不变；`SERVICE_MANAGER`目标必须等于PROJ当前唯一主责服务经理；`SECOND_LINE/RND`目标必须通过SYSTEM显式角色成员锁定和项目`ACTION_VIEW`。改派追加原审批人、新审批人、原因、操作者、时间和候选事实版本，不覆盖历史。
- 节点表是CUT待办的唯一业务事实；本人待办只返回根`PENDING`、无hold、节点`PENDING`且`currentApproverUserId`为受信当前用户，并通过同一当前Owner/项目范围重验的节点。Provider不可用使整页失败，不得返回部分待办。前端按钮和站内信不产生审批资格。
- 审批详情/动作矩阵固定为：原始发起人且当前有`ACTION_VIEW`可看完整进度但不因此获得审批权；当前审批人通过节点Owner/范围重验后可看完整冻结审批页并在节点可执行时获得通过/驳回；其他只读项目成员仅在终态查看不含来源快照和节点评审正文的最终结果；审批管理员通过独立改派候选队列和`REASSIGNMENT_ONLY`详情只读取实例、任务、未处理节点、当前指派及版本等改派必需元数据，不读取冻结业务快照或评审正文，改派响应仍为该投影。审批管理员只有未处理节点改派权。`allowedActions`必须与命令的权限、主体事实、项目范围、根/节点状态同构。
- 每次首节点创建、下一节点激活或改派成功只在业务事务内追加唯一`PENDING`通知记录并返回成功；事务提交后的独立投递动作才调用现有`NotifyMessageSendApi`。发送失败只把通知改为`PENDING_RETRY`并按同一deliveryKey重试，不回滚或改写审批动作；通知Provider失败不属于通过/驳回/改派错误合同，通知成功也不等于节点已处理。

## 4. API与Owner合同

- 用户REST及精确wire/error/幂等见`F-CUT-005-api-contract.json`：审批详情、本人待办、管理员改派候选队列、通过、驳回、改派六类操作。
- F-CUT-004/F-CUT-006消费的`start/inspect/lockAndRevalidate/pauseForSourceInvalidation`保持`F-CUT-005-approval-owner-contract.json`的既有签名；本Feature实现唯一生产Provider。
- 候选解析见`F-CUT-005-candidate-owner-contract.json`。当前只授权CUT消费端口与`src/test`受控实现；PROJ/SYSTEM物理Owner Provider未形成时不注册生产完整Service/Controller，不得使用旧项目团队表、SYSTEM直表或空候选fallback。SYSTEM接口返回完整候选集而不是预先挑选单人，唯一性由CUT在项目范围交集后判断。
- PROJ候选合同作为本Feature内的物理Owner支撑Task交付，不新建纯Provider Feature或新表；PROJ实现可复用现有ProjectParticipant聚合与锁序，但必须提供`FOUND/NOT_UNIQUE/STALE`稳定结果，不能把既有混合异常直接泄漏给CUT。
- SYSTEM候选合同同样只登记为物理Owner支撑Task；当前不授权修改Yudao基础模块。正式Provider形成前，CUT内核通过测试作用域受控事实跑正向链，生产装配保持失败关闭。

## 5. 数据与迁移

- 新平台新增`cut_approval_instance`、`cut_approval_node`、`cut_approval_review_item`、`cut_approval_reassignment`、`cut_approval_notification`五张CUT Owner表；精确列、约束、锁序和状态见physical contract。
- `pms_cut_task.approval_opinion`与`pms_cut_plan.approved_by/approved_time/approval_opinion/status`只保留旧事实，不迁移、不双写、不生成新审批实例或`CutoverApproved`。
- Flyway版本只在实际串行合入时确定；Feature Ready不预约迁移号。菜单、权限、站内信模板和重试Job在Technical Plan中串行落种，默认不授权角色。

## 6. 验收标准

- AC-FCUT005-001：A/B/C/D提交分别冻结4/3/2/2节点，首节点为发起人，候选与来源快照不可覆盖。
- AC-FCUT005-002：五项评审、服务经理P2复核、反馈意见和否项原因按判别规则失败关闭。
- AC-FCUT005-003：中间通过只激活下一节点；任一驳回原子返回P4；全通过原子进入P6并仅产生一个`CutoverApproved`。
- AC-FCUT005-004：非当前人、越级、项目越权、候选失效、陈旧版本、同键异载荷和并发请求均零业务副作用；改派保留完整历史。
- AC-FCUT005-005：站内通知失败不回滚审批，待办仍可查询并可按同deliveryKey重试。
- AC-FCUT005-006：CUT单元/集成可用受控PROJ/SYSTEM替身跑完整A/B/C/D正向与驳回链；生产Owner未接通前不得声明真实浏览器或Implementation Done。

## 7. Feature Ready Gate

当前：`BASELINE / READY / GO / NOT_STARTED`。Feature Ready独立复审已在锁定提交`2e3fdba3`通过；允许生成唯一Technical Plan并送独立复审，Technical Plan通过前不得实施。
