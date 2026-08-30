# F-CUT-002 割接任务接入与人工分级 Feature Spec

> 文档状态：`DRAFT`
> Feature Ready：`NOT_READY / NO-GO`
> Requirement：`CUT-01（V1/P0）`、`CUT-02（V1/P0）`
> Requirement切片覆盖：`CUT-01@V1=PARTIAL；CUT-02@V1=PARTIAL`
> Owner Context：`CUT（变更切换与稳定治理）`
> 前置Feature：`F-CUT-001`、`F-PROJ-003`、`F-PROJ-007`、`F-IMP-001`
> AST支撑Task：`T-FIMP001-AST-01`
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`
> 独立裁决：`NO-GO`（锁定提交`72ccb83f8052758e70fc585b1226403b6a825311`）
> 旧实现复用审计：`specs/features/F-CUT-002-legacy-reuse-audit.md`
> 机器物理/迁移合同：`specs/features/F-CUT-002-physical-contract.json`（迁移Contract Gate `PASS`；Feature物理基线仍`REVIEW_REQUIRED`）
> REST/Internal API机器合同：`specs/features/F-CUT-002-rest-api-contract.json`（`REVIEW_REQUIRED`）

## 1. 业务目标

为割接-一线工程师提供首个CUT正向闭环：在本人有权项目和设备范围内自建唯一CutoverTask，冻结IMP就绪快照后进入P2，填写并提交问卷和人工A/B/C/D级；A/B/C进入P3，D跳过P3进入P4。任务详情展示P2～P6五步工作台骨架和只读P1来源事实。

## 2. Scope

### 2.1 包含

- 一线工程师按设备序列号查询授权项目/设备并自建P1任务；服务端生成任务编号；
- 一线自建前由只读上下文解析接口按`projectId`稳定排序返回全部可选项目候选、各自稳定设备、办事处、客户服务等级与IMP就绪快照及其版本；多候选必须由工程师明确选择，服务端不得代选，确认创建时逐项锁定重验；
- 可信内部来源创建命令契约，支持ITR来源键和项目事件ID幂等，但本Feature不实现Producer或第三方适配器；
- 唯一CutoverTask、来源事实、项目/客户/设备引用及必要快照、当前阶段和状态版本；
- 同项目同设备范围同一时点仅一个活动任务，后续任务关联前次已结束任务；
- P2问卷暂存、提交和人工A/B/C/D级；不计算建议等级；
- A/B/C→P3、D→P4的服务端状态迁移；P2～P6工作台只读骨架；
- 创建和每次继续前通过F-IMP-001公开契约读取并重验快照；
- CUT单元/集成装配可对PROJ、AST、CUS、IMP正式消费端口使用受控正向模拟；模拟不得进入生产装配或形成Owner完成证据；
- 租户、项目/设备范围、一线工程师权限、幂等、并发、审计和响应式UI。

### 2.2 不包含

- EXE-06生产Provider或IMP快照表；
- ITR连接器、第三方HTTP、项目事件Producer、自动匹配或自动指派；
- CUT-03清单、P4方案、P5审批/服务经理复核、P6闭环归档；
- CUT-01@V2首页KPI、CUT-02@V3建议等级、通用取消/暂停/转派/SLA或通用工单；
- 修改旧`pms_cut_task/risk/plan`、旧页面、接口、数据或Yudao基础平台；
- 用附件、旧表、手工SQL、测试替身或种子声明真实依赖或浏览器闭环完成。

## 3. 业务规则

### BR-FCUT002-001 P1自建与来源幂等

- 用户自建请求只提交项目ID、设备序列号、任务名称、割接类型、组网模式、计划时间、背景以及前一次只读上下文解析返回的PROJ/AST/CUS/IMP期望版本；任务编号、来源主体、项目/客户/设备引用与快照由服务端生成。期望版本只作为并发守卫，不允许客户端写Owner事实。
- 创建前重新校验当前租户、工程师主体、PROJ公开scope action返回的“本人参与、负责或明确授权项目”范围、AST公开契约返回的全部设备当前项目归属及版本，并锁定IMP明确`READY`快照。不得以“可管理项目”替代或收窄PRD授权语义。
- 用户命令的幂等作用域为`CUTOVER_SELF_CREATE:{tenantId}:{actorId}:{Idempotency-Key}`；同键同规范化请求返回原任务，同键异请求冲突。可信内部来源分别以`sourceSystem+sourceBusinessNo`和`businessEventId`唯一。
- 复用平台既有命令幂等摘要，不新增第二套哈希、指纹或幂等表。
- ITR、项目事件重复请求只返回既有任务，不覆盖来源、上下文、阶段或历史。

### BR-FCUT002-002 活动设备范围唯一性

- 同一租户、项目和任一设备同一时点只能属于一个活动CutoverTask；创建时按规范化设备ID顺序锁定活动范围记录。
- 前一任务结束后可创建新任务，并保存不可变`previousTaskId`；同一直接前驱最多一个后继且必须同租户、同项目。F-CUT-002不拥有任务结束转换，因此本Feature只创建首任务；该字段和线性约束供后续拥有终态的CUT Feature使用，不得猜测终态。
- 并发创建最多一个成功；失败请求不生成任务、设备范围、阶段历史或成功审计。

### BR-FCUT002-003 P2问卷与人工等级

- 只有`GRADE_CONFIRMING`状态且当前一线工程师具有任务与项目设备范围权限时可暂存/提交。
- 问卷由CUT固定为服务端模板`CUT_P2_MANUAL_ASSESSMENT@1`，包含业务重要等级、操作复杂度等级、隐患风险等级、备件是否申请；客户服务等级来自CUS并作为同屏只读上下文展示。模板标识/版本由服务端写入，客户端不得提交或猜测“当前启用版本”。
- 客户服务等级只能来自CUS `CustomerServiceLevelFactApi`当前有效版本并冻结到评估上下文；现有`CustomerSummaryDTO`不含该事实，不能替代。`NOT_CONFIGURED`或版本失效时允许保存草稿但不得提交，不接受用户手工补值。
- 草稿答案四个精确键均可为`null`，暂存不推进状态；每次暂存只读刷新DRAFT上下文，允许客户等级从`NOT_CONFIGURED`变为`AVAILABLE`。提交必须具备全部非空答案、当前`AVAILABLE`客户等级和人工最终等级A/B/C/D，并保存评估版本、上下文快照、提交人和时间。
- 不默认C级，不计算分值、区间或系统建议等级。

### BR-FCUT002-004 状态与重验

```text
P1 accepted -> GRADE_CONFIRMING
GRADE_CONFIRMING --submit A/B/C--> SURVEYING
GRADE_CONFIRMING --submit D--> PLAN_DRAFTING
SURVEYING/PLAN_DRAFTING --later CUT continuation proves submitted context stale--> GRADE_CONFIRMING (replacement DRAFT)
```

- 状态只能由创建、P2提交以及后续CUT继续命令触发的内部评估失效替代迁移推进，客户端不得直接写阶段、状态或等级。失效迁移同事务保留旧评估、追加下一版DRAFT、清空任务等级、返回P2并追加历史；GET和外部Provider不得执行该写入。
- 创建和P2提交前均按明确IMP快照ID/版本重验；项目、设备范围、批准方案、EXE来源事实变化或Provider不可用时保持当前状态并返回原因。
- P2提交使用任务`If-Match`、评估版本和`Idempotency-Key`；并发只接受首个有效版本。
- P5复核与驳回回改未在本Feature实现，因此CUT-02覆盖固定为`PARTIAL`。

### BR-FCUT002-005 权限与工作台

- 一线工程师只能查询、创建和办理本人有权项目及设备的任务；项目只读角色只读；服务经理不获得P1创建、指派、取消或P2代填权限。
- P1来源事实在任务详情只读；P2～P6固定显示五步，未实现步骤显示后续阶段但不提供伪操作按钮。
- `allowedActions`由服务端只读`inspect`功能权限、项目/设备范围、任务状态、负责人和Owner事实后提示性返回；详情GET不得取得写锁。真正提交命令必须在写事务内独立执行全部`lockAndRevalidate`，前端不按角色名称自行推导。
- 审计记录来源键、项目/设备稳定ID、IMP快照、问卷字段键、人工等级、状态前后值、版本、操作者和时间；不复制设备凭证或附件正文。

## 4. API与模块契约

精确请求、响应、Header、Wire Long/时间、权限、`allowedActions`和错误恢复动作由`specs/features/F-CUT-002-rest-api-contract.json`锁定。所有用户路径继承`/api/v1/pms`：

| 接口 | 操作 | 契约 |
|---|---|---|
| `/cutover-tasks/actions/resolve-create-context` | `POST` | 只读返回SN对应的全部授权项目候选、办事处、设备、客户服务等级和IMP就绪事实；多候选由工程师明确选择，不创建业务记录 |
| `/cutover-tasks` | `GET` | 按服务端项目/设备范围分页查询 |
| `/cutover-tasks` | `POST` | 一线自建；`Idempotency-Key`；只接受业务输入，不接受来源主体、状态或等级 |
| `/cutover-tasks/{id}` | `GET` | 返回来源上下文、P2～P6工作台和`allowedActions` |
| `/cutover-tasks/{id}/assessment` | `PUT` | P2暂存；`If-Match`任务版本和评估版本 |
| `/cutover-tasks/{id}/assessment/actions/submit` | `POST` | 提交完整问卷与人工等级；`Idempotency-Key/If-Match` |

权限固定为`pms:cutover-task:query/create/save-assessment/submit-assessment`四项；详情动作只允许`SAVE_ASSESSMENT/SUBMIT_ASSESSMENT`，且只读投影必须与功能权限、项目范围、任务Owner、当前状态和Owner事实`inspect`一致，写命令再独立锁定重验。`LEGACY_FORWARD`不返回任何动作。

内部`CutoverTaskIntakeApi.create`使用`ITR/PROJECT_EVENT`严格判别联合，只接受受信租户、来源身份和明确`handlingEngineerUserId`；该用户必须在写前通过同一项目/设备操作范围验证，不建立自动匹配、指派或领取流程。ITR按`tenant+sourceSystem+sourceBusinessNo`、项目事件按`tenant+businessEventId`幂等，并执行与自建相同的PROJ/AST/CUS/IMP重验。本Feature不实现任何Producer或第三方客户端。

CUT通过`ImplementationReadinessApi.inspect/lockAndRevalidate`消费IMP；通过`ProjectScopeApi.ACTION_EDIT`消费“本人参与、负责或明确授权项目”范围，通过AST物理Owner支撑Task `T-FIMP001-AST-01`的`DeviceScopeFactApi`消费`deviceId/currentProjectId/projectAssignmentVersion`，通过CUS `CustomerServiceLevelFactApi.inspectCurrent/lockAndRevalidate`消费当前有效客户服务等级事实。两个尚未通过公共机器Contract Gate的跨Context接口只登记消费合同，不在CUT实现Provider；禁止依赖其他Context的Service、Mapper、DO或业务表。

## 5. 数据与迁移边界

- 新建CUT Owner前向表：`cut_task`、`cut_task_device_scope`、`cut_task_stage_history`、`cut_assessment`。其中聚合根和评估表名严格沿用正式SDS，不另建同义表。
- `cut_task`保存来源判别联合、不可变`previous_task_id`、背景、当前阶段/状态、负责人、IMP快照、PROJ水位及项目/办事处/设备/客户/就绪上下文快照；`cut_task_device_scope`保存稳定设备ID、SN快照、归属版本和活动唯一标记；`cut_task_stage_history`只追加P1接入与P2提交产生的阶段迁移；`cut_assessment`保存DRAFT/SUBMITTED/INVALIDATED版本、服务端固定模板、草稿可空/提交非空答案JSON、上下文JSON和人工等级。已提交版本失效时同事务追加下一版DRAFT并原子切换当前评估引用，不留下无current marker状态。
- 已提交评估和阶段历史只追加；当前任务根保存阶段、状态、人工等级、当前评估ID、IMP快照ID/版本和乐观锁版本。
- 活动设备范围使用`uk(tenant_id, project_id, device_id, active_marker)`控制并发；任务终态由后续Feature在同事务清除活动标记。
- 来源唯一键分别约束`tenant_id/source_system/source_business_no`和`tenant_id/business_event_id`；用户自建幂等复用平台命令事实。
- `CutoverTask`严格执行正式迁移契约`pms_cut_task -> cut_task / CURRENT_FORWARD`：只前向迁移经字段、状态和完整性映射证明有效的任务事实，不改写、双写或逆向同步旧表；无法无损映射的旧行不得猜测补齐。
- F-CUT-002只允许把通过身份、项目归属和审计完整性校验的旧行迁成`task_origin=LEGACY_FORWARD/task_status=LEGACY_UNKNOWN`只读身份投影；目标保留`legacy_task_id/legacy_cutover_type_raw/legacy_network_mode_raw/legacy_status_value/legacy_mapping_version`，当前`cutover_type/network_mode`及全部新路径事实保持空。旧粗粒度类型/组网不冒充F-CUT-001正式字典，任何旧`0..8`状态也不解释为`GRADE_CONFIRMING/SURVEYING/PLAN_DRAFTING`。只读投影没有`allowedActions`，不生成设备范围、评估或阶段历史，也不参与新路径的活动设备唯一性。
- 旧行的`risk_level/source_type/source_id/approval_opinion/remark/actual_time`不得推导人工等级、可信来源、IMP快照、问卷、阶段或P6结果。软删除行、身份/枚举损坏行、项目无法在同租户解析行及目标身份冲突行保留在旧表并形成明确迁移处置，不插入目标；不得以默认值、测试种子、名称或时间补造。
- `CutoverAssessment`严格执行`cut_assessment / NEW_ONLY`：旧`pms_cut_risk`不是P2问卷与人工等级的权威来源，不迁入`cut_assessment`；旧`pms_cut_risk/plan`保持不变。
- Flyway使用实施时下一未占用版本；具体字段/状态映射必须在Feature Ready前形成可评审迁移契约，不以测试种子作为生产迁移事实。

## 6. UI

- 新建割接任务首页和任务详情工作台，不改旧`cut-task`页面；新菜单使用`pms:cutover-task:*`权限。
- 自建入口先输入SN并展示服务端按项目稳定排序返回的全部授权候选、办事处、设备和只读上下文；多项目时由工程师明确选择后再确认创建。客户等级未配置不阻断创建或草稿，但必须清楚提示且禁止P2提交。
- 详情固定展示P2～P6五步：P2可填写/只读取决于服务端动作；P3/P4只反映已进入的下一阶段，P5/P6为后续状态，不伪造完成。
- 问卷和人工等级为同一提交区；不显示自动建议、默认等级、自动指派、取消、暂停或转派。
- 320/768/1024/1440无页面级横向溢出，错误不能只用颜色表达。

## 7. 验收标准

- AC-FCUT002-001：一线工程师输入跨多个有效项目的SN时获得稳定候选列表，明确选择一个有权项目及其全部设备并使用READY快照创建唯一任务；任务进入P2，P1列表展示割接来源、办事处和任务生成时间。
- AC-FCUT002-002：同一自建幂等键重放返回同一任务；异载荷、重复来源键或同设备活动冲突不新增任务。
- AC-FCUT002-003：P2可暂存；完整问卷和人工A/B/C级提交后进入P3，D级进入P4，且不生成自动建议等级。
- AC-FCUT002-004：缺少必填答案/人工等级、越权、跨租户、陈旧任务版本或陈旧IMP快照保持P2且无阶段历史副作用。
- AC-FCUT002-005：项目/设备/方案/EXE来源事实变化或Provider不可用时，创建或P2提交被拒绝并保持原状态。
- AC-FCUT002-006：服务经理不能创建、代填、指派或取消；项目只读角色只能查看授权任务。
- AC-FCUT002-007：真实MySQL验证来源唯一、活动设备唯一、评估历史不可变和事务回滚；真实浏览器使用IMP生产Provider生成的权威READY快照完成自建到P3/P4闭环。
- AC-FCUT002-008：旧割接页面、接口、表和数据行为不变；附件不参与需求、差异、数量、阻断或完成决策。

## 8. Feature Ready Gate

当前结论：`NOT_READY / NO-GO`。

最近Gate为`F-CUT-002 API/Physical Machine Contract`：四表字段与联合约束、REST/Internal API、权限、错误、来源身份、评估快照及PROJ/AST/CUS/IMP消费边界必须先独立通过。随后还须分别取得`ImplementationReadinessApi`与`CustomerServiceLevelFactApi`公共机器合同Gate；无需等待F-IMP-003～005或CUS生产实现完成，直接消费合同冻结后即可重审F-CUT-002 Feature Ready，并在通过后使用受控正向模拟实施CUT自有单元/集成闭环。模拟不进生产装配、不产生正式就绪/客户等级事实、不支撑真实浏览器验收；生产Owner事实未形成、合入并通过契约验证前，不得声明Implementation Done或真实浏览器正向闭环。
