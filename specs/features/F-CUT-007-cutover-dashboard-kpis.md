# F-CUT-007 割接首页授权KPI Feature Spec

> 文档状态：`BASELINE`
> Feature Ready：`READY / GO@f6141e21`
> Requirement：`CUT-01（V2/P0）`
> Requirement切片覆盖：`CUT-01@V2=FULL`
> Owner Context：`CUT（变更切换与稳定治理）`
> 前置Feature：`F-CUT-002`、`F-CUT-003`、`F-CUT-004`、`F-CUT-005`、`F-CUT-006`
> 支撑依赖：`ProjectScopeApi.ACTION_VIEW`、各阶段既有CUT查询动作守卫
> 机器合同：`specs/features/F-CUT-007-api-contract.json`、`specs/features/F-CUT-007-physical-contract.json`
> 旧实现复用审计：`specs/features/F-CUT-007-legacy-reuse-audit.md`
> 唯一Technical Plan：`未生成；Feature Ready通过前禁止生成`

## 1. 业务目标

在现有割接任务工作台首页，为当前用户展示其授权项目范围内的待办、已归档、审批中、驳回待修改四项实时KPI。KPI只读聚合现有P1～P6事实，不修改任务状态，不新增业务事实，也不以按钮、通知或接口成功替代业务完成。

## 2. Scope

### 2.1 包含

- `GET /api/v1/pms/cutover-dashboard/kpis`只读接口；
- 当前受信租户、`pms:cutover-task:query`功能权限与`ProjectScopeApi.ACTION_VIEW`可见项目交集；
- 待办、已归档、审批中、驳回待修改四项任务数及服务端生成时间；
- 待办按现有P2～P6真实写动作守卫求并集，并按`taskId`去重；
- 现有统一割接工作台只读KPI卡片；
- CUT单元、集成与受控页面验证可使用测试作用域的确定性ProjectScope替身，继续使用CUT真实领域事实和动作守卫。

### 2.2 不包含

- 新表、物化视图、缓存、快照、事件或定时任务；
- KPI趋势、日期筛选、下钻筛选、导出、统一待办或跨领域经营报表；
- 修改P1～P6状态机、allowedActions、权限键或现有命令守卫；
- 为ProjectScope或其他跨模块能力新增生产Fake、fallback或空成功Provider；
- COM、AST、IMP、PLT、SYSTEM、PROJ或Yudao实现；
- 修改旧`cut-task`页面、旧`pms_cut_*`接口或旧运行数据；
- F-CUT-002～006生产Provider接通、Task 10、真实浏览器或Implementation Done。

## 3. 业务规则

### BR-FCUT007-001 可见范围

- 调用者必须通过`pms:cutover-task:query`功能权限；租户从受信上下文取得。
- CUT先取得当前用户`ProjectScopeApi.ACTION_VIEW`可见项目集合，再在该集合内聚合。空集合返回四项计数均为0，禁止省略项目条件后扩大查询。
- ProjectScope不可用、返回损坏事实或身份不一致时，整个KPI请求失败关闭；不得返回部分计数，也不得把未知事实投影为0。
- 复用P2～P6动作守卫时，PROJ、AST、CUS、IMP、PLT、SYSTEM、INT12任一实际物理Owner不可用或事实损坏，必须沿用对应既有Feature合同的错误类别并在`ErrorData.ownerContext`标识真实Owner；不得压扁成CUT错误。只有CUT自有投影或动作事实损坏时才使用`ownerContext=CUT`。
- 查询权只产生KPI可见性，不自动产生待办。待办必须另行满足本Feature锁定的真实写动作守卫。

### BR-FCUT007-002 三项状态KPI

- `archivedCount`：可见任务中`taskStatus=ARCHIVED`的`COUNT(DISTINCT taskId)`。
- `approvingCount`：可见任务中`currentStage=P5 AND taskStatus=APPROVING`的`COUNT(DISTINCT taskId)`。
- `rejectedPendingModificationCount`：可见任务中`currentStage=P4 AND taskStatus=PLAN_DRAFTING`，且当前未被替换的审批实例状态为`REJECTED`的`COUNT(DISTINCT taskId)`。
- 上述三项按当前CUT权威任务/审批投影计算，不从旧tinyint、通知、按钮、历史审批实例或自由文本推导。

### BR-FCUT007-003 待办KPI

