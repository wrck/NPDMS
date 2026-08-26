# F-SOL-001 项目工期基线与变更审批 Feature Spec

> 文档状态：`IN_REVIEW`
> Feature Ready：`PENDING_INDEPENDENT_REVIEW`
> Requirement：`PRE-01（V1/P1）`
> Owner Context：`SOL（交付准备与方案）`
> 前置Feature：`F-PROJ-001`、`F-PROJ-003`、`F-PROJ-005`、`F-PROJ-007`
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`
> 边界裁决：`GO`，PRE-01单独建立`F-SOL-001`，不与PRE-02、PLN-01或PLN-04合并
> Technical Plan：Feature Ready独立GO且NPDMS锁定新规格提交后全新生成

## 1. 目标

在SOL拥有的`ConstructionPlan`聚合内建立项目唯一当前生效工期基线。首次工期合法保存即生效；后续修改必须形成版本化变更、冻结原值/新值/计算口径/附件引用，由当前项目服务经理审批。审批通过后新工期成为唯一当前版本并记录“计划待重算”；PLN-01/PLN-04未实施、不可用或失败均不回滚工期生效，也不覆盖当前施工计划。

## 2. Scope

### 2.1 包含

- 本人负责项目的项目经理按“起止日期”或“工期时长+计算起点”首次录入，服务端统一计算开始日期、结束日期和自然日时长；
- 首次录入在同一事务创建`sol_construction_plan`、revision 1、当前生效引用、幂等成功和审计；
- 变更草稿、提交、通过、驳回和在待审时撤回；同一项目同一时点最多一个`PENDING_APPROVAL`变更；
- 原生效revision、候选revision、变更原因、原因规则结果和客户延期材料`FileArtifact`稳定引用冻结；
- 当前生效revision指针、待审变更指针与计划重算影响状态分轴管理；
- 项目维度当前工期、revision历史和变更历史查询，不覆盖历史版本；
- `ProjectScopeApi`、PROJ公开项目角色事实查询、功能权限和申请人/审批人主体约束；
- 平台幂等、`plt_operation_audit`、租户、CAS、单租户配置感知上下文和失败无业务副作用；
- 项目工期页面/项目工作区真实入口、Yudao/Element Plus风格与320/768/1024/1440响应式布局；
- 旧`pms_schedule_backward`与`pms_plan_change_request`的架构、状态迁移和日期校验仅作复用审计，只复用符合本规格的局部逻辑。

### 2.2 不包含

- PRE-02工勘分工、Preparation/DynamicFormInstance、实施就绪门禁；
- PLN-01施工计划倒排/重算和差异生成，PLN-02/03预警统计，PLN-04施工计划审批/生效/推进S3；
- 直接修改Project阶段或旧/新施工计划时间；
- INT-05钉钉通知与外部审批；通知失败不改变待审业务事实；
- 为未实施PLN-01创建事件、Outbox消费者、假重算结果或空`-api`模块；
- 旧`pms_schedule_backward/pms_plan_change_request`历史数据自动迁移；AI-MIG-000、Deployment、SIT、UAT和Release。

## 3. 业务规则

### BR-FSOL001-001 口径与首次生效

- `DATE_RANGE`必须提交`startDate/endDate`，平台按含首尾的自然日计算`durationDays`。`DURATION_FROM_START`必须提交`startDate/durationDays`，平台计算`endDate=startDate+durationDays-1`。
- `durationDays`必须为正整数，起止日期不得倒置；同时提交的派生值与平台计算不一致时拒绝，不作客户端值猜测。
- 首次保存只允许`ACTIVE`、当前阶段为S1且已有当前项目经理的项目。一次事务生成plan、revision 1并将`current_duration_revision_id`指向该revision；失败不留孤立revision。后续变更仍要求项目`ACTIVE`，不因项目已离开S1而覆盖或删除既有工期历史。
- 首次生效后立即把计划影响状态记为`PENDING_RECALCULATION`并冻结来源工期revision；不调用、不伪造PLN-01结果。

### BR-FSOL001-002 变更草稿与冻结

- 变更草稿必须引用创建时的`baseRevisionId`。草稿可修改候选revision、原因和材料；修改只在`DRAFT`且`If-Match`命中时生效。
- 变更原因使用基础平台字典`pms_duration_change_reason_type`。是否要求客户材料由基础平台参数`pms.sol.duration-change.customer-evidence-required-reason-codes`决定；服务端在提交时回源、解析并冻结`customer_evidence_required`结果。配置缺失、非法或原因不在启用字典中时失败关闭。
- 当`customer_evidence_required=true`时必须提交有权访问、已通过文件安全校验的`fileArtifactId/fileVersion`。只保存稳定引用和版本，不保存URL或文件正文。
- 提交时重新校验当前生效revision仍等于`baseRevisionId`，冻结候选revision和变更快照，将状态改为`PENDING_APPROVAL`并写入`pending_change_id`。旧基线、已有待审或版本冲突均拒绝。

### BR-FSOL001-003 审批生效与驳回

- 只有目标项目当前有效主责一级/二级服务经理可审批，且不得审批本人申请。服务经理主体事实必须通过PROJ公开只读契约查询，SOL不读PROJ的Mapper、Repository或业务表。
- 通过命令锁定plan和pending change，重验候选revision、附件版本和审批主体后，在同一事务将`current_duration_revision_id`切换为候选revision，变更记为`APPROVED`，清空pending指针，并将计划影响状态设为`PENDING_RECALCULATION`。
- PLN-01未实施、查询不可用或重算失败不得让通过命令回滚；旧施工计划及其生效引用保持不变。本Feature不发布跨Context事件。
- 驳回必须有非空意见，将变更记为`REJECTED`并清空pending指针；撤回只允许申请人在`PENDING_APPROVAL`执行并记为`WITHDRAWN`。两者均不改当前生效revision和计划影响状态。

### BR-FSOL001-004 三类事实分轴

| 事实 | 物理承载 | 规则 |
|---|---|---|
| 版本审批生命周期 | `sol_construction_plan_change.status_code` | `DRAFT -> PENDING_APPROVAL -> APPROVED/REJECTED/WITHDRAWN`；非法迁移拒绝 |
| 唯一当前生效工期 | `sol_construction_plan.current_duration_revision_id` | 首次生效或审批通过才能切换；不从change status临时推断 |
| 计划重算影响 | `plan_recalculation_status_code/source_duration_revision_id` | 本Feature只写`PENDING_RECALCULATION`；`RECALCULATED/RECALCULATION_FAILED`由后续PLN-01在真实处理时前向接入 |

三类事实不得合并为一个“工期状态”，也不得由前端显示状态反写。

### BR-FSOL001-005 权限矩阵

| 能力 | 功能权限码 | ProjectScope | 主体/字段约束 |
|---|---|---|---|
| 查看当前工期和历史 | `pms:construction-plan:query` | `VIEW` | 项目成员只读；附件下载另行回源文件权限 |
| 首次录入、草稿、提交和撤回 | `pms:construction-plan:duration-manage` | `EDIT` | 当前项目经理；不得直接修改生效revision |
| 通过/驳回 | `pms:construction-plan:duration-approve` | `MANAGE` | 当前主责服务经理，不得为申请人 |

服务端按受信租户、功能权限、ProjectScope、当前项目角色、变更状态和版本依次重验。前端按钮与角色名称不是权限真值。

### BR-FSOL001-006 幂等、并发与审计

- 首次录入、创建草稿、提交、通过、驳回和撤回均必须提交`Idempotency-Key`；同键同载荷重放首次结果，同键异载荷冲突，进行中重复返回稳定冲突。
- 草稿修改和全部状态命令使用`If-Match`与plan/change对应版本CAS。决策事务锁定plan当前行后重验current/pending指针，并发只允许一个结果成功。
- 成功审计分别冻结创建/草稿/提交/通过/驳回/撤回的planId、revisionId、changeId、前后指针、前后状态、操作人、原因、operationId和时间。失败事务回滚后使用平台公共审计记录稳定拒绝码和必要安全事实。
- 不写Outbox事件。计划待重算是SOL内部可查询状态，不充当未实施PLN-01的交付成功事实。

## 4. API契约

所有HTTP路径继承`/api/v1/pms`前缀，返回平台统一`CommonResult`和稳定业务错误码。

| 接口 | 操作 | 契约 |
|---|---|---|
| `/construction-plans` | `POST` | 首次工期；Header必填`Idempotency-Key`，输入`projectId/calculationBasis/startDate/endDate/durationDays`，只允许当前项目经理 |
| `/construction-plans/{id}` | `GET` | 返回plan、当前生效revision、pending change摘要、重算影响状态、planVersion和允许操作 |
| `/construction-plans` | `GET` | 参数`projectId`；只返回VIEW范围内的唯一plan，无记录返回空业务结果 |
| `/construction-plans/{id}/revisions` | `GET` | 稳定`revisionNo,id`分页，返回历史工期及是否当前；不返回文件URL |
| `/construction-plans/{id}/changes` | `POST`, `GET` | POST创建草稿，必填`Idempotency-Key/If-Match`；GET稳定`createTime,id`分页 |
| `/construction-plans/{id}/changes/{changeId}` | `GET`, `PATCH` | PATCH仅修改实际提交的草稿字段，必填`If-Match`；空PATCH拒绝，提交`null`表示清空可空材料引用 |
| `/construction-plans/{id}/actions/{submit\|approve\|reject\|withdraw}` | `POST` | Header必填`Idempotency-Key/If-Match`，输入`changeId`；reject必填`opinion`；服务端不接受tenantId、applicantId或approverId自报 |

列表响应包含`items/nextCursor/hasMore`，使用服务端限制的`pageSize`和稳定排序。所有写命令在业务边界校验日期组合、状态、主体、版本和附件引用。

### 4.1 模块边界

- SOL位于现有`pms-module-engineering`，实体、Mapper、Repository和应用服务均由SOL持有；PROJ不读SOL表。
- SOL消费现有`ProjectScopeApi`；为服务经理/项目经理主体重验，在现有`pms-module-project-api`前向增加一个只读、场景化`ProjectParticipantFactApi.inspect(ProjectParticipantFactQuery)`，返回项目、用户、有效角色代码、责任类型和事实版本；空/越租户/未知数据失败关闭。
- 本Feature不新建SOL `-api`模块。后续PLN-01与PRE-01同属SOL且有真实读取方时，直接读取`current_duration_revision_id`和影响状态，再以前向命令回写真实重算结果；当前不建空Provider。

## 5. 数据与物理边界

机器契约：`specs/features/F-SOL-001-physical-contract.json`。

- 前向新建`sol_construction_plan`、`sol_construction_plan_revision`和`sol_construction_plan_change`；`sol_construction_plan_item`属于后续PLN-01，本Feature不提前建表。
- `project_id`是PROJ稳定引用，不建跨Context外键。SOL表之间可使用同租户应用守卫与物理外键。
- plan按`tenant_id+project_id`唯一；revision按`tenant_id+plan_id+revision_no`唯一；当前与pending由plan行指针、行锁和CAS共同保证，不复制第二个“当前”标记。为避免MySQL中plan与首个revision形成不可插入的循环外键，两个revision指针物理可空，但首次创建事务提交前必须全部指向同租户同plan的revision；任何已提交的非删除plan均不得为空。
- 已提交的候选revision的工期字段不可更新；审批只更新change生命周期和plan指针/影响状态。
- 新DDL使用NPDMS当时下一个未占用Flyway版本，不修改已执行V1～V89。旧表保留历史读取直到后续独立退役/迁移单元；不双写。

## 6. UI

- 在项目工作区/详情中增加“项目工期”真实入口，展示当前基线、计算口径、重算影响、待审摘要和历史。
- 项目经理使用表单/抽屉完成首次录入、草稿和提交；服务经理在同一详情中查看冻结差异后通过或驳回。
- 优先复用Yudao现有Descriptions、Form、DatePicker、InputNumber、Upload、Table、Timeline、Dialog/Drawer和权限组件；无可复用时遵循Element Plus结构与主题变量，不堆叠内联样式。
- 320/768/1024/1440无页面级横向溢出；窄屏差异和审批区使用抽屉/分段布局，文件权限失效时只显示稳定摘要和可恢复错误。
- 旧“工期倒排”和“计划变更审批”页面只作差距证据，不作当前入口；本Feature实施后冻结其PRE-01写路由，但不提前退役PLN后续所需查询证据。

## 7. 验收标准

- `AC-FSOL001-001`：两种计算口径均生成一致起止日期和正整数时长；倒置、零/负时长、派生值冲突被拒绝且无版本副作用。
- `AC-FSOL001-002`：首次保存原子创建plan/revision 1/current pointer/待重算影响/幂等/审计；同项目重复创建只能重放或冲突。
- `AC-FSOL001-003`：草稿可用CAS修订；提交冻结base/candidate/原因/规则结果/FileArtifact版本，同一项目最多一个待审变更。
- `AC-FSOL001-004`：客户延期等配置要求材料的原因缺少或无权/失效文件时拒绝；不要求材料的启用原因不伪造文件引用。
- `AC-FSOL001-005`：通过后候选revision成为唯一当前版本，变更为APPROVED、pending清空、影响为PENDING_RECALCULATION；PLN-01不可用不使业务回滚，当前施工计划无变化。
- `AC-FSOL001-006`：驳回/撤回保留原当前revision和重算影响状态，清空pending；非空意见、申请人及合法状态守卫生效。
- `AC-FSOL001-007`：项目经理不能审批，非当前服务经理、平级/越权/跨租户主体拒绝；项目成员只读且无文件权限时不泄露引用。
- `AC-FSOL001-008`：并发提交/审批只有一个成功；同键重放不重复版本、指针、审计或文件引用，同键异载荷拒绝。
- `AC-FSOL001-009`：项目当前工期、revision历史和change历史稳定分页且刷新后持久；创建、提交、审批和拒绝审计可按projectId/planId/changeId/operationId追溯。
- `AC-FSOL001-010`：全新MySQL从V1迁移至当前版本，验证三表、外键/唯一/CAS/回滚/字典与配置种子；旧V22/V23不修改且无双写。
- `AC-FSOL001-011`：真实浏览器完成首次录入→变更草稿→提交→服务经理通过，以及驳回路径、权限负向、刷新持久和四档响应式；无当前功能控制台/失败HTTP异常。
- `AC-FSOL001-012`：不宣称PRE-02、PLN-01/02/03/04、INT-05、历史数据切换、Deployment、SIT、UAT或Release完成。

## 8. 测试与证据

本Feature按已确认的非TDD方式实施，先完成当前Task最小实现，再按风险补齐验证。完成证据至少包含服务/API自动化、状态与权限负向、幂等/CAS并发、文件引用、平台审计、真实MySQL迁移与事务、真实浏览器闭环和独立代码评审。

## 9. Definition of Ready

| 项目 | 当前状态 |
|---|---|
| PRE-01与PRE-02/PLN-01/PLN-04拆分 | PASS（独立边界裁决GO） |
| 工期口径、版本和生效指针 | PASS |
| 变更审批与计划影响分轴 | PASS |
| API、权限、文件、幂等、并发和UI验收 | PASS |
| 物理契约与老表边界 | PASS |
| 独立Feature Ready裁决 | PENDING |

结论：`IN_REVIEW`。Feature Ready独立GO前不同步NPDMS、不生成Technical Plan、不开始实现；不重开已通过的PRD/SDS门禁。
