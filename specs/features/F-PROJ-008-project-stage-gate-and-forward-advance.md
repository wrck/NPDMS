# F-PROJ-008 项目阶段准出门禁与正向推进 Feature Spec

> 文档状态：`BASELINE`
> Feature Ready：`READY / GO（744c70a0独立整改复审通过）`
> Implementation：`NOT_STARTED`
> Requirement：`PM-03（V1阶段门禁正向运行切片）`
> Requirement切片覆盖：`PM-03@V1=PARTIAL`
> 关联Requirement：`PM-01`、`PM-04`、`PM-11`；不宣称关联Requirement完成
> Owner Context：`PROJ（项目治理）`
> 前置Feature：`F-PROJ-001`、`F-PROJ-003`、`F-PROJ-007`
> 上位决策：`ADR-0043（ACCEPTED）`
> 适用基线：PRD V1.8；F-PROJ-008 SDS Phase 2 / P3-E09 `READY / GO`
> Technical Plan：`PENDING_FORMATION`；`Q-FPROJ-008`已按需求方决定关闭，允许形成唯一计划，计划GO前不得进入Implementation

## 1. 业务价值与最小正向闭环

项目经理在项目工作区查看当前阶段冻结的准出条件，完成或启动所需任务、里程碑、交付件和审批流程；系统实时按Owner事实给出有序未满足项。全部EXIT Gate满足后，项目经理一次提交即可把活动项目从S0～S3推进到相邻S1～S4，并形成不可变阶段快照、审计和事件。

```text
查看当前阶段准出条件
→ 完成已有Owner业务 / 启动冻结版本Gate流程
→ 重新读取准出结果
→ 提交相邻阶段推进
→ 原子更新Stage与Project并形成Snapshot/Outbox
→ 项目工作区展示新阶段和历史
```

本Feature是PM-03项目实例运行切片。模板创建、四维匹配和实例化已由F-PROJ-001承接；本Feature不重做模板系统。

## 2. Scope

### 2.1 包含

- S0→S1、S1→S2、S2→S3、S3→S4四条通用相邻推进；
- 当前阶段EXIT Gate及Reference readiness查询；
- TASK、MILESTONE、DELIVERABLE、STATE、APPROVAL、PROCESS六类Owner事实评估；
- PMS专用Gate流程按冻结`definitionKey + definitionVersion`启动、审批结果重验和幂等重放；
- 模板发布时校验S0～S3 EXIT Gate非空、Reference完整、Provider存在及专用流程定义可用；
- `pms:project:update + PROJECT_MANAGE + 当前PROJECT_MANAGER`服务端授权；
- Project/Stage/Gate/Reference/Owner事实稳定锁序、版本冲突和失败关闭；
- 当前Stage `DONE`、下一Stage `ACTIVE`、Project.current_stage、Gate结果、StageSnapshot、审计、Outbox和幂等完成点同事务；
- 项目工作区当前阶段、准出条件、启动审批、推进按钮及阶段历史正向交互。

### 2.2 不包含

- S4→S5；继续使用F-COM-001 `enter-acceptance-stage`及ACC范围绑定事务；
- S5→S6、正常闭环、阶段回退、异常关闭和重开；
- 改写Yudao `BpmProcessInstanceApi`、BPM内部Service/Mapper/DO或通用流程发起规则；
- 新权限键、新门禁结果表、新阶段历史表、Flyway或存量历史推断；
- 自动替用户完成任务、交付件、里程碑或审批；
- 第三方审批平台、通知送达、SIT、UAT、Deployment和Release。

## 3. 业务规则

### BR-FPROJ008-001 冻结事实与空Gate

- 只使用项目创建时冻结的Stage、EXIT Gate、Gate Reference及其稳定对象键；模板当前版本、名称相似对象和客户端结论不是真值。
- S0～S3每个阶段至少一个EXIT Gate，每个EXIT Gate至少一个Reference；模板发布时缺失即拒绝。运行时实例缺失返回`DEPENDENCY_UNAVAILABLE`，不得用空集真值放行。
- readiness只作当前预览；推进命令必须在写事务中重新锁定并重验全部事实。

### BR-FPROJ008-002 六类满足谓词

- TASK：同项目稳定taskCode唯一命中，只有`DONE`满足；
- MILESTONE：同项目稳定milestoneCode唯一命中，只有`ACHIEVED`满足；
- DELIVERABLE：ACC Owner实现本Feature新增的`ProjectStageGateFactProviderApi`，按同租户、同项目、稳定deliverableCode锁定唯一`acc_project_deliverable`根，返回根ID、业务状态和row version；只有`ACCEPTED`满足。PROJ不得读取ACC表；仓库当前不存在可直接复用的`AccProjectDeliverableFact`接口。
- STATE：受控`S0_COMPLETED`～`S6_COMPLETED`精确映射ProjectStage，只有对应Stage `DONE`满足；
- APPROVAL/PROCESS：按Gate Reference固定businessKey和冻结定义版本关联最新尝试，只有已结束且整数状态`APPROVE(2)`满足；未启动、运行、驳回、撤回均是已知未满足。
- Owner缺失、重复、身份或版本不一致、未知状态、Provider缺失或不可用属于依赖不可判定，不得任选或解释为满足。