- `todoCount=COUNT(DISTINCT taskId)`；候选必须同时满足受信租户、`ACTION_VIEW`可见项目、`taskOrigin=NEW_PLATFORM`且任务未归档。
- 只有当前操作者至少拥有以下一个现有服务端真实写动作时，该任务才进入待办：
  - P2：`SAVE_ASSESSMENT`、`SUBMIT_ASSESSMENT`；
  - P3：`GENERATE_CHECKLIST`、`SAVE_CHECKLIST`、`REQUEST_COLLECTION`、`SUBMIT_CHECKLIST`；
  - P4：`CREATE_DRAFT`、`SAVE_DRAFT`、`SUBMIT_PLAN`、`REVISE_PLAN`；
  - P5：`APPROVE`、`REJECT`；
  - P6：`CREATE_CLOSURE`、`SAVE_CLOSURE`、`REQUEST_COLLECTION`、`LINK_MANUAL_RESULT`、`SUBMIT_CLOSURE`。
- 真实动作判定必须复用各阶段既有服务端守卫，保持当前状态、Owner资格、功能权限、项目范围、来源可比性、revision、完整性、hold和节点状态语义一致。不得只按阶段、状态、负责人或权限做粗略SQL近似。
- P5候选等于现有`myTodos`真实审批资格与本KPI `ACTION_VIEW`可见范围的交集。
- `UPDATE_APPROVED_CONTACTS`、只读详情、下载、审批管理员改派、未来节点、终态、hold且无动作、旧平台任务均不进入待办。
- 同一任务同时具备多个动作或阶段投影时仍只计1次。待办可与审批中、驳回待修改重叠，四项计数不得相加解释为任务总数。

### BR-FCUT007-004 查询实现边界

- 使用CUT场景化批量候选/投影查询和可复用动作守卫完成聚合；禁止逐任务调用Controller/HTTP、N+1或复制第二套动作语义。
- 查询只读取已提交事实，不加业务写锁、不写审计/Outbox、不改变任务或阶段事实。
- `generatedAt`取服务端时钟。响应必须一次性返回四项计数；任何Owner/动作事实不可判定时整体失败。
- 跨模块预留接口在CUT测试与受控验收中可由确定性替身模拟正常事实；替身不得进入`src/main`生产装配，也不得作为生产浏览器或Implementation Done证据。

## 4. API、权限与Owner边界

- 精确REST、wire、错误和聚合合同见`F-CUT-007-api-contract.json`。
- 错误响应固定为`CommonResult<ErrorData>`；HTTP 403/500/503的`data`非空且精确为`ErrorData{category,reasonCode,recoveryAction,ownerContext}`。Provider不可用为503并重试Owner恢复，Owner事实损坏为500并联系支持，任何错误均不返回部分计数。
- 本Feature只复用`pms:cutover-task:query`，不新增功能权限。
- ProjectScope仍归PROJ物理Owner；CUT只消费公开接口。本Feature不修改PROJ、其他Context或Yudao。
- KPI返回计数和服务端时间，不返回任务明细、项目清单、审批正文或设备信息。

## 5. 数据与迁移

- 不新增或修改业务表、索引、字典、菜单、事件、缓存或迁移脚本。
- 只读现有`cut_task`及P2～P6当前事实/投影；精确读取边界见physical contract。
- 旧`pms_cut_*`数据不迁移、不双写、不反向补造KPI事实。

## 6. 验收标准

- AC-FCUT007-001：有查询权限的用户只看到`ACTION_VIEW`授权项目内四项KPI；空可见范围稳定返回四项0。
- AC-FCUT007-002：归档、P5审批中和P4驳回待修改按锁定谓词去重计数，不泄漏任务明细。
- AC-FCUT007-003：待办只统计当前真实可执行写动作；只读可见但不可办理的任务不计入，同任务多动作只计1次。
- AC-FCUT007-004：P5待办与`myTodos`资格一致并叠加KPI可见范围；Owner不可用时整体失败且不返回部分结果。
- AC-FCUT007-005：调用KPI前后CUT任务、审批、方案、清单和闭环事实均不变化。
- AC-FCUT007-006：CUT可用受控跨模块替身完成正常正向聚合与页面闭环；生产Provider/真实浏览器未形成前不得声明Implementation Done。

## 7. Feature Ready Gate

当前：`BASELINE / READY / GO@f6141e21 / NOT_STARTED`。Feature Ready最终状态关闭已通过；下一Gate为生成并复审唯一Technical Plan。当前不授权DDL、后端、前端或运行实现。