### BR-FPROJ008-003 Gate流程启动

- 公共PMS `ProjectStageGateProcessOwnerApi`位于`pms-module-project-api`，真实Provider位于`pms-module-integration`；按key+version解析精确定义ID，再由Flowable按definitionId启动。
- 唯一发起授权是`pms:project:update + PROJECT_MANAGE + 当前PROJECT_MANAGER`。Yudao start-user用户/部门白名单继续只约束通用发起入口，PMS不查询或复制；专用定义的精确BPMN含`START_USER_SELECT(35)`时不得发布或启动。
- businessKey固定为`PROJECT_STAGE_GATE:{gateReferenceId}`；tenant、project、stage、gate、reference、refType、refCode、refVersion及actor均由服务端冻结。
- 服务端设置Flowable authenticated initiator、`PROCESS_START_USER_ID`、`PROCESS_STATUS=RUNNING(1)`及skip-expression变量；客户端不能覆盖。
- 同operation同摘要返回原实例且不得再次启动；异摘要冲突。不得退化为按key启动当前最新版。

### BR-FPROJ008-004 相邻推进与原子性

- 命令只提交`projectId/expectedCurrentStage/expectedProjectVersion/expectedTreeVersion/Idempotency-Key`，目标阶段由服务端从冻结顺序推导。
- 仅`ACTIVE`项目的当前S0～S3可推进；不得跳级、指定非相邻目标或代理S4→S5。
- 同一Gate全部Reference满足、同一Stage全部EXIT Gate通过才允许推进。
- 成功事务共同完成当前Stage `DONE`、下一Stage `ACTIVE`、Project.current_stage/version、Gate `PASSED`、不可变`STAGE_ADVANCE`快照、审计、`ProjectStageChanged` Outbox和幂等结果。任一失败不提交部分成功事实。

### BR-FPROJ008-005 权限、锁序与错误

- readiness使用`pms:project:query + PROJECT_VIEW`；启动Gate流程和推进均使用既有`pms:project:update + PROJECT_MANAGE + 当前PROJECT_MANAGER`。
- 锁序固定为Project→当前/下一Stage→EXIT Gate→Reference→Owner稳定对象；后序Owner锁取得后不得回头补锁。
- Owner确定但未满足为`BUSINESS_GATE`并返回有序未满足项；Provider/身份未知为`DEPENDENCY_UNAVAILABLE`；版本漂移为`VERSION_CONFLICT`；均保持项目阶段不变。

## 4. API与事件

| API | 语义 |
|---|---|
| `GET /api/v1/pms/projects/{id}/stage-advance-readiness` | 返回当前阶段、服务端推导的相邻目标、Project/tree版本、有序Gate/Ref结果和允许动作；不授权推进 |
| `POST /api/v1/pms/projects/{id}/stage-gates/{gateReferenceId}/actions/start-process` | 以`If-Match + Idempotency-Key`启动冻结版本的PMS专用Gate流程；不接受definition、businessKey、tenant或actor覆盖 |
| `POST /api/v1/pms/projects/{id}/actions/advance-stage` | Body只含`expectedCurrentStage/expectedTreeVersion`，Header含`If-Match/Idempotency-Key`；目标阶段服务端推导 |
| `ProjectStageGateFactProviderApi.lockAndRevalidate` | 六类Owner Fact统一类型化SPI，封闭返回满足、未满足、版本冲突或依赖不可用 |
| `ProjectStageGateProcessOwnerApi.startFrozenProcess` | PMS窄版本化流程Owner命令；同operation幂等返回实例/定义Fact |

推进成功发布`ProjectStageChanged`，包含`eventId/tenantId/projectId/projectVersion/action=STAGE_ADVANCE/beforeStage/afterStage/stageSnapshotId/gateEvaluationSummaryRef/actorUserId/occurredAt`；门禁正文保留在PROJ快照，不进入事件。

## 5. 数据与物理边界

机器契约：`specs/features/F-PROJ-008-physical-contract.json`。

- `NO_PHYSICAL_DELTA`：复用`proj_project`、`proj_project_stage`、`proj_project_gate`、`proj_project_gate_reference`、`proj_project_task`、`proj_project_milestone`、`acc_project_deliverable`、`proj_project_stage_snapshot`及既有平台幂等/审计/Outbox。
- APPROVAL/PROCESS复用Flowable定义、运行和历史事实；不建PMS流程映射表。实现若不能唯一解析冻结身份，必须回SDS复审，不得在计划中静默补表。
- 快照的`guard_snapshot/provider_facts`逐项保存Gate/Reference身份、Owner对象键、业务版本、factVersion、outcome和稳定未满足码，不复制外域正文。

## 6. 旧实现复用边界

| 资产 | 裁决 | 边界 |
|---|---|---|
| ProjectMaster当前行锁、ProjectStage/Gate/Reference实例、Task/Milestone实例 | `DIRECT_REUSE` | 作为PROJ当前真值；补场景化锁查询和应用服务，不复制模型 |
| ACC `acc_project_deliverable`根及现有DO/Mapper | `DIRECT_REUSE_AS_OWNER_IMPLEMENTATION` | 只作为ACC Owner Provider的真实载体；PROJ不得直表读取，不能声称存在可复用的`AccProjectDeliverableFact` |
| `ProjectStageGateFactProviderApi/ProjectStageGateFact` | `ADDITIVE_NEW_PUBLIC_SPI` | 本Feature在`pms-module-project-api`新增统一SPI；ACC实现`ACC_DELIVERABLE` Provider并返回根ID、状态和row version |
| ProjectScopeApi、当前PROJECT_MANAGER、PlatformCommandExecutionApi | `DIRECT_REUSE` | 复用权限范围、主体、幂等/审计/Outbox能力 |
| ProjectGovernanceApplicationService的项目锁、快照、事件模式 | `COPY_THEN_ENHANCE` | 复制增强为独立StageAdvance服务；不得改写rollback/close/reopen行为 |
| `pms-module-integration`现有Flowable运行/历史查询模式 | `COPY_THEN_ENHANCE` | 新建窄版本化Gate Provider；不复用宽泛“无活动即无阻断”结论 |
| Yudao `BpmProcessInstanceApi` | `DO_NOT_REUSE_FOR_VERSIONED_START / PRESERVE_EXISTING` | 只能按key启动当前版本；通用流程保持不变 |
| PM-10治理命令、F-COM-001 S4→S5、旧`pms_project_phase` | `DO_NOT_REUSE_RUNTIME / PRESERVE_EXISTING` | 不改义、不代理、不形成第二阶段真值 |

## 7. UI

- 项目工作区当前Stage区域展示相邻目标、EXIT Gate及有序Reference结果；未知依赖与业务未满足分开展示。
- PROCESS/APPROVAL未启动时，对有权项目经理显示“发起审批”；运行中展示流程实例入口；批准后刷新readiness。
- 只有readiness当前全部满足且用户有权时显示推进按钮；点击后仍由服务端重验，前端状态不作授权真值。
- 推进成功刷新项目阶段、Stage→Task导航和阶段历史；S4只显示“进入验收阶段”专用入口提示，不调用通用推进。

## 8. 验收标准

- `AC-FPROJ008-001`：真实冻结模板项目在S0～S3可查询当前EXIT Gate和六类Reference结果；结果来自精确Owner事实，未满足项有序可读。
- `AC-FPROJ008-002`：项目经理可从项目工作区启动`PROCESS/APPROVAL`冻结定义版本；流程实例记录真实发起人、整数运行状态、固定businessKey和冻结变量，同operation重放不新增实例。
- `AC-FPROJ008-003`：TASK=DONE、MILESTONE=ACHIEVED、DELIVERABLE=ACCEPTED、STATE=DONE、流程批准完成后，对应Reference稳定为满足；DELIVERABLE结果必须来自ACC实现的统一Provider对唯一应交根的锁定重验，PROJ无ACC表依赖；未启动/运行/驳回/撤回不满足。
- `AC-FPROJ008-004`：全部EXIT Gate满足后，S0→S1→S2→S3→S4逐次相邻推进成功；每次仅产生一组Stage/Project/Gate/Snapshot/Audit/Outbox/幂等结果。
- `AC-FPROJ008-005`：任一业务未满足、依赖不可判定、版本漂移、无权或非当前项目经理时不改变阶段；空EXIT Gate/空Reference不能放行。
- `AC-FPROJ008-006`：S4通用推进明确拒绝并引导F-COM-001专用入口；不触发、补建或反推AcceptanceScopeBinding。
- `AC-FPROJ008-007`：真实浏览器从项目工作区完成“查看门禁→启动审批→批准后刷新→推进→查看新阶段/历史”的正向闭环；自动化只补关键服务、事务和幂等回归，不建立大型负向矩阵。

## 9. 实施与验证原则

采用实现优先的最小增量：先完成本地TASK/MILESTONE/STATE评估和readiness/advance正向链，再接ACC交付件Fact和BPM版本化Provider，最后接工作区UI与一次真实浏览器闭环。不得先为未实现能力铺设失败测试；代码形成后补直接单测、真实MySQL事务验证和一条浏览器正向验收。无关Phase 1/2/3、割接、全仓回归和低收益异常组合不作为本Feature实施前置。

## 10. Definition of Ready

| 项目 | 当前状态 |
|---|---|
| PM-03正向运行切片及S4后置边界 | PASS |
| 六类Owner与唯一满足谓词 | PASS |
| 版本化Gate流程授权、发起及状态 | PASS |
| 权限、锁序、事务、幂等、事件 | PASS |
| 物理差量与旧实现复用边界 | PASS（NO_PHYSICAL_DELTA） |
| 最小正向UI/浏览器验收 | PASS（已定义，未实施） |
| 独立Feature Ready裁决 | PASS（744c70a0） |

结论：`BASELINE / READY / NOT_STARTED`。Feature Ready既有GO保持有效，`Q-FPROJ-008`已关闭；允许形成并独立审核唯一Technical Plan，计划GO前不得创建Task或修改产品代码。
